# Terraform

Infrastructure-as-Code for the Catalogix platform, split into two independent layers with separate state files.

---

## Two-Layer Architecture

```
terraform/
├── backend-bootstrap         # Layer 0 - provision S3 for storing remote state backend and state lock
│   └── (S3)
├── bootstrap-infra/          # Layer 1 — stable, apply once
│   └── (VPC, EC2, IAM, security groups)
│
└── platform-infra/           # Layer 2 — torn down and rebuilt freely
    ├── env/
    │   ├── dev/              # Dev environment root module
    │   └── staging/          # Staging environment root module (same VPC, isolated cluster)
    └── modules/              # Reusable modules called by env roots
        ├── alb/
        ├── ecr/
        ├── eks/
        ├── eso/
        ├── rds/
        ├── secrets-manager/
        └── security-groups/
```

**Why two layers?**

`bootstrap-infra` provisions the VPC and NAT Gateway, which take 10–15 minutes to create and cost money even when idle. It is applied once and left running. `platform-infra` provisions everything else (EKS, RDS, ECR). It reads VPC outputs from `bootstrap-infra` via `terraform_remote_state` and can be destroyed and rebuilt freely without touching the network layer.

Both layers store their state in the same S3 bucket (`catalogix-tfstate`) under different keys.

---

## Prerequisites

Before running any `terraform` commands:

1. AWS CLI authenticated: `aws sts get-caller-identity`
2. Terraform state backend exists (S3 bucket). The `bootstrap.py` script creates these automatically. If running manually, apply `terraform/backend-bootstrap/` first.
3. `terraform.tfvars` exists in the directory you are working in. Copy `terraform.tfvars.example` and fill in your values.

---

## S3 State Bucket Name

The bucket name `catalogix-tfstate` is hardcoded in the Terraform backend configurations. Terraform backends do not support variable interpolation — this is a known limitation.

If you fork this project and need a different bucket name, do a global find-and-replace for `catalogix-tfstate` across:
- `terraform/bootstrap-infra/providers.tf`
- `terraform/platform-infra/env/dev/providers.tf`
- `terraform/platform-infra/env/staging/providers.tf`
- `terraform/platform-infra/env/dev/main.tf` (remote state config)
- `terraform/platform-infra/env/staging/main.tf` (remote state config)
- `scripts/python/backend_bootstrap.py`

---

## Layer 0 - backend-bootstrap

Apply once per AWS account/region. Creates: S3

```bash
cd terraform/backend-bootstrap

terraform init
terraform validate
terraform plan -out=tfplan       # review the plan before applying
terraform apply tfplan           # applies only the reviewed plan
```

This layer creates S3 resource that is used for storing remote state and state lock where the remote state of bootstrap-infra and platform-infra are stored.

## Layer 1 — bootstrap-infra

Apply once per AWS account/region. Creates: VPC, public/private subnets, NAT Gateway, Jenkins EC2, SonarQube EC2, IAM roles, security groups.

```bash
cd terraform/bootstrap-infra

# Copy and fill in your variable values
cp terraform.tfvars.example terraform.tfvars
# Edit terraform.tfvars — at minimum, confirm aws_region and key_name

terraform init
terraform validate
terraform plan -out=tfplan       # review the plan before applying
terraform apply tfplan           # applies only the reviewed plan
```

**Do not use `-auto-approve` on bootstrap-infra.** This layer creates VPC and NAT Gateway resources that are expensive to recreate and that platform-infra depends on.

Outputs from this layer (VPC ID, subnet IDs, Jenkins role ARN) are read automatically by platform-infra via `terraform_remote_state`. You do not need to copy them manually.

---

## Layer 2 — platform-infra (dev)

Apply after bootstrap-infra. Creates: EKS cluster, EKS node group, RDS (PostgreSQL 18.1), ECR repositories, External Secrets Operator, IAM roles for IRSA (ESO, EBS CSI driver, ALB controller), and the gp3 StorageClass.

```bash
cd terraform/platform-infra/env/dev

terraform init
terraform validate
terraform plan -out=tfplan      # always review before applying
terraform apply tfplan
```

First apply takes 15–25 minutes (EKS cluster dominates).

### Destroying platform-infra

Before running `terraform destroy`, the Helm releases and Kubernetes resources must be removed first, otherwise AWS will not release the load balancers and the VPC ENIs, blocking Terraform from deleting subnets and security groups.

The `Jenkinsfile.platform-infra` `Destroy` stage handles this ordering automatically. If running manually:

```bash
# 1. Remove Helm releases (application and monitoring)
helm uninstall catalogix -n catalogix || true
helm uninstall kube-prometheus-stack -n monitoring || true

# 2. Delete PVCs so EBS volumes are released (gp3-sc uses reclaimPolicy: Delete)
kubectl delete pvc --all -n monitoring || true

# 3. Wait for load balancers to be deprovisioned by the ALB controller
#    (typically 30–60 seconds after Helm uninstall)
sleep 60

# 4. Destroy Terraform
cd terraform/platform-infra/env/dev
terraform destroy   # will prompt for confirmation
```

---

## Module Reference

| Module | What it creates |
|---|---|
| `modules/eks` | EKS cluster, managed node group, OIDC provider, access entries |
| `modules/rds` | RDS PostgreSQL instance, subnet group, SSM parameter for endpoint |
| `modules/ecr` | ECR repositories with lifecycle policies |
| `modules/alb` | IAM role + policy for the AWS Load Balancer Controller (IRSA) |
| `modules/eso` | ESO Helm release, IAM role + policy (IRSA), ClusterSecretStore, ExternalSecret |
| `modules/secrets-manager` | Secrets Manager secret + version with DB credentials |
| `modules/security-groups` | RDS security group, EKS node security group rules |

---

## Adding a New Environment

To add a `prod` environment:

1. Copy `terraform/platform-infra/env/staging/` to `terraform/platform-infra/env/prod/`
2. Update the `key` in the backend config (`providers.tf`) to `platform-infra/prod/terraform.tfstate`
3. Update `local.env_prefix` to `"catalogix-prod"`
4. Adjust `min_size`, `max_size`, `desired_size` for production capacity
5. Set `skip_final_snapshot = false` in the RDS module call
6. Set `backup_retention_period` to at least 7 in the RDS module call
7. Create a matching `helm/catalogix-hc/values-prod.yaml`

The staging environment shares the VPC from bootstrap-infra. Prod should have its own bootstrap-infra stack in a separate AWS account.