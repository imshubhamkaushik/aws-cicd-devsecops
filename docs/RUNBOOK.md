# Runbook: from an empty AWS account to a running platform, and back

This is the one document to follow when you run Catalogix on AWS. Each phase says what you do, what you should see, and how long it takes. Times marked "about" are estimates; times marked "documented" come from the other READMEs.

```text
A  Local rehearsal (optional)      Docker Compose on your machine
B  Prepare the repository          bucket name, GitHub repository, token
C  Prepare your workstation        tools, AWS login, SSH key
D  Bootstrap                       state bucket, network, Jenkins + SonarQube, credentials
E  First look at Jenkins           jobs, SonarQube, the first gate run
F  Platform and application        platform-infra (Apply), which also deploys the app
G  Verify                          app, kubectl, Grafana, SonarQube, NetworkPolicy
H  Day-2 flow                      change, pull request, gate, deploy
I  Tear down                       return the account to clean
```

**This creates billable resources** (EKS, RDS, NAT gateway, two EC2 servers, an ALB and more). Plan to finish phases D to G in one sitting and run phase I when you are done. See [Cost and clean-up](../README.md#cost-and-clean-up).

---

## Prerequisites

| You need | Notes |
|---|---|
| An AWS account and an AWS CLI profile | The bootstrap creates VPCs, EC2, IAM roles and policies, S3, KMS and more, so the profile needs broad rights. A scoped IAM user is better than the root user; delete its access key when phase D is finished. Default region is `ap-south-1` |
| A Linux machine or WSL (Ubuntu) | The installer targets Linux. Python 3.9 or newer |
| A GitHub account | Jenkins clones from GitHub. This repository has to be pushed there (phase B) |
| A GitHub personal access token | Classic token with the `repo` scope is the simplest. Jenkins uses it to clone, discover branches and pull requests, and publish status checks |
| For phase A only: Docker with Compose | About 6 GB RAM and 4 CPUs free |
| Optional | An SMTP account for alert emails, a DNS name and an ACM certificate for HTTPS |

Passwords you choose later must be 8 to 64 characters with a lowercase letter, an uppercase letter and a digit, printable ASCII only, and none of `/ " @` or whitespace.

---

## Phase A: Local rehearsal (optional, about 15 minutes)

Proves the workload builds and runs before you spend money on AWS.

```bash
cp .env.example .env
python3 scripts/python/local/generate_jwt_keys.py      # writes the two RS256 key pairs into .env
docker compose up --build
python3 scripts/python/local/smoke_test.py             # in a second terminal
```

Expected: `smoke_test.py` reports success. The app is on http://localhost:11000 (login `admin@catalogix.local` / `AdminAdminAdmin`), Mailpit on http://localhost:8025. Stop with `docker compose down`. To start over with a clean database: `docker compose down -v`.

To run the same checks Jenkins runs (tests, Gitleaks, Trivy, SonarQube, smoke test):

```bash
python3 scripts/python/local/devsecops_check.py --start-sonarqube --shutdown
```

---

## Phase B: Prepare the repository (about 10 minutes)

### B1. Decide the GitHub repository

Create an **empty** repository on GitHub (private is fine). The URL in `infra/ansible/group_vars/jenkins/vars.yaml` must be that repository's HTTPS URL; Jenkins parses the owner and name from it:

```yaml
git_repo_url: "https://github.com/<your-user>/<your-repo>.git"
```

Also update the badge URL at the top of the root `README.md` to your user and repository.

### B2. Pick your own state bucket name

S3 bucket names are global, so `catalogix-tfstate` is almost certainly taken. Pick a unique lowercase name, then replace it everywhere (Terraform backends cannot use variables):

```bash
OLD=catalogix-tfstate
NEW=catalogix-tfstate-<your-name>-<digits>
grep -rlI "$OLD" infra scripts --include='*.tf' --include='*.py' --include='*.example' \
     --include='Jenkinsfile*' --include='*.md' | xargs sed -i "s/$OLD/$NEW/g"
grep -rI "$OLD" infra scripts --exclude-dir=__pycache__ || echo "no references left"
```

The files this touches are listed in [infra/terraform/README.md](../infra/terraform/README.md#state-bucket-name). The region `ap-south-1` can stay.

### B3. Keep secrets out of Git

`bootstrap.py ansible` writes an Ansible Vault file into the working tree. It is encrypted, but it should still never be committed. Make sure it is ignored:

```bash
echo 'infra/ansible/group_vars/all/vault.yaml' >> .gitignore
```

### B4. Push

```bash
git init -b main                      # skip if the folder is already a Git repository
git add -A
git status                            # check: no .env, no vault.yaml, no *.tfvars, no node_modules
git commit -m "Initial commit"
git remote add origin https://github.com/<your-user>/<your-repo>.git
git push -u origin main
```

The branch must be called `main`; the pipelines check out `main`. The gate runs Gitleaks over the full history, so a secret committed once and removed later still fails it.

---

## Phase C: Prepare your workstation (about 10 minutes)

From the repository root:

```bash
bash scripts/bash/install-all.sh          # Python if missing, then jq, ssh-keygen, AWS CLI v2, Terraform, Ansible + boto3 (in .venv)
aws configure                             # access key, secret, region ap-south-1
aws sts get-caller-identity               # must print your account
python3 scripts/python/setup/create_ssh_key.py     # creates ~/.ssh/catalogix-key and .pub
```

Expected: `python3 scripts/python/install_tools.py --check-only` lists every tool as installed.

---

## Phase D: Bootstrap (about 25 to 40 minutes, mostly waiting)

```bash
python3 scripts/python/aws/bootstrap.py
```

This runs four steps in order. Each can be re-run alone (`backend`, `infra`, `ansible`, `credentials`), and each skips work that is already done.

| Step | What happens | You answer |
|---|---|---|
| Checks | Verifies tools and AWS login, offers to install anything missing | `yes` to install |
| 1. State bucket (`backend`) | Terraform creates the versioned, encrypted S3 bucket | Review the plan, `yes` to apply |
| 2. Network and servers (`infra`) | Adds **your current IP** to the allowlist, then Terraform creates the VPC, NAT gateway, Jenkins (public subnet, Elastic IP) and SonarQube (private subnet) EC2 instances, and waits for their status checks | `yes` to proceed, review the plan, `yes` to apply |
| 3. Configuration (`ansible`) | Creates the Ansible Vault, then installs Docker, tools, SonarQube, Jenkins, plugins and JCasC in two phases (SonarQube first, then Jenkins) | See below |
| 4. Credentials (`credentials`) | Stores the passwords you choose in AWS Secrets Manager | See below |

**Ansible prompts** (first run only):

| Prompt | Value |
|---|---|
| Vault password (twice) | Any strong password. Saved to `~/.vault_pass` (mode 600). Keep a copy: without it the vault cannot be decrypted |
| Jenkins admin password | Your Jenkins login password for user `admin` |
| GitHub token | The token from the prerequisites |
| SonarQube admin password | Your SonarQube login password for user `admin` |

The SonarQube token is generated by Ansible and written into the vault; you never type it.

**Credentials prompts** (environment `dev`):

| Credential | Used for |
|---|---|
| RDS master password | The PostgreSQL instance and the database-role setup |
| Optional read-only database login | Leave blank to skip |
| RabbitMQ username (default `catalogix`) and password | The message broker |
| Seed admin password | Password of `admin@catalogix.local`, the first application admin |
| Grafana admin username (default `admin`) and password | Grafana login |

Expected at the end: `Full Bootstrap Completed Successfully.` Check:

```bash
python3 scripts/python/aws/credentials.py --env dev --check     # all three categories exist and pass validation
cd infra/terraform/bootstrap-infra && terraform output public_ip_jenkins && cd -
```

Write down the Jenkins IP. The Jenkins UI and SSH only accept connections from allowlisted IPs, so if your IP changes later, see [Day-2 operations](#h4-your-ip-changed).

---

## Phase E: First look at Jenkins (about 10 minutes)

1. Open `http://<jenkins-ip>:8080` and sign in as `admin` with the Jenkins admin password from the vault prompts.
2. Confirm three jobs exist: `quality-security-gate` (multibranch), `platform-infra`, `app-cicd`. Jenkins is configured entirely by JCasC, so anything you change in the UI is overwritten the next time Ansible runs.
3. **Manage Jenkins → System** shows the SonarQube server `sonarqube` and the library `catalogix-shared-library`.
4. Open `quality-security-gate`. If no branch has been indexed yet, click **Scan Repository Now**. The `main` build runs the gate (Gitleaks, Hadolint, Terraform fmt and Trivy IaC, Ansible lint, Ruff, Python tests, Helm lint and render, kubeconform, chart scan, ShellCheck). Expected: green.

> **Expected noise.** A green gate on `main` automatically starts `app-cicd`. The platform does not exist yet, so that run fails early (at the Kubernetes access stage). Ignore it; phase F runs it properly.

> **Parameters appear after a job has run once.** For `platform-infra` and `app-cicd`, Jenkins only learns the parameters from the Jenkinsfile when the job first runs, so the first click may show **Build Now** instead of **Build with Parameters**. Click it, wait until the first stage starts, then abort the build. From then on **Build with Parameters** is shown. Do this for `platform-infra` before phase F.

---

## Phase F: Platform and application (about 45 to 90 minutes)

In Jenkins open `platform-infra` → **Build with Parameters**:

| Parameter | Value |
|---|---|
| `ACTION` | `Apply` |
| `ENV` | `dev` |
| `DEPLOY_APP_AFTER_INFRA` | checked: deploys the application when the platform is ready |
| `ALERT_EMAIL_TO`, `SMTP_HOST`, `SMTP_USERNAME` | Leave empty to keep alert email disabled, or fill all three (for example `smtp.gmail.com:587`) |
| `CONFIRM_DESTROY` | unchecked |

What happens, in order:

1. Checkout from GitHub, check the state bucket and the three credential categories.
2. `terraform init`, `validate`, `plan`.
3. **Pause for approval.** Open the build's **Console Output** (or **Stage View**), read the plan, and click **Apply**. The first apply takes about 15 to 25 minutes (documented), mostly the EKS cluster.
4. Configure `kubectl`, wait for the node group and nodes, install metrics-server.
5. Install the monitoring stack (Prometheus, Grafana, Alertmanager).
6. Wait for External Secrets Operator, then start `app-cicd` for this commit.

`app-cicd` then runs: authenticate to AWS and ECR, detect changed services (the first run builds everything), run tests, SonarQube analysis and the quality gate, build and Trivy-scan the images, push to ECR, deploy with Helm, and verify the rollout. The first full run is the slowest part of the project.

Expected: both builds green. The last stage of `app-cicd` prints the ALB hostname; it can take a few minutes to appear.

If you did not tick `DEPLOY_APP_AFTER_INFRA`, run `app-cicd` yourself: **Build with Parameters**, tick `OVERRIDE_QUALITY_GATE`, and start it. Otherwise it refuses, because a manual run cannot prove the gate passed.

---

## Phase G: Verify (about 20 minutes)

### G1. The application

Find the address in the `app-cicd` console, or from the Jenkins host (next section). Open `http://<alb-hostname>` and sign in as `admin@catalogix.local` with the seed admin password you chose. Browse products, add to cart, place an order and pay with a mock card.

The cluster has no mail server, so verification and password-reset emails are not delivered. Use the seeded admin to explore.

### G2. kubectl, from the Jenkins host

Your laptop's IAM identity is not granted access to the cluster by default; the Jenkins role is. Use the Jenkins host:

```bash
ssh -i ~/.ssh/catalogix-key ubuntu@<jenkins-ip>
export KUBECONFIG=/var/lib/jenkins/.kube/catalogix-cluster-dev.config
sudo -E -u jenkins kubectl get pods -n catalogix
sudo -E -u jenkins kubectl get externalsecret -n catalogix                       # both should be Ready
sudo -E -u jenkins kubectl get ingress catalogix-gateway-ingress -n catalogix \
     -o jsonpath='{.status.loadBalancer.ingress[0].hostname}'; echo
sudo -E -u jenkins kubectl get networkpolicy -n catalogix
```

Expected: every pod `Running` and `Ready`, both ExternalSecrets `Ready`, an ALB hostname.

### G3. Grafana, Prometheus, Alertmanager

On the Jenkins host start a port-forward, and tunnel it to your machine from a second terminal:

```bash
# on the Jenkins host
sudo -E -u jenkins kubectl port-forward svc/monitoring-grafana 3000:80 -n monitoring
# on your machine
ssh -i ~/.ssh/catalogix-key -L 3000:localhost:3000 ubuntu@<jenkins-ip>
```

Open http://localhost:3000 and sign in with the Grafana credentials you chose. The **Catalogix Service Health** dashboard shows traffic, latency, errors, JVM and RabbitMQ panels. Prometheus is `svc/monitoring-kube-prometheus-stack-prometheus` on 9090 and Alertmanager `svc/monitoring-kube-prometheus-stack-alertmanager` on 9093; forward them the same way. To see an alert fire, scale a service to zero (`kubectl scale deploy/payment-svc --replicas=0 -n catalogix`, wait about 3 minutes for `ServiceUnavailable`) and scale it back to its previous count (see `kubectl get hpa -n catalogix`).

### G4. SonarQube

```bash
cd infra/terraform/bootstrap-infra && terraform output private_ip_sonarqube && cd -
ssh -i ~/.ssh/catalogix-key -L 9000:<sonarqube-private-ip>:9000 ubuntu@<jenkins-ip>
```

Open http://localhost:9000, user `admin`, the SonarQube admin password from the vault prompts.

### G5. NetworkPolicy

Run the four checks in [infra/helm/catalogix-hc/README.md](../infra/helm/catalogix-hc/README.md#testing-the-networkpolicy) from the Jenkins host. Expected: the two "blocked" checks time out, the public one succeeds.

Record your evidence in the table in [the README](../README.md#verification-status).

---

## Phase H: Day-2 flow

### H1. Ship a change

```bash
git switch -c my-change
# edit something, commit
git push -u origin my-change
```

Open a pull request on GitHub. Jenkins runs the gate for the pull request (run **Scan Repository Now** on `quality-security-gate` if you have not set up a webhook). Merge when it is green. The `main` build runs the gate again and then starts `app-cicd` for that commit, which tests and rebuilds only what changed.

To make GitHub notify Jenkins instead of scanning by hand, follow [github-webhook.md](github-webhook.md). Jenkins is not reachable from the internet by default, so this needs an HTTPS endpoint you control.

### H2. Change a password

```bash
python3 scripts/python/aws/credentials.py --env dev --show                    # names only, never values
python3 scripts/python/aws/credentials.py --env dev --only seed_admin_password
```

External Secrets refreshes hourly and pods read secrets at start, so restart the affected workload afterwards (`kubectl rollout restart deploy/<name> -n catalogix`).

### H3. Re-run or change infrastructure

Edit Terraform, push through a pull request, then run `platform-infra` (`Apply`). A plan with no changes skips the approval step.

### H4. Your IP changed

```bash
python3 scripts/python/aws/allowlist.py list
python3 scripts/python/aws/bootstrap.py infra          # adds your new IP and updates the security groups
# then run platform-infra (Apply) so the EKS API allowlist is updated too
```

The allowlist is additive, so older IPs keep working until you prune them (`allowlist.py prune --older-than 30`).

### H5. Rebuild every image

Run `app-cicd` with `FORCE_ALL_BUILDS` ticked (use it after changing base images or tooling).

---

## Phase I: Tear down

Order matters. The platform must go before the network.

1. **Platform.** In Jenkins run `platform-infra` with `ACTION=Destroy`, `ENV=dev`, `CONFIRM_DESTROY` ticked. Review the destroy plan and approve the second prompt. The pipeline uninstalls the Helm releases, deletes the monitoring volumes, waits for the load balancers to disappear, runs `terraform destroy` and finally deletes the three dev credential secrets. Allow about 20 to 30 minutes [estimate].
2. **Bootstrap layer.** On your workstation:
   ```bash
   python3 scripts/python/aws/destroy_infra.py          # answer yes; removes the NAT gateway, VPC and both EC2 servers
   ```
3. **Check the account is clean** (region `ap-south-1`):
   ```bash
   aws eks list-clusters
   aws rds describe-db-instances --query 'DBInstances[].DBInstanceIdentifier'
   aws elbv2 describe-load-balancers --query 'LoadBalancers[].LoadBalancerName'
   aws ec2 describe-nat-gateways --filter Name=state,Values=available --query 'NatGateways[].NatGatewayId'
   aws ec2 describe-instances --filters Name=instance-state-name,Values=running --query 'Reservations[].Instances[].InstanceId'
   aws ec2 describe-volumes --filters Name=status,Values=available --query 'Volumes[].VolumeId'
   aws ecr describe-repositories --query 'repositories[].repositoryName'
   aws secretsmanager list-secrets --query 'SecretList[].Name'
   ```
   Every list should be empty (or contain only things that are not Catalogix's). Elastic IPs and volumes left behind keep billing.
4. **The state bucket** is kept on purpose (`prevent_destroy`) and costs almost nothing. To remove it, empty it including all object versions in the S3 console, then delete it.
5. **Local clean-up.** Revoke the GitHub token, delete the AWS access key you used, and remove `~/.vault_pass`, `infra/ansible/group_vars/all/vault.yaml` and the SSH key pair if you no longer need them.

To start again later, repeat from phase D. Phase D re-creates the credentials; the state bucket and your repository are reused.

---

## Troubleshooting

| Symptom | Likely cause and fix |
|---|---|
| `No SSH public key found` during bootstrap | Run `python3 scripts/python/setup/create_ssh_key.py` |
| `BucketAlreadyExists` or a 403 on the state bucket | The name is taken by someone else. Redo [B2](#b2-pick-your-own-state-bucket-name) with a different name |
| SSH or the Jenkins UI times out | Your IP is not on the allowlist. See [H4](#h4-your-ip-changed) |
| Ansible cannot decrypt the vault | Wrong or missing `~/.vault_pass`. The tool lets you retry three times and never overwrites the vault |
| Jenkins fails to start after Ansible | JCasC rejects unknown keys on purpose. Check `sudo journalctl -u jenkins` on the Jenkins host, fix `jcasc.yaml`, re-run `bootstrap.py ansible` |
| No jobs in Jenkins, or "Couldn't find any revision" | `git_repo_url` is wrong, the repository is empty, or the token cannot read it. Fix it, re-run `bootstrap.py ansible` |
| Gate fails on Gitleaks | A secret is in the history. Rotate it, then rewrite the history; deleting the file is not enough |
| `platform-infra` fails at *Precheck Bootstrap State* | The bucket name in `Jenkinsfile.platform-infra` does not match the real bucket, or bootstrap never finished |
| `platform-infra` fails at *Verify Operator Credentials* | A category is missing or invalid. `python3 scripts/python/aws/credentials.py --env dev --check`, then `bootstrap.py credentials` |
| `app-cicd`: "This job is normally started by quality-security-gate" | Start it from a green gate, from `platform-infra`, or tick `OVERRIDE_QUALITY_GATE` for a deliberate manual run |
| Pods stuck `Pending` or `CreateContainerConfigError` | The Secret does not exist yet. `kubectl get externalsecret -n catalogix` and the External Secrets pods' logs in `external-secrets` |
| ALB hostname never appears | Check the AWS Load Balancer Controller pods in `kube-system` and the ingress events (`kubectl describe ingress -n catalogix`) |
| Trivy blocks an image | Fix the dependency or base image. Only unfixed CVEs are ignored; a documented, expiring `.trivyignore` entry is the last resort |
| Terraform reports a state lock | Another run is active, or one was killed. Wait, or remove the stale `.tflock` object from the state bucket only if you are sure nothing is running |
| `kubectl` from your laptop is denied | Expected: only the Jenkins role has cluster access. Use the Jenkins host ([G2](#g2-kubectl-from-the-jenkins-host)) |
