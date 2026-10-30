# app/: the workload the platform delivers

Catalogix is a small e-commerce system. In this project it is the **payload**: it is deliberately modest in business logic, but it has the properties that make delivery engineering worthwhile: seven independently deployable services, a database per service, a message broker, and service-to-service authentication. Everything that builds, scans, ships and runs it is in [`../infra`](../infra/README.md); how to run the whole project is in [`../docs/RUNBOOK.md`](../docs/RUNBOOK.md).

```text
app/
  backend/         security-common and seven Spring Boot services (Maven modules, parent pom.xml at the repository root)
  frontend/        React 19 + Vite 8 + Tailwind CSS 4 single-page app, built to static files and served by Nginx
  gateway/         Nginx reverse proxy: routing, rate limiting, security headers
  postgres-init/   01-create-databases.sql: one database and one login role per service (Docker Compose only)
```

How a request and an order travel through the services is in [`../docs/app-guide.md`](../docs/app-guide.md); the token design is in [`../docs/auth-explained.md`](../docs/auth-explained.md). This page is the operator's reference.

## Services, ports and dependencies

| Service | Port | Database (role) | RabbitMQ | Calls | Exposed by the gateway |
|---|---|---|---|---|---|
| `gateway` | 11000 | none | no | all routed services | The only published port; `/gateway/health` is its health check |
| `frontend` | 11001 | none | no | none | Yes (`/`) |
| `catalog-svc` | 11002 | `catalogix-catalog` (`catalog_svc`) | no | inventory | Yes |
| `inventory-svc` | 11003 | `catalogix-inventory` (`inventory_svc`) | no | none | **No, internal only** |
| `cart-svc` | 11004 | `catalogix-cart` (`cart_svc`) | no | catalog, inventory | Yes |
| `user-svc` | 11005 | `catalogix-users` (`user_svc`) | publishes | none | Yes |
| `payment-svc` | 11006 | `catalogix-payment` (`payment_svc`) | no | none | **No, internal only** |
| `checkout-svc` | 11007 | `catalogix-checkout` (`checkout_svc`) | publishes | catalog, cart, user, inventory, payment | Yes |
| `notification-svc` | 11008 | `catalogix-notification` (`notification_svc`) | consumes | user | Yes |
| `security-common` | none | none | no | none | A shared library (JWT validation, rate limiting, CORS), not a service |

These "Calls" columns are the same graph as the Helm `NetworkPolicy` allow rules and the Docker Compose `depends_on` order. If you add a call, update the chart's `networkpolicy.yaml` too.

Database names contain hyphens, so they must be quoted in SQL. The same names are created in three places that must stay in step: `postgres-init/` (Docker Compose), `infra/helm/catalogix-hc/templates/postgres-local.yaml` (local Kubernetes) and `infra/terraform/platform-infra/modules/db-roles` (RDS). Every service validates its schema against its Flyway migrations (`src/main/resources/db/migration/*.sql`) with `spring.jpa.hibernate.ddl-auto=validate`; Hibernate never changes the schema.

### Gateway routes

| Path | Goes to |
|---|---|
| `/api/users/login`, `/api/users/register`, `/api/users/refresh` | user-svc, stricter rate limit |
| `/api/users/...` | user-svc |
| `/api/products/...` | catalog-svc |
| `/api/orders/...`, `/api/admin/...` | checkout-svc |
| `/api/cart/...` | cart-svc |
| `/api/notifications/...` | notification-svc |
| everything else | frontend |

`/api` is stripped before the request reaches the service. The gateway limits clients to 20 requests per second (burst 40) in general and 5 per second (burst 10) on login, register and refresh, and overwrites `X-Real-IP`, which the services trust only because of that.

## What the platform relies on

| Contract | Detail |
|---|---|
| Health and metrics | Every backend serves `/health`, `/actuator/health/**` (liveness and readiness probes) and `/actuator/prometheus` without a token. Every other path needs one. The ports are reachable only inside the cluster |
| Custom metrics | `checkout_outbox_pending_count`, `checkout_outbox_dead_letter_count`, `checkout_order_event_outbox_pending_count`, `checkout_order_event_outbox_dead_letter_count` feed the alert rules |
| Graceful shutdown | The chart adds a `preStop` sleep and a 45 s grace period; services finish in-flight requests |
| Stateless pods | State lives in PostgreSQL and RabbitMQ, so every service can run several replicas behind an autoscaler. Background jobs use `FOR UPDATE SKIP LOCKED` or an advisory lock so replicas do not collide |
| Configuration | Environment variables only (below). No config server |
| Secrets | Never in an image or a values file. Kubernetes Secrets `catalogix-secrets` and `catalogix-jwt-keys` are populated by External Secrets on AWS |
| Idempotency | Stock changes and payments take an idempotency key, so a retried pipeline step or HTTP call does not double-charge or double-reserve |
| Failure behaviour | Calls on the order path go through circuit breakers, and failed stock releases are queued in an outbox and retried, so a restarting dependency degrades checkout instead of corrupting it |

