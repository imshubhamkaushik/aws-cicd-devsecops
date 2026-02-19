# Monitoring Stack (Prometheus, Grafana & Alertmanager)

This directory contains the Helm-based configuration for deploying
monitoring and alerting components into the Kubernetes cluster.

## Tools Used
- Prometheus (metrics collection)
- Grafana (metrics visualization)
- Alertmanager (alert handling)
- Helm (deployment and lifecycle management)

## Helm Chart
We use the **kube-prometheus-stack** chart from the
`prometheus-community` Helm repository, which is an industry-standard
bundle for Kubernetes monitoring.

## Why Helm?
- Simplifies installation and upgrades
- Manages CRDs automatically
- Provides versioned, repeatable deployments
- Reduces manual YAML management

## Deployment
Monitoring is deployed using a Bash script that:
- Creates the monitoring namespace if required
- Adds and updates the Helm repository
- Installs or upgrades the monitoring stack using Helm values

## Accessing Grafana
Grafana is exposed via NodePort for local learning purposes.
Alternatively, port-forwarding can be used.

## Dashboards
Dashboards are currently created manually using the Grafana UI.
In future iterations, dashboards can be exported as JSON and stored
under the `dashboards/` directory for version control.
