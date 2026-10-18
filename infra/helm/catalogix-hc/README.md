# Helm chart: `catalogix-hc`

Deploys the Catalogix application: seven Spring Boot services, the nginx gateway, the frontend and RabbitMQ. The database is **not** part of the AWS deployment (it is RDS, created by Terraform). For a laptop cluster the chart can run a Postgres StatefulSet instead.

Chart version `0.2.0`, app version `2.0`. There is no `values.yaml`: every environment has its own values file and they are used on their own (`values-local.yaml` is an overlay on top of `values-dev.yaml`).

## What it creates

| Template | Resources |
|---|---|
| `backend-deployments.yaml` | One Deployment and Service per entry in `backendServices`, generated from the same template |
| `backend-services.yaml` | ClusterIP Services for the backends |
| `gateway.yaml` | nginx gateway Deployment (with an nginx-prometheus-exporter sidecar on 9113) and Service. The gateway is the only workload the load balancer reaches |
| `frontend.yaml` | Frontend Deployment and Service |
| `ingress-alb.yaml` | One `Ingress` (class `alb`) that sends `/` to the gateway. Internet-facing, target type `ip`, health check `/gateway/health`. Adds HTTPS and an HTTP-to-HTTPS redirect when `ingress.tlsCertArn` is set |
| `rabbitmq.yaml` | StatefulSet, headless Service, ConfigMap, Erlang cookie Secret and the RBAC used for peer discovery. Each replica gets its own PVC |
| `hpa.yaml` | HorizontalPodAutoscaler per backend, plus frontend and gateway. CPU target, memory only if `hpa.memoryUtilization` is set |
| `pdb.yaml` | PodDisruptionBudget (`maxUnavailable: 1`) for every workload that can run more than one pod |
| `networkpolicy.yaml` | Default-deny ingress and per-app allow rules (below) |
| `external-secrets.yaml` | Two `ExternalSecret`s: `catalogix-secrets` and `catalogix-jwt-keys` (AWS only) |
| `postgres-local.yaml`, `secrets-local.yaml` | Local Kubernetes only: an in-cluster Postgres and a generated `catalogix-secrets` |
| `_helpers.tpl` | Shared security context, probes, init containers, environment variables, graceful-shutdown hook |

The backends are described once in `backendServices` (port, database name, replicas, resources, HPA, extra environment). Adding a service means adding an entry there and a caller list in `networkpolicy.yaml`; the template fails with `networkpolicy.yaml has no caller list for <name>` if you forget the second part.

## Values files

| File | Used for |
|---|---|
| `values-dev.yaml` | The dev cluster. This is the reference file: the image-tag contract test and local overlay build on it |
| `values-staging.yaml` | The staging cluster. Smaller requests, 3 to 4 replicas per service |
| `values-local.yaml` | Overlay for kind, minikube or Docker Desktop. Use it **together with** `values-dev.yaml` |

Values the pipeline sets at deploy time (you do not edit them): `database.host` (the RDS endpoint from SSM), `global.imageRegistry`, `global.imageTag`, per-service `image.repository` and `image.tag`, `ingress.host`, `ingress.tlsCertArn`, `ingress.loadBalancerName`, `secretsManager.appSecretName` and the alert settings. Because the pipeline sets each repository, the `image.repository` default in a values file does not matter on AWS.

Staging notes:

- `values-staging.yaml` is **not** tested by CI beyond `helm lint` and rendering in `Jenkinsfile.lint`; the image-tag contract test checks dev only. Treat it as unproven until you deploy it.
- No `operatorSecretName` is set and nothing creates a staging operator-credentials secret, so `SEED_ADMIN_PASSWORD` is absent and user-svc logs an error and creates **no admin account**. Create `catalogix-cluster-staging/operator-credentials` (keys `rabbitmq_user`, `rabbitmq_password`, `seed_admin_password`) and set `secretsManager.operatorSecretName` to use an admin in staging.

Email: the chart does not deploy a mail server. Dev points `MAIL_HOST` at `mailpit`, which exists only in Docker Compose, so verification and password-reset emails are not delivered in a dev cluster unless you set `MAIL_HOST` and `MAIL_PORT` (and credentials) on `notification-svc` to a real SMTP server.

## Hardening built into the templates

| Control | Where |
|---|---|
| Non-root user 1000, read-only root filesystem, no privilege escalation, all capabilities dropped | Every backend (`catalogix.securityContext`); the frontend and gateway set their own non-root contexts |
| Writable `/tmp` through an `emptyDir` | Backends |
| Startup probe (up to 300 s: 60 failures x 5 s), readiness and liveness probes against Actuator | `catalogix.probes` |
| `wait-for-postgres` and, for services that use it, `wait-for-rabbitmq` init containers (TCP check only) | `catalogix.waitForDeps` |
| Rolling update with `maxUnavailable: 0`, `preStop` sleep (10 s) and a 45 s grace period | Deployments, `catalogix.lifecycle` (needs Kubernetes 1.30 or later) |
| Per-service database role, so one service cannot read another's database | `catalogix.commonEnv` (`db_user_<svc>`, `db_password_<svc>`) |
| Only user-svc receives the user-token private key | `catalogix.commonEnv` |
| Resource requests and limits on every container | values files |

## Secrets

Nothing sensitive is in the values files. On AWS, External Secrets Operator copies from AWS Secrets Manager into two Kubernetes Secrets:

| Kubernetes Secret | Source | Holds |
|---|---|---|
| `catalogix-secrets` | `secretsManager.appSecretName` (everything in it), plus `secretsManager.operatorSecretName` for exactly `rabbitmq_user`, `rabbitmq_password`, `seed_admin_password` | Per-service DB credentials, RabbitMQ credentials, the seed admin password |
| `catalogix-jwt-keys` | `secretsManager.jwtKeysSecretName` | `jwt_private_key_b64`, `jwt_public_key_b64`, `internal_jwt_private_key_b64`, `internal_jwt_public_key_b64` |

