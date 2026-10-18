# Jenkins pipelines

Three Jenkinsfiles. Jenkins finds the first two through the `scriptPath(...)` entries in [`../ansible/roles/jenkins/templates/jcasc.yaml`](../ansible/roles/jenkins/templates/jcasc.yaml), which Ansible installs, so a moved or renamed file must be changed there too.

| File | Job | Trigger | Purpose |
|---|---|---|---|
| `Jenkinsfile.app-cicd` | `app-cicd` | `githubPush()` on `main`, or by hand | Test, scan, build and deploy the application |
| `Jenkinsfile.platform-infra` | `platform-infra` | By hand | Create or destroy the EKS platform and the monitoring stack |
| `Jenkinsfile.lint` | none | none | Static checks. **Not created by JCasC**: add a `pipelineJob` with `scriptPath('infra/jenkins/Jenkinsfile.lint')` or create the job by hand to use it |

Both jobs check out `main` of the repository named by `GIT_REPO_URL` with the `github-token` credential. The Jenkins security group allows port 8080 only from the operator IPs, so GitHub cannot reach `githubPush()` unless you choose one of the options in [../../docs/github-webhook.md](../../docs/github-webhook.md).

Shared steps live in [`../../vars/`](../../vars) (`buildAndScanImage`, `ensureKubernetesAccess`, `getChangedServices`, `pushImageWithRetry`) and are loaded as a global library.

## `Jenkinsfile.app-cicd`

Parameters:

| Parameter | Default | Meaning |
|---|---|---|
| `ENV` | `dev` | `dev` or `staging`. Chooses the cluster (`catalogix-cluster-<env>`) and values file |
| `FORCE_ALL_TESTS` | false | Test every backend service, ignoring change detection |
| `FORCE_ALL_BUILDS` | false | Build, scan and push every image and retag every service to this run's tag |
| `CLEAR_MAVEN_CACHE` | false | Delete the Maven cache before building |
| `RUN_DAST` | false | Run an OWASP ZAP baseline scan after the deploy |
| `INGRESS_HOST` | empty | Public DNS name. Drives the ALB host rule, the URL in emails and the cookie `Secure` flag |
| `TLS_CERT_ARN` | empty | ACM certificate. Enables HTTPS and the HTTP-to-HTTPS redirect |

`INGRESS_HOST` and `TLS_CERT_ARN` are validated against a pattern before they reach any shell command.

Image tags are `<MAJOR_VERSION>-<BUILD_NUMBER>`. Only one run at a time (`disableConcurrentBuilds`, queued, never aborted), a 90 minute limit, and the last 30 builds are kept.

| Stage | What it does |
|---|---|
| Clean Workspace, Checkout | Fresh checkout of `main` |
| Secrets Scanning - Gitleaks | Scans the full history. Any finding fails the build |
| AWS Authentication, Login to Amazon ECR | Resolves the account, registry and image tag |
| Ensure Kubernetes Access | Writes a kubeconfig and verifies it points at the selected environment's cluster |
| Detect Changed Services | Diffs against the previous commit. A change to `security-common` selects every backend service. A first deploy (no `catalogix` Helm release) builds everything |
| Determine Per-Service Image Tags | Rebuilt services get the new tag. Unchanged services keep the tag read back from the live release (`helm get values`), so their pods are not restarted |
| Hadolint | One parallel branch per Dockerfile |
| Verify Operator Credentials | Dev only: fails early if the operator credentials secret is missing or incomplete |
| Clear Maven Cache | Only with `CLEAR_MAVEN_CACHE` |
| Install Shared Modules | Installs this run's `security-common` into the Maven cache so the service builds never use a stale jar |
| Unit & Integration Tests | Parallel `mvn clean verify` per changed service (Testcontainers provides PostgreSQL) and, when the frontend is rebuilt, `npm ci` and `npm run test:coverage` |
| Compile Untested Modules | `test-compile` for the services not tested this run, so SonarQube always sees bytecode for every module |
| SonarQube Analysis, Quality Gate | Scanner over the repository, then waits up to 5 minutes for the webhook and aborts if the gate fails |
| Build Docker Images and Trivy Image Scan | Parallel per image through `buildAndScanImage`. Fixable HIGH and CRITICAL findings fail the build (`--ignore-unfixed`, exit code 1) |
| Push Docker Images to Amazon ECR | Parallel, with retry |
| Fetch RDS Endpoint | Read from SSM Parameter Store, never hard-coded |
| Prepare Helm Arguments | Builds every `--set` argument in one place, including per-image repository and tag |
| Trivy Scan Helm Charts | Kubernetes misconfiguration scan of `infra/helm/catalogix-hc` |
| Deploy to EKS using Helm | `helm upgrade --install catalogix ... --wait --timeout 300s`, up to three attempts 30 seconds apart |
| Post-Deployment Verification | Lists the workload, then requests the ALB address every 10 seconds for up to 5 minutes and accepts 200, 301 or 302. Skipped with a warning if the ALB has no hostname yet |
| DAST Scan - OWASP ZAP Baseline | Only with `RUN_DAST`. Runs `zap-baseline.py` in informational mode and archives the HTML report |

On failure the pipeline prints pod status, events and ExternalSecret status, then rolls back to the highest revision whose status is `superseded` (the last known-good release). It resolves the revision number explicitly because a plain `helm rollback` would target the previous revision, which can be one of this run's failed retries. If there is no earlier revision the rollback is skipped. After every run it prunes containers and dangling images but keeps the BuildKit cache.

Accepted CVEs are in the root [`.trivyignore`](../../.trivyignore). Each entry carries the package, CVE id, severity, reason, a revisit condition and an expiry date, after which Trivy reports the finding again.

## `Jenkinsfile.platform-infra`

Parameters:

| Parameter | Default | Meaning |
|---|---|---|
| `ACTION` | `Apply` | `Apply` or `Destroy` |
| `ENV` | `dev` | `dev` or `staging`. Terraform runs in `infra/terraform/platform-infra/env/<ENV>` |
| `CONFIRM_DESTROY` | false | Must be true for `Destroy` |
| `ALERT_EMAIL_TO`, `SMTP_HOST`, `SMTP_USERNAME` | empty | Alert email settings. Leave empty to keep alerting off. The SMTP password is a Terraform variable stored in Secrets Manager |
| `DEPLOY_APP_AFTER_INFRA` | false | After the monitoring stack is verified, check that External Secrets Operator is available (it only warns if the two ExternalSecrets are not Ready yet), then start `app-cicd` for the same `ENV` without waiting for it |

One run at a time (queued, never aborted, because killing `terraform apply` leaves a stale lock), with a 90 minute limit.

**Apply**

| Stage | What it does |
|---|---|
| Precheck Bootstrap State | Checks that the `bootstrap-infra` state object exists in S3 |
| Verify Operator Credentials | Dev only: `credentials.py --check` |
| Trivy Terraform IaC Scan | `trivy config` over `infra/terraform/platform-infra/`, fails on HIGH or CRITICAL, honouring `.trivyignore` |
| Terraform Init, Validate | In `TF_DIR` |
| Terraform Plan and Apply | `terraform plan -detailed-exitcode`. Exit 0 skips the apply and the approval; exit 2 waits for a human to approve the saved plan, then applies exactly that plan |
| Ensure Kubernetes Access, Wait for EKS Ready | Waits for the node group to be `ACTIVE`, all nodes `Ready` and CoreDNS rolled out |
| Install Metrics Server | Needed by the autoscalers and the dashboards |
| Deploy Monitoring Stack | `helm upgrade --install monitoring infra/helm/monitoring` in the `monitoring` namespace, with the alert email settings and secret names as `--set` values |
| Verify Monitoring Stack | Lists pods and services and checks the stack is up |

**Destroy** (`ACTION=Destroy` and `CONFIRM_DESTROY=true`)

| Stage | What it does |
|---|---|
| Destroy Safety Gate | Fails immediately if the box is not ticked |
| Terraform Init, Plan - Destroy Preview, Destroy Approval | Shows the destroy plan and asks for approval |
| Cleanup Kubernetes Resources | Uninstalls the `catalogix` and `monitoring` releases and waits for the load balancers to disappear |
| Terraform Destroy | Destroys the platform |
| Delete Operator Credentials | Deletes `<cluster>/operator-credentials` |

Staging is not kept in step with dev. For `staging`, the operator-credentials check is skipped and Terraform still generates its own passwords.

## `Jenkinsfile.lint`

Not wired to a job. Stages: Hadolint on every Dockerfile under `app/`, `terraform fmt -check` on `infra/terraform`, `ansible-lint` on the playbook, `ruff` on `scripts/python`, the Python unit tests, `helm dependency build` for the monitoring chart, `helm lint` for both charts, kubeconform on the rendered manifests, the Helm image-tag contract test (`scripts/ci/check-helm-image-tags.sh`), a Trivy scan of the chart, `shellcheck` on the shell scripts, and Gitleaks. It exists so these checks can run without a cluster or built images. `app-cicd` repeats only Hadolint, Gitleaks and the Trivy scan of the chart, because those gate a deploy.
