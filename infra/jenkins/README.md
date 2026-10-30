# Jenkins pipelines

Jenkins is the **deployment** side of the project. The repository quality and security checks (tests, lint, IaC, chart and secret scans) run in GitHub Actions, in `.github/workflows/ci.yaml`, because they need nothing from AWS and GitHub cannot reach the IP-restricted Jenkins UI. Jenkins keeps everything that needs AWS or the private network: SonarQube, image build and scan, ECR, Helm, and `terraform plan` against live state. How the two sides connect is in [../../docs/ci-gate.md](../../docs/ci-gate.md).

Job definitions live in [`../ansible/roles/jenkins/templates/jcasc.yaml`](../ansible/roles/jenkins/templates/jcasc.yaml), and Ansible installs them. Shared implementation lives in [`../../vars/`](../../vars).

| Jenkins job | Jenkinsfile | Trigger | Responsibility |
|---|---|---|---|
| `app-cicd` | `Jenkinsfile.app-cicd` | Polls `main` every ~5 minutes; started by `platform-infra` when `DEPLOY_APP_AFTER_INFRA` is ticked; or by hand | Verify the GitHub `ci-gate` for the commit, changed-service tests, Sonar quality gate, image build and Trivy scan, ECR push, Helm deploy, runtime verification |
| `platform-infra` | `Jenkinsfile.platform-infra` | Manual only | Terraform plan/approval/apply or explicitly approved destroy; deploy/verify monitoring |

`platform-infra` stays manual because applying or destroying cloud infrastructure must not happen automatically for every push. Run it from protected `main` after `ci-gate` is green.

To run both in order for the first time, follow [../../docs/RUNBOOK.md](../../docs/RUNBOOK.md).

**First run of a parameterised job.** Jenkins reads the parameters of `platform-infra` and `app-cicd` from their Jenkinsfiles, so until each job has run once the UI offers **Build Now** instead of **Build with Parameters**. Run it once, let the first stage start, abort it, and the parameter form appears. The first polled `app-cicd` run, before the platform exists, fails early at *Ensure Kubernetes Access*, which is expected.

## What the GitHub Actions gate covers

Tests (Maven, frontend), Gitleaks, Hadolint, Ruff and the Python tests, ShellCheck, Terraform formatting, Trivy IaC and Helm chart scans, Ansible lint, Helm lint and rendering, kubeconform and the Helm image-tag contract. `app-cicd` does not repeat them. It still runs the checks that need AWS or the build host: backend and frontend tests for changed services, SonarQube analysis and quality gate, and Trivy scans on newly built images. `platform-infra` still runs Terraform init/validate/plan against the selected environment before requesting human approval. Staging values remain a configuration example; this does not claim the staging Terraform root is deployable.

## `Jenkinsfile.app-cicd`

Parameters:

| Parameter | Default | Meaning |
|---|---|---|
| `ENV` | `dev` | Environment/cluster to deploy. Staging is illustrative, not represented as fully supported. |
| `FORCE_ALL_TESTS` | false | Test all backend services; does not force image rebuilds. |
| `FORCE_ALL_BUILDS` | false | Build, scan, push and retag every image. |
| `SOURCE_REVISION` | empty | Full commit SHA to deploy. Empty means the latest `main`. Either way the commit needs a passing `ci-gate` unless `OVERRIDE_QUALITY_GATE` is ticked. |
| `OVERRIDE_QUALITY_GATE` | false | Emergency override: skip the `ci-gate` verification. Avoid for routine deployment. |
| `CLEAR_MAVEN_CACHE` | false | Delete Maven cache before compiling. |
| `RUN_DAST` | false | Optional OWASP ZAP baseline scan after deployment. |
| `INGRESS_HOST`, `TLS_CERT_ARN` | empty | Optional public DNS name and ACM certificate; public/shared deployments should supply both. |

Important stages: checkout the commit (the latest `main`, or `SOURCE_REVISION`); **Verify CI Gate** (waits up to 25 minutes for the GitHub `ci-gate` check of that exact SHA and fails unless it succeeded; uses the `github-token` credential through `scripts/python/ci/verify_github_gate.py`); authenticate to AWS; detect changed test targets and image targets independently; verify dev credential categories; run relevant Maven/frontend tests; compile untouched modules for Sonar; enforce the SonarQube quality gate; build/scan/push only images that need rebuilding; resolve existing tags for unchanged images; deploy with Helm; verify rollout and application access; optionally run DAST. Source lint/secrets/config scans are deliberately not repeated here.

`FORCE_ALL_TESTS` broadens test selection only. A change to `security-common` still triggers all backend tests and image rebuilds because every backend depends on that module. A first deploy or `FORCE_ALL_BUILDS` also builds all images.

## `Jenkinsfile.platform-infra`

Parameters:

| Parameter | Default | Meaning |
|---|---|---|
| `ACTION` | `Apply` | `Apply` provisions and deploys monitoring; `Destroy` tears the platform down |
| `ENV` | `dev` | Environment and cluster. Staging is illustrative |
| `ALERT_EMAIL_TO`, `SMTP_HOST`, `SMTP_USERNAME` | empty | Critical-alert email settings (`host:port` for the smarthost). Leave all empty to keep alert email disabled. The SMTP password is not a parameter; it comes from Secrets Manager |
| `DEPLOY_APP_AFTER_INFRA` | false | After a successful apply, wait for External Secrets and start `app-cicd` for the same commit |
| `CONFIRM_DESTROY` | false | Required when `ACTION=Destroy`; the run aborts before touching AWS without it |

Stages for `Apply`: clean workspace, checkout, check the bootstrap state and the operator credentials, `terraform init`, `validate`, `plan -detailed-exitcode`, approval (skipped when the plan has no changes), apply, write the kubeconfig, wait for the node group, nodes and CoreDNS, install metrics-server, install and verify the monitoring chart. Stages for `Destroy`: safety gate, destroy plan, a second approval, Helm releases and monitoring volumes removed, wait for the load balancers to disappear, `terraform destroy`, and (dev only) deletion of the three operator-credential secrets.

This is manually triggered. `ACTION=Apply` runs Terraform format/init/validate/plan, asks for approval when the plan has changes, then verifies EKS and deploys monitoring. `ACTION=Destroy` requires `CONFIRM_DESTROY=true`, displays and approves a destroy plan, removes Kubernetes releases, then destroys the Terraform-managed platform. ECR repositories are now part of this dev platform's destroy lifecycle and `force_delete=true` allows tagged images to be removed; rebuild images before redeploying. Keep the separate bootstrap/state infrastructure intact.

Dev operator credentials must exist under `<cluster>/credentials/{database,application,observability}`. The pipeline validates them before apply and deletes all three categories during the explicit platform destroy. The legacy `<cluster>/operator-credentials` secret is retained by migration until the operator verifies and manually removes it.
