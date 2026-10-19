# GitHub webhook: quality/security gate first

Catalogix uses a single webhook-driven Jenkins entry point: the multibranch job `quality-security-gate`. Do not configure a GitHub webhook directly for `app-cicd` or `platform-infra`.

## 1. Make Jenkins reachable securely

GitHub must be able to reach the Jenkins webhook endpoint over HTTPS:

```text
https://<your-jenkins-host>/github-webhook/
```

Use a DNS name and TLS-enabled reverse proxy or another approved secure ingress path. Do not open the Jenkins UI broadly to the internet just to receive webhooks. The project’s default Jenkins security group restricts port 8080 to operator IPs, so configure a controlled webhook-reachable endpoint or use the multibranch source's scheduled scan fallback in a network where inbound webhooks cannot be supported.

## 2. Configure the repository webhook

In GitHub: **Repository → Settings → Webhooks → Add webhook**. Configure the payload URL above, `application/json`, a strong secret if webhook-secret verification is configured, and enable these events only:

- Pushes
- Pull requests

The GitHub Branch Source plugin discovers branches and pull requests and runs `infra/jenkins/Jenkinsfile.lint`. Its daily folder scan is a recovery fallback, not the normal delivery trigger.

## 3. Protect `main`

Create a branch ruleset or branch protection rule for `main` and require the pull-request check produced by `quality-security-gate`. Run a test pull request first, then select the exact status-check context shown in GitHub. Require review if appropriate and disallow bypass except for trusted administrators.

## 4. Downstream flow

```text
GitHub push / pull request
          │
          ▼
quality-security-gate
  ├── failure → stop; no app deploy
  ├── pull request → publish required check only
  └── successful main push → start app-cicd with the tested commit SHA

platform-infra → manual only, after the main branch is green
```

The gate owns Gitleaks, Hadolint, Terraform formatting/IaC scans, Ansible lint, Ruff, Python tests, Helm validation, kubeconform, Helm image-tag contract validation, chart scanning and ShellCheck. `app-cicd` owns change-based Maven/frontend tests, SonarQube quality-gate enforcement, Trivy scanning of newly built images, deployment and runtime verification. This avoids repeating repository-wide scans in the delivery pipeline.

`platform-infra` is never auto-triggered by a push because a code change must not implicitly apply or destroy cloud infrastructure. Run it manually on protected `main` after the quality gate is green. If `platform-infra` is configured to start `app-cicd` after an apply, it passes the checked-out commit SHA to the downstream job.

For troubleshooting, inspect the `quality-security-gate` branch/PR build first. If GitHub webhooks are not arriving, review webhook delivery logs in GitHub and Jenkins system logs, verify the endpoint responds, and confirm the GitHub token has enough repository access for branch/PR discovery.
