# Jenkins pipelines

Jenkins runs one repository-wide **quality/security gate** before application delivery. GitHub push and pull-request webhooks trigger only the multibranch `quality-security-gate` job. Pull requests get a required check; after a successful push to `main`, the gate starts `app-cicd` at the exact commit that passed. `platform-infra` remains a manual operation because applying or destroying cloud infrastructure must not happen automatically for every push. Run it from protected `main` after the quality gate is green.

Job definitions live in [`../ansible/roles/jenkins/templates/jcasc.yaml`](../ansible/roles/jenkins/templates/jcasc.yaml), and Ansible installs them. Shared implementation lives in [`../../vars/`](../../vars).

| Jenkins job | Jenkinsfile | Trigger | Responsibility |
|---|---|---|---|
| `quality-security-gate` (multibranch) | `Jenkinsfile.lint` | GitHub push/PR webhook; daily scan is a fallback | Repository lint, configuration validation and source/IaC/chart security checks |
| `app-cicd` | `Jenkinsfile.app-cicd` | Successful `main` gate, or explicit manual override | Changed-service tests, Sonar quality gate, image build and Trivy scan, ECR push, Helm deploy, runtime verification |
| `platform-infra` | `Jenkinsfile.platform-infra` | Manual only | Terraform plan/approval/apply or explicitly approved destroy; deploy/verify monitoring |

## Quality/security gate

This job runs checks that do not need a live cluster or newly built application images: Gitleaks, Hadolint, Terraform formatting and Trivy IaC scan, Ansible lint, Ruff security/style checks, Python unit tests, Helm dependency build/lint/rendering, kubeconform, Helm image-tag contract validation, Trivy chart scan, and ShellCheck. Keep each check in one pipeline only.

The gate does **not** replace deployment-specific checks: `app-cicd` still runs backend Maven verification and frontend tests when relevant, SonarQube analysis/quality gate, and Trivy scans on new container images. `platform-infra` still runs Terraform init/validate/plan against the selected environment before requesting human approval. Staging values remain a configuration example; this audit does not claim the staging Terraform root is deployable.

### GitHub webhook and branch protection

Configure a repository webhook at `https://<reachable-jenkins-host>/github-webhook/` with **Pushes** and **Pull requests** events, content type `application/json`, and a strong webhook secret if the Jenkins GitHub integration is configured to validate it. Jenkins must be reachable by GitHub over HTTPS (do not expose an unauthenticated Jenkins UI to the internet just to receive a webhook; use a secured reachable endpoint or polling in restricted networks).

In GitHub repository settings, protect `main` and require the status check produced by the `quality-security-gate` pull-request job before merging. The exact status-context label is shown after the first PR run; select that observed check, not a guessed label.

## `Jenkinsfile.app-cicd`

Parameters:

| Parameter | Default | Meaning |
|---|---|---|
| `ENV` | `dev` | Environment/cluster to deploy. Staging is illustrative, not represented as fully supported. |
| `FORCE_ALL_TESTS` | false | Test all backend services; does not force image rebuilds. |
| `FORCE_ALL_BUILDS` | false | Build, scan, push and retag every image. |
| `SOURCE_REVISION` | empty | Exact commit passed from the quality gate. Normal runs require it. |
| `OVERRIDE_QUALITY_GATE` | false | Explicit manual/emergency override; avoid for routine deployment. |
| `CLEAR_MAVEN_CACHE` | false | Delete Maven cache before compiling. |
| `RUN_DAST` | false | Optional OWASP ZAP baseline scan after deployment. |
| `INGRESS_HOST`, `TLS_CERT_ARN` | empty | Optional public DNS name and ACM certificate; public/shared deployments should supply both. |

Important stages: checkout the exact gated commit; authenticate to AWS; detect changed test targets and image targets independently; verify dev credential categories; run relevant Maven/frontend tests; compile untouched modules for Sonar; enforce the SonarQube quality gate; build/scan/push only images that need rebuilding; resolve existing tags for unchanged images; deploy with Helm; verify rollout and application access; optionally run DAST. Source lint/secrets/config scans are deliberately not repeated here.

`FORCE_ALL_TESTS` broadens test selection only. A change to `security-common` still triggers all backend tests and image rebuilds because every backend depends on that module. A first deploy or `FORCE_ALL_BUILDS` also builds all images.

## `Jenkinsfile.platform-infra`

This is manually triggered. `ACTION=Apply` runs Terraform format/init/validate/plan, asks for approval when the plan has changes, then verifies EKS and deploys monitoring. `ACTION=Destroy` requires `CONFIRM_DESTROY=true`, displays and approves a destroy plan, removes Kubernetes releases, then destroys the Terraform-managed platform. ECR repositories are now part of this dev platform's destroy lifecycle and `force_delete=true` allows tagged images to be removed; rebuild images before redeploying. Keep the separate bootstrap/state infrastructure intact.

Dev operator credentials must exist under `<cluster>/credentials/{database,application,observability}`. The pipeline validates them before apply and deletes all three categories during the explicit platform destroy. The legacy `<cluster>/operator-credentials` secret is retained by migration until the operator verifies and manually removes it.