The operator secret is listed key by key because it also holds the RDS master password and the Grafana login, which must not reach this namespace. The Secret and Cluster Secret Store (`aws-secrets-manager`) are created outside this chart by Terraform. On local Kubernetes, `secrets-local.yaml` generates `catalogix-secrets` (RabbitMQ and database passwords) on first install and keeps it across `helm upgrade`. It does **not** create `catalogix-jwt-keys`: run `scripts/generate-local-jwt-keys.sh --kubernetes` first (see below).

## NetworkPolicy

Controlled by `networkPolicy.enabled` (on in dev and staging, off in the local overlay). Only **ingress** is restricted; egress stays open so pods can reach RDS, DNS, SMTP and AWS APIs.

1. `default-deny-ingress` selects every pod in the namespace and allows nothing.
2. Each app then gets an allow policy. The call graph is the table in `networkpolicy.yaml`:

| Service | Accepts connections from |
|---|---|
| user-svc | gateway, checkout-svc, notification-svc |
| catalog-svc | gateway, cart-svc, checkout-svc |
| inventory-svc | catalog-svc, cart-svc, checkout-svc (not the gateway) |
| cart-svc | gateway, checkout-svc |
| payment-svc | checkout-svc only |
| checkout-svc | gateway |
| notification-svc | gateway |
| gateway | anything on 11000 (the ALB is outside the cluster, so the rule has no source) |
| frontend | gateway |
| rabbitmq | user-svc, checkout-svc, notification-svc on 5672; other RabbitMQ pods; the `monitoring` namespace on 15692 |
| postgres-local (local only) | every backend |

Every backend and the gateway also accept the `monitoring` namespace on their port (and 9113 on the gateway) so Prometheus can scrape them.

**Enforcement depends on the network plugin.** The API server accepts these objects on any cluster, but they only take effect if the plugin enforces them. On EKS that is the VPC CNI add-on with `enableNetworkPolicy` set in the Terraform `eks` module. On Docker Desktop they are ignored, which is why the local overlay turns the policies off.

### Testing the NetworkPolicy

Run these on a cluster that enforces policies (the EKS dev cluster, or kind with Calico or Cilium and `networkPolicy.enabled=true`). They have not been run for this repository, so confirm the results yourself.

```bash
NS=catalogix

# 1. The policies exist
kubectl get networkpolicy -n $NS

# 2. Should be BLOCKED: a throwaway pod with no app label cannot reach payment-svc
kubectl run np-test --rm -it --restart=Never -n $NS --image=busybox:1.36.1 -- \
  wget -qO- -T 3 http://payment-svc:11006/actuator/health
# expected: timeout (wget exits non-zero)

# 3. Should be BLOCKED: the gateway cannot reach inventory-svc directly
GW=$(kubectl get pod -n $NS -l app=gateway -o name | head -1)
kubectl exec -n $NS $GW -c gateway -- wget -qO- -T 3 http://inventory-svc:11003/actuator/health
# expected: timeout

# 4. Should be ALLOWED: through the public path (replace the host)
curl -i http://<alb-hostname>/api/products
# expected: a normal HTTP response from catalog-svc via the gateway
```

If step 2 succeeds, either the plugin is not enforcing policies or `networkPolicy.enabled` is false. Check the add-on configuration before assuming the chart is wrong. If step 4 fails after enabling policies, look at `kubectl describe networkpolicy` for the app that was blocked.

## Rendering and linting locally

```bash
helm lint infra/helm/catalogix-hc --values infra/helm/catalogix-hc/values-dev.yaml
helm template catalogix infra/helm/catalogix-hc -f infra/helm/catalogix-hc/values-dev.yaml \
  --set database.host=db.example.internal --set global.cloudProvider=aws
scripts/ci/check-helm-image-tags.sh      # the pipeline's image keys must change the rendered image
```

`Jenkinsfile.lint` also validates the rendered manifests with kubeconform (`-ignore-missing-schemas`, because the External Secrets resources have no schema in its default catalogue).

## Running on local Kubernetes

Build the images, create the JWT key secret, then install with the dev file plus the local overlay. The overlay uses `latest` tags and an in-cluster Postgres. The key script also writes the same keys into the repository's `.env` (creating it from `.env.example` if missing) and replaces any earlier keys, so tokens already issued stop working.

```bash
# from the repository root, with images tagged catalogix-<service>:latest built locally
scripts/generate-local-jwt-keys.sh --kubernetes catalogix
helm upgrade --install catalogix ./infra/helm/catalogix-hc \
  -f infra/helm/catalogix-hc/values-dev.yaml \
  -f infra/helm/catalogix-hc/values-local.yaml \
  -n catalogix --create-namespace

kubectl get pods -n catalogix
# Docker Desktop: open http://localhost:11000
```

Things that differ from AWS: every service runs one replica, no HPA, no ingress (the gateway Service is a `LoadBalancer`), no TLS, NetworkPolicy off, and the seed admin password is a fixed local value (`values-local.yaml`; never use it anywhere reachable). If a PVC stays `Pending`, run `kubectl get storageclass` and set `database.local.storageClass` and `rabbitmq.storageClass` to match (`hostpath` on Docker Desktop, `standard` on kind). On kind the gateway needs `extraPortMappings`, on minikube `minikube tunnel`.

The local overlay re-declares user-svc `extraEnv` in full, because Helm replaces lists instead of merging them. Keep it in step with `values-dev.yaml`.
