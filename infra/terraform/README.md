# Terraform

Infrastructure as code for Catalogix, in three independent roots with their own state files. Terraform 1.12 or newer, AWS provider 6.x.

```text
infra/terraform/
  backend-bootstrap/    S3 bucket that holds the state of the other two roots
  bootstrap-infra/      VPC, subnets, NAT gateway, Jenkins and SonarQube EC2, IAM, security groups, key pair
  platform-infra/
    env/dev/            root module for the dev cluster (the maintained environment)
    env/staging/        same pattern for staging (not kept in step with dev)
    modules/
      alb/              IAM role and policy for the AWS Load Balancer Controller (IRSA); the Helm release itself is in env/<env>/main.tf
      db-roles/         per-service database and login role, optional read-only login (PostgreSQL provider)
      ecr/              one private repository per image, immutable tags, scan on push, lifecycle policy
      eks/              cluster, managed node group, OIDC provider (IRSA), add-ons, access entries
      eso/              External Secrets Operator (Helm), its IRSA role and the ClusterSecretStore
      rds/              PostgreSQL instance, subnet group, endpoint published to SSM Parameter Store
      secrets-manager/  generic "write this map as a secret" module
      security-groups/  RDS security group, the rules that let EKS nodes and Jenkins reach Postgres, and the rule that lets Jenkins reach the EKS API
```

## Why three roots

- **backend-bootstrap** creates the bucket that stores everyone else's state, so it cannot store its own state remotely. Apply it once per AWS account and region. The bucket is versioned, encrypted (AES256), blocks public access, enforces bucket-owner ownership and has `prevent_destroy`.
- **bootstrap-infra** creates the VPC and NAT gateway (slow to create and billed while idle) and the two CI servers. Apply it once and leave it.
- **platform-infra** creates everything that can be rebuilt: EKS, RDS, ECR, the ALB controller, External Secrets and the secrets. It reads the VPC and subnet ids from `bootstrap-infra` through `terraform_remote_state`, so nothing is copied by hand, and it can be destroyed without touching the network.

State is stored in S3 with native lockfile locking (`use_lockfile = true`) under these keys: `bootstrap-infra/terraform.tfstate` and one `platform-infra/<env>/...` key per environment.

## Prerequisites

1. AWS CLI authenticated: `aws sts get-caller-identity`.
2. The state bucket exists. `python3 scripts/python/bootstrap.py backend` creates it; by hand, apply `backend-bootstrap` first.
3. Optional `terraform.tfvars` in the directory you are working in. Each root ships a `terraform.tfvars.example` with the variables and defaults; copy it to `terraform.tfvars` (git-ignored) only for values you want to change. In `bootstrap-infra` the SSH public key is the one required value, and `scripts/python/bootstrap_infra.py` reads it from `~/.ssh/catalogix-key.pub`.
4. For `platform-infra`: the operator credentials secret must exist and be complete (see [Secrets](#secrets)).

Prefer the scripts and pipelines, which add the checks listed in [infra/README.md](../README.md). The manual commands below are what they run.

## backend-bootstrap

```bash
cd infra/terraform/backend-bootstrap
terraform init
terraform plan -out=tfplan
terraform apply tfplan
```

## bootstrap-infra

Creates the VPC (two public and two private subnets across `ap-south-1a` and `ap-south-1b`), an internet gateway, `nat_gateway_count` NAT gateways (default 1), the Jenkins instance with an Elastic IP in a public subnet, the SonarQube instance in a private subnet, IAM roles including a permissions boundary for Jenkins, security groups and the EC2 key pair from your public key. Both instances default to `c7i-flex.large`.

```bash
cd infra/terraform/bootstrap-infra
cp terraform.tfvars.example terraform.tfvars      # optional; by hand pass the SSH key with TF_VAR_public_key
(cd ../../../scripts/python && python3 allowlist.py sync)   # adds your current IP to the allowlist and writes allowed_cidrs.auto.tfvars.json
terraform init
terraform plan -out=tfplan
terraform apply tfplan
```

Do not use `-auto-approve` here: this layer holds the network that `platform-infra` depends on and is slow to recreate.

Outputs used elsewhere: `public_ip_jenkins`, `jenkins_sg_id`, `vpc_id`, `public_subnets`, `private_subnets`, `jenkins_role_arn`, `allowed_cidrs`. `platform-infra` reads the network ones automatically.

## platform-infra

```bash
cd infra/terraform/platform-infra/env/dev
terraform init
terraform validate
terraform plan -out=tfplan
terraform apply tfplan
```

In normal use the `platform-infra` Jenkins job does this, with a Trivy scan before and a human approval between plan and apply. The first apply takes 15 to 25 minutes, mostly the EKS cluster.

What `env/dev` creates:

| Resource | Notes |
|---|---|
| EKS cluster | Kubernetes 1.36, public endpoint restricted to the Jenkins IP and the operator allowlist, secrets encrypted with a KMS key, `API_AND_CONFIG_MAP` authentication |
| Managed node group | `c7i-flex.large`, on demand, AL2023. Dev: min 2, desired 4, max 5 |
| EKS add-ons | vpc-cni (with network policy enabled), coredns, kube-proxy, aws-ebs-csi-driver, all with pinned versions so nothing upgrades silently |
| `gp3-sc` StorageClass | `WaitForFirstConsumer`, not the cluster default. Used by Prometheus, Grafana and RabbitMQ |
| RDS | PostgreSQL 18.1, `db.t4g.micro`, 20 GB growing to 100 GB, encrypted, not publicly accessible |
| ECR | One repository per image |
| ALB controller | Helm release 1.11.0 with its IRSA role |
| External Secrets Operator | Helm release 0.9.20 with an IRSA role limited to `GetSecretValue` and `DescribeSecret` on this environment's secrets, plus the `aws-secrets-manager` ClusterSecretStore |
| Database roles | One database and one role per service, with generated passwords |
| Secrets Manager secrets | See below |

The PostgreSQL provider connects to RDS from the Jenkins host, which the RDS security group allows. `module.eso` waits for the cluster and the ALB controller (`wait = true` on the chart so the CRDs exist before the ClusterSecretStore is created).

### Secrets

| Secret | Contents | Written by |
|---|---|---|
| `<cluster>/operator-credentials` | `db_master_password`, optional `db_readonly_username` and `db_readonly_password`, RabbitMQ user and password, admin password, Grafana login | `python3 scripts/python/credentials.py` (you choose them). Terraform only reads it |
| `<cluster>/db-credentials` | RDS master user and password | Terraform |
| `<cluster>/app-secrets` | `db_user_<svc>` and `db_password_<svc>` for each service | Terraform |
| `<cluster>/jwt-keys` | `jwt_private_key_b64`, `jwt_public_key_b64`, `internal_jwt_private_key_b64`, `internal_jwt_public_key_b64` | Terraform (`tls_private_key.jwt_user_access` and `tls_private_key.internal_service`) |
| `<cluster>/alerting-secrets` | `smtp_password` | Terraform, from `var.smtp_password` (empty until you supply one) |

A `terraform_data` check fails the plan with a clear message if `db_master_password` is missing from the operator secret. Secrets Manager secrets are deleted immediately on destroy (recovery window 0) so a later apply can reuse the name, which is a dev convenience.

Terraform state contains the generated private keys and database passwords. The state bucket is encrypted and private; keep its IAM access tight.

Only one writer may own each Secrets Manager secret. Do not edit Terraform-managed secret versions by hand.

### Destroying platform-infra

Kubernetes resources that own cloud objects must be removed first, or AWS keeps the load balancers and network interfaces and Terraform cannot delete the subnets and security groups. The `Destroy` action of `Jenkinsfile.platform-infra` does this in order. By hand:

```bash
helm uninstall catalogix -n catalogix || true
helm uninstall monitoring -n monitoring || true
kubectl delete pvc --all -n monitoring || true      # releases the EBS volumes (gp3-sc has reclaimPolicy Delete)
sleep 60                                            # the ALB controller deprovisions the load balancers
cd infra/terraform/platform-infra/env/dev
terraform destroy
```

Then destroy the bootstrap layer with `python3 scripts/python/destroy_infra.py`. The state bucket is protected by `prevent_destroy` and is left in place.

## State bucket name

The bucket name `catalogix-tfstate` is written literally because Terraform backend blocks cannot use variables. To use another name, replace it in all of these:

- `infra/terraform/backend-bootstrap/main.tf`
- `infra/terraform/bootstrap-infra/providers.tf`
- `infra/terraform/bootstrap-infra/iam.tf` (two policy ARNs)
- `infra/terraform/platform-infra/env/dev/providers.tf` and `env/dev/main.tf` (remote state)
- `infra/terraform/platform-infra/env/staging/providers.tf` and `env/staging/main.tf`
- `infra/jenkins/Jenkinsfile.platform-infra` (the bootstrap state check)
- `scripts/python/allowlist.py` (or set `TFSTATE_BUCKET` in the environment)
- `scripts/python/backend_bootstrap.py`

The region `ap-south-1` is set in the same files plus the variable defaults.

## Adding an environment

To add `prod`:

1. Copy `platform-infra/env/staging/` to `platform-infra/env/prod/`.
2. Change the `key` in `providers.tf` to a new state path such as `platform-infra/prod/terraform.tfstate`.
3. Change `local.env_prefix` (the cluster name) to `catalogix-cluster-prod` to match what the pipelines compute.
4. Set `min_size`, `max_size` and `desired_size` for production capacity.
5. In the RDS module call set `skip_final_snapshot = false` and `backup_retention_period` to at least 7.
6. Add `infra/helm/catalogix-hc/values-prod.yaml` and add `prod` to the `ENV` choices in both Jenkinsfiles and to `CLUSTER_NAMES` in `scripts/python/credentials.py`.

Staging shares the VPC from `bootstrap-infra`. A real production environment should have its own network layer, ideally in a separate AWS account.
