# Monitoring chart: `catalogix-monitoring`

Installs Prometheus, Grafana and Alertmanager (the `kube-prometheus-stack` chart, version 82.6.0, as a dependency) and adds the Catalogix-specific parts: what to scrape, the alert rules, the dashboard, the data source and the secrets. It runs in the `monitoring` namespace. `Jenkinsfile.platform-infra` installs it as the release `monitoring` after the cluster is ready, and removes it on destroy.

```text
infra/helm/monitoring/
  Chart.yaml, Chart.lock     dependency on kube-prometheus-stack 82.6.0
  values.yaml                Prometheus, Grafana, Alertmanager settings and the alert rules
  dashboards/catalogix-service-health.json
  templates/
    servicemonitors.yaml            what Prometheus scrapes
    catalogix-dashboard-cm.yaml     the dashboard, labelled for the Grafana sidecar
    observability-datasources.yaml  the Prometheus data source
    alertmanager-secrets.yaml       ExternalSecret: SMTP password
    grafana-admin-secret.yaml       ExternalSecret: Grafana admin login (only if operatorSecretName is set)
    ingress.yaml                    internal ALB with three host rules
```

`charts/*.tgz` (the downloaded dependency) is git-ignored; `helm dependency update infra/helm/monitoring` fetches it, which the pipeline does on every run.

## What is scraped

Three `ServiceMonitor`s, all looking in the `catalogix` namespace (`global.targetNamespace`):

| Target | Port and path | Notes |
|---|---|---|
| The seven backend services (selected by their `app` label) | `http`, `/actuator/prometheus`, every 15 s | JVM, HTTP, Hikari and the custom checkout outbox metrics. The frontend is excluded because nginx has no metrics endpoint |
| RabbitMQ | `metrics`, `/metrics`, every 15 s | Queue depth and consumers from the `rabbitmq_prometheus` plugin (port 15692) |
| Gateway | `metrics`, `/metrics`, every 15 s | nginx-prometheus-exporter sidecar on 9113 |

The application chart's NetworkPolicy lets the `monitoring` namespace reach each of these ports. If you change the monitoring namespace, change `networkPolicy.monitoringNamespace` in the application chart too, or Prometheus is blocked.

## Alert rules

Eight rules in `values.yaml` (`additionalPrometheusRulesMap.catalogix-alerts`):

| Alert | Fires when | Severity |
|---|---|---|
| `ServiceUnavailable` | A Deployment has 0 available replicas for 3 minutes | critical |
| `HighErrorRate` | 5xx responses are more than 5 % of all requests for 5 minutes | critical |
| `HighLatencyP95` | p95 latency above 1.5 s for 5 minutes | warning |
| `PodCrashLooping` | A container restarted 3 or more times in 15 minutes (for 5 minutes) | critical |
| `OutboxDeadLetterBacklog` | `checkout_outbox_dead_letter_count` or `checkout_order_event_outbox_dead_letter_count` above 0 for 10 minutes | critical |
| `RabbitMQQueueBacklogGrowing` | More than 500 ready messages in a queue for 10 minutes | warning |
| `DeadLetterQueueNotEmpty` | Any queue matching `.*dlq.*` has messages for 10 minutes | warning |
| `RabbitMQQueueNoConsumers` | A queue has ready messages and 0 consumers for 5 minutes | critical |

Inhibition: while `ServiceUnavailable` is firing, `HighErrorRate` and `HighLatencyP95` are suppressed for the same namespace, so one outage is one email.

## Alert routing

Only `severity: critical` goes anywhere: it is emailed through the `critical-receiver` and repeated every hour. `warning` alerts route to the `null` receiver, so they appear in the Alertmanager and Prometheus UIs but send nothing. If you want warnings by email, add a route in `values.yaml`.

The email settings are empty in the file. The pipeline fills in the recipient, SMTP host and user name from its parameters (`ALERT_EMAIL_TO`, `SMTP_HOST`, `SMTP_USERNAME`). The SMTP password never appears in values: Alertmanager reads it from the file `/etc/alertmanager/secrets/alertmanager-secrets/smtp_password`, which the `alertmanager-secrets` Secret provides (an ExternalSecret reading `<cluster>/alerting-secrets`, created by the Terraform `alerting_secrets` module). With the parameters left empty, alerts are evaluated but no email is sent.

## Dashboard and Grafana

The dashboard JSON lives in `dashboards/catalogix-service-health.json` and is loaded into a ConfigMap with the label `grafana_dashboard: "1"`; the Grafana sidecar picks it up, so a changed JSON file plus `helm upgrade` is how you update it. Panels: pod restarts, p95 latency, service availability, total traffic, 5xx error rate, request rate, JVM heap, GC pause p95, database connection pool usage, thread pool, RabbitMQ queue depth and consumers per queue.

The Prometheus data source is provisioned from a ConfigMap labelled `grafana_datasource`. The chart does not deploy Loki or Tempo, so there is no log or trace data source.

Grafana's login comes from the operator-chosen password when `global.operatorSecretName` is set; only the two Grafana keys are read from that secret. `Jenkinsfile.platform-infra` passes it (and `--set kube-prometheus-stack.grafana.admin.existingSecret=grafana-admin-secret`) **for dev only**. Staging keeps the chart's default login, so change it before using Grafana there.

The Grafana Service listens on port 80 (forwarding to the container's 3000), which is why the ingress and the port-forward below use 80.

## Reaching the UIs

Services are `ClusterIP`; nothing is public. Use port-forwarding:

```bash
kubectl port-forward svc/monitoring-grafana 3000:80 -n monitoring
kubectl port-forward svc/monitoring-kube-prometheus-stack-prometheus 9090:9090 -n monitoring
kubectl port-forward svc/monitoring-kube-prometheus-stack-alertmanager 9093:9093 -n monitoring
```

`templates/ingress.yaml` also creates an **internal** ALB with the host names `grafana.internal.catalogix.com`, `prometheus.internal.catalogix.com` and `alertmanager.internal.catalogix.com`. Those names do not resolve until you add a private Route 53 zone with aliases to that ALB, and the ALB is reachable only from inside the VPC. Change the host names to your own domain if you use it.

## Storage and sizing

Prometheus keeps 10 days of data on a 5 Gi `gp3-sc` volume (limit 1 Gi memory); Grafana uses 2 Gi. Both volumes are deleted when the pipeline destroys the platform (`kubectl delete pvc --all -n monitoring`), so metrics history does not survive a destroy.

## Install by hand

```bash
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm dependency update infra/helm/monitoring
helm upgrade --install monitoring infra/helm/monitoring -n monitoring --create-namespace \
  --set global.alertingSecretName=<cluster>/alerting-secrets
```

The release must be named `monitoring`: the ServiceMonitors carry the label `release: monitoring` and the data source URL is built from the release name.