## Configuration

The full local list is in [`../.env.example`](../.env.example) and each service's block in [`../docker-compose.yaml`](../docker-compose.yaml). On Kubernetes the Helm chart sets the same names.

| Variable | Used by | Meaning |
|---|---|---|
| `SPRING_DATASOURCE_URL`, `_USERNAME`, `_PASSWORD` | all backends | Per-service database and role |
| `JWT_PRIVATE_KEY_B64` | user-svc only | Private key that signs user access tokens |
| `JWT_PUBLIC_KEY_B64` | all backends | Public key that verifies them |
| `INTERNAL_JWT_PRIVATE_KEY_B64`, `INTERNAL_JWT_PUBLIC_KEY_B64` | all backends | The shared service-to-service pair |
| `JWT_EXPIRATION_MS`, `REFRESH_TOKEN_EXPIRATION_MS` | user-svc | Token lifetimes (15 minutes and 7 days by default) |
| `SPRING_RABBITMQ_*` | user-svc, checkout-svc, notification-svc | Broker connection |
| `*_SVC_URL` | callers | Where to reach another service |
| `ALLOWED_ORIGINS` | all backends | CORS origins. Empty disables CORS; only needed when bypassing the gateway |
| `MAIL_*`, `MAIL_FROM`, `FRONTEND_BASE_URL` | notification-svc, user-svc | SMTP settings, sender and the base URL used in email links |
| `SEED_ADMIN_EMAIL`, `SEED_ADMIN_PASSWORD` | user-svc | First admin, created on first start if none exists |
| `RATE_LIMIT_ENABLED`, `RATE_LIMIT_GENERAL_RPM`, `RATE_LIMIT_AUTH_RPM` | all backends | Per-IP limits (300 and 10 requests per minute by default) |

Keys are Base64 of DER: PKCS#8 for private keys, X.509 for public keys. Locally `python3 scripts/python/local/generate_jwt_keys.py` writes them into `.env`; on AWS Terraform generates them and stores them in Secrets Manager.

## Build and test

All commands run from the repository root. Java 21 and Maven are required for the backend, Node 24 or newer for the frontend, and Docker for integration tests (Testcontainers) and images.

```bash
mvn verify                                    # every backend module
mvn -pl app/backend/checkout-svc -am verify   # one service and what it depends on
npm ci                                        # frontend dependencies
npm test                                      # frontend tests (Vitest)
npm run build                                 # production bundle in app/frontend/build
npm start                                     # Vite dev server on :5173, proxying /api to the gateway on :11000
docker compose build                          # all images
```

### Images

Backend Dockerfiles build from the repository root, because the build context must include the root `pom.xml` and `security-common`:

```bash
docker build -f app/backend/user-svc/Dockerfile .
```

| Image | Build | Runtime |
|---|---|---|
| Backends | `maven:3.9.16-eclipse-temurin-21-alpine`, BuildKit cache mount for the Maven repository (one cache id per service) | `gcr.io/distroless/java21-debian13:nonroot`, no shell, JVM sized from the container memory limit |
| Frontend | `node:24-alpine` | `nginx:stable-alpine3.24`, UID 1000, port 11001, `HEALTHCHECK` |
| Gateway | none | `nginx:stable-alpine3.24`, UID 1000, ports 11000 and 8081 (`stub_status` for the Prometheus exporter), `HEALTHCHECK` |

Distroless images have no shell, so the Java images have no container health check; Kubernetes uses HTTP probes and Docker Compose only orders start-up. The root `.dockerignore` is an allow-list for the Java builds (only `pom.xml`, each module's `pom.xml` and `src` reach the daemon); `app/frontend/.dockerignore` covers the frontend context. There are no per-service `.dockerignore` files because Docker only reads the one at the root of the build context.

## Application behaviour in brief

- **Orders.** `checkout-svc` runs a saga without a distributed transaction: price from catalog, stock reservation in inventory, shipping address from user, then payment outside any database transaction. Failures are compensated, and compensation and notification events go through outbox tables that give up after 5 and 8 attempts into a `DEAD_LETTER` state an admin can retry. Delivery to RabbitMQ is at least once.
- **Authentication.** Short-lived RS256 access tokens signed only by `user-svc`; opaque refresh tokens stored as hashes in an `httpOnly` cookie and rotated on every use; five failed logins lock an account for 15 minutes; roles are `USER`, `SELLER`, `ADMIN`. Service-to-service calls use two-minute tokens signed with the shared internal key, with `iss`, `aud` and caller allowlists on internal endpoints.
- **Frontend.** One component per page in `frontend/src/components/`, routes in `App.jsx`, an API layer in `frontend/src/api/` that attaches the token and refreshes it through the cookie. The build is plain static files, so the image, chart and gateway do not depend on any framework server.

The details, including the order state machine and what breaks when a component is down, are in [`../docs/app-guide.md`](../docs/app-guide.md).
