# The CI gate: GitHub Actions checks, Jenkins verifies

The repository quality and security checks run in GitHub Actions. Jenkins deploys only commits that passed them.

```text
push / pull request ──► GitHub Actions: .github/workflows/ci.yaml
                          backend · frontend · tooling · security · infra
                                         │
                                         ▼
                                      ci-gate   (succeeds only if every job above succeeded)
                                         │
              pull request ──► required status check blocks the merge
              push to main ──► Jenkins app-cicd polls main, then asks GitHub:
                                "did ci-gate pass for this exact commit?"
                                         │ yes
                                         ▼
                          tests · SonarQube · image scan · ECR · Helm · verify
```

## Why it is built this way

- The checks need nothing from AWS, so they do not need Jenkins.
- GitHub cannot reach the Jenkins UI (it accepts only operator IPs), so a push cannot trigger Jenkins. Jenkins **pulls** instead: `app-cicd` polls `main` every ~5 minutes and checks GitHub for the result.
- The checks run once. `app-cicd` no longer repeats them.
- SonarQube stays in Jenkins because it sits in a private subnet that GitHub runners cannot reach.

## What runs where

| Where | What |
|---|---|
| GitHub Actions (`ci.yaml`) | Maven and frontend tests, Gitleaks, Hadolint, Ruff, Python tests, ShellCheck, Terraform fmt, Trivy IaC and chart scans, Ansible lint, Helm lint, render and kubeconform, Helm image-tag contract |
| Jenkins `app-cicd` | Verify `ci-gate`, tests of changed services, SonarQube, image build and Trivy image scan, ECR push, Helm deploy, runtime verification |
| Jenkins `platform-infra` | `terraform plan` and apply on live state, monitoring install, destroy |

## Branch protection

The gate only blocks a merge if you require it. In GitHub: **Settings → Branches → Add branch ruleset** (or a branch protection rule) for `main`:

1. Require a pull request before merging.
2. Require status checks to pass, and add **`ci-gate`**. The name appears in the list after the workflow has run once.
3. Optionally require branches to be up to date and block force pushes.

Do not require the individual jobs; `ci-gate` already depends on all of them, so adding a job to the workflow needs no change here (add it to `needs:` of `ci-gate`).

## How Jenkins verifies it

`Jenkinsfile.app-cicd` has a stage `Verify CI Gate`, right after checkout. It runs `scripts/python/ci/verify_github_gate.py` with the `github-token` Jenkins credential:

- It asks the GitHub API for the `ci-gate` check run of the exact commit being deployed.
- While the check is missing, queued or running it waits (up to 25 minutes), because Jenkins can pick up a commit before the workflow finishes.
- It passes only on a completed check run with the conclusion `success`. A failure, a cancelled or skipped run, a timeout, or an API error such as a bad token fails the build before anything is built.
- `OVERRIDE_QUALITY_GATE` skips the check for an emergency. The build log says so.

The token needs permission to read the repository's checks. A classic personal access token with the `repo` scope is enough. It is stored once, in the Ansible vault, and Jenkins exposes it as the credential `github-token`.

## Changing it

- **Add a check:** add a step or job to `ci.yaml` and, for a new job, add it to the `needs:` list of `ci-gate`.
- **Rename the gate job:** change `--check-name` in `Jenkinsfile.app-cicd` and the required check in GitHub.
- **Tool versions:** Trivy and Helm versions are read from `infra/ansible/group_vars/all/vars.yaml`, the same file that installs them on the Jenkins server.
- **Deploy faster than the poll interval:** click **Build Now** on `app-cicd`, or change the `scm('H/5 * * * *')` trigger in `jcasc.yaml`.

## Troubleshooting

| Symptom | Cause and fix |
|---|---|
| `Verify CI Gate` fails with HTTP 401, 403 or 404 | The token is wrong, expired, or cannot read this repository's checks. Update `vault_github_token` and re-run `bootstrap.py ansible` |
| `Verify CI Gate` times out, no `ci-gate` check | The workflow did not run (Actions disabled, or the commit is not on GitHub). Open the Actions tab |
| `ci-gate` is red | Fix the failing job and push; the new commit gets its own check |
| `app-cicd` never starts on its own | Check **Polling Log** on the job page, that Jenkins can reach github.com, and that the `github-token` credential can read the repository |
