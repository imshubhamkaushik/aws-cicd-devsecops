# Root module for platform-infra: EKS, RDS, ECR, ALB controller, ESO and secrets.

# Read VPC and subnet outputs from bootstrap-infra, which must be applied first.
data "terraform_remote_state" "bootstrap" {
  backend = "s3"
  config = {
    bucket = "catalogix-tfstate"
    key    = "bootstrap-infra/terraform.tfstate"
    region = "ap-south-1"
  }
}

# AWS data sources for dynamic values and to avoid hardcoding ARNs or account IDs.
data "aws_caller_identity" "current" {}

data "aws_iam_session_context" "current" {
  arn = data.aws_caller_identity.current.arn
}

# Credentials chosen by the operator (python bootstrap.py credentials) live in one
# Secrets Manager secret, "<env_prefix>/operator-credentials", which this pipeline reads.
# Only the RDS master password passes through Terraform (and so lands in the encrypted
# state); the app admin, RabbitMQ and Grafana passwords go straight to External Secrets.
data "aws_secretsmanager_secret_version" "operator" {
  secret_id = "${local.env_prefix}/operator-credentials"
}

locals {
  operator_credentials = jsondecode(data.aws_secretsmanager_secret_version.operator.secret_string)

  db_master_password = lookup(local.operator_credentials, "db_master_password", "")

  # Optional read-only database login (both keys, or neither).
  db_readonly_username = lookup(local.operator_credentials, "db_readonly_username", "")
  db_readonly_password = lookup(local.operator_credentials, "db_readonly_password", "")
}

# Same password policy as scripts/python/credentials.py; backstop for manual runs.
resource "terraform_data" "operator_credentials_check" {
  lifecycle {
    precondition {
      condition     = length(local.db_master_password) > 0
      error_message = "db_master_password is missing from ${local.env_prefix}/operator-credentials. Run: python scripts/python/credentials.py --env dev"
    }
    precondition {
      condition = (
        length(local.db_master_password) >= 8 &&
        length(local.db_master_password) <= 64 &&
        can(regex("[a-z]", local.db_master_password)) &&
        can(regex("[A-Z]", local.db_master_password)) &&
        can(regex("[0-9]", local.db_master_password)) &&
        !can(regex("[/\"@[:space:]]", local.db_master_password))
      )
      error_message = "db_master_password must be 8-64 characters with a lowercase letter, an uppercase letter and a digit, and must not contain / \" @ or whitespace (RDS rejects those). Change it with: python scripts/python/credentials.py --env dev --only db_master_password"
    }
    precondition {
      condition     = (local.db_readonly_username == "") == (local.db_readonly_password == "")
      error_message = "db_readonly_username and db_readonly_password must be set together (or both left out) in ${local.env_prefix}/operator-credentials."
    }
  }
}

# JWT signing key shared by all backend services. keepers prevent rotation on refresh,
# since rotating invalidates every live session.
resource "random_password" "jwt" {
  length  = 48
  special = false

  keepers = {
    cluster_name = local.env_prefix
  }
}



# Customer-managed KMS key for EKS secrets encryption.
resource "aws_kms_key" "eks" {
  description             = "EKS secrets encryption key - dev environment"
  deletion_window_in_days = 7
  enable_key_rotation = true
}

# Shared values from other modules and remote state.
locals {
  # Pulled from remote state so every module uses the same source of truth
  vpc_id          = data.terraform_remote_state.bootstrap.outputs.vpc_id
  private_subnets = data.terraform_remote_state.bootstrap.outputs.private_subnets
  public_subnets  = data.terraform_remote_state.bootstrap.outputs.public_subnets

  # Pulled from EKS module output — used in providers.tf for kubernetes/helm
  cluster_name        = module.eks.cluster_name
  cluster_endpoint    = module.eks.cluster_endpoint
  cluster_certificate = module.eks.cluster_certificate

  # Required on every IAM role created below; see bootstrap-infra/iam.tf.
  permissions_boundary_arn = data.terraform_remote_state.bootstrap.outputs.jenkins_boundary_arn

  # Env-specific prefix
  env_prefix = "${var.cluster_name}-${var.environment}"

  # Single source of truth for the DB username - referenced by RDS, Secrets Manager, and Helm
  db_username = "catalogix"
}

# Security Groups — all in the shared VPC from bootstrap
module "sg" {
  source       = "../../modules/security-groups"
  project_name = local.env_prefix
  vpc_id       = local.vpc_id
  vpc_cidr     = data.terraform_remote_state.bootstrap.outputs.vpc_cidr

  jenkins_sg_id     = data.terraform_remote_state.bootstrap.outputs.jenkins_sg_id
  eks_cluster_sg_id = module.eks.cluster_sg_id # implicit depends_on module.eks
}

# EKS
module "eks" {
  source = "../../modules/eks"

  cluster_name    = local.env_prefix
  cluster_version = "1.36"
  private_subnets = local.private_subnets

  # Desired size leaves room for RabbitMQ replicas to spread across nodes.
  min_size     = 2
  max_size     = 5
  desired_size = 4

  # KMS key for EKS secrets
  kms_key_arn = aws_kms_key.eks.arn

  jenkins_role_arn  = data.terraform_remote_state.bootstrap.outputs.jenkins_role_arn
  jenkins_public_ip = data.terraform_remote_state.bootstrap.outputs.public_ip_jenkins

  # my_ip_cidr is captured once from bootstrap-infra state rather than re-queried here.
  my_ip_cidr = data.terraform_remote_state.bootstrap.outputs.jenkins_my_ip_cidr

  # Whoever runs terraform apply automatically gets console access.
  # No variable or tfvars entry needed.
  console_iam_arn = data.aws_iam_session_context.current.issuer_arn

  # permissions_boundary_arn is required on every IAM role created in this module.
  permissions_boundary_arn = local.permissions_boundary_arn
}

# EKS data
data "aws_eks_cluster" "this" {
  name = module.eks.cluster_name

  depends_on = [module.eks]
}

# ECR — global, no VPC dependency
module "ecr" {
  source = "../../modules/ecr"
  repositories = [
    "catalogix-user-svc", "catalogix-catalog-svc", "catalogix-inventory-svc", "catalogix-cart-svc", 
    "catalogix-payment-svc", "catalogix-checkout-svc", "catalogix-notification-svc",
    "catalogix-frontend", "catalogix-gateway"
  ]
}

# ALB Controller (installs the AWS Load Balancer Controller into EKS)
module "alb" {
  source = "../../modules/alb"

  cluster_name             = module.eks.cluster_name
  oidc_provider_arn        = module.eks.oidc_provider_arn
  oidc_provider            = trimprefix(module.eks.oidc_provider_arn, "arn:aws:iam::${data.aws_caller_identity.current.account_id}:oidc-provider/")
  permissions_boundary_arn = local.permissions_boundary_arn

  depends_on = [module.eks, module.sg]
}

# RDS
module "rds" {
  source = "../../modules/rds"

  project_name = "${local.env_prefix}-db"
  # Initial database required by RDS; unused. Per-service databases come from
  # module.db_roles below.
  db_name                 = "catalogix-admin"
  username                = local.db_username
  password                = local.db_master_password
  private_subnets         = local.private_subnets
  security_group_id       = module.sg.rds_sg
  db_engine_version       = "18.1"
  backup_retention_period = 0
  multi_az                = false
  skip_final_snapshot     = true

  ssm_parameter_path = "/${local.env_prefix}/rds-endpoint"

  # Never create the database with a password that failed the policy check.
  depends_on = [terraform_data.operator_credentials_check]
}

# Per-service databases + roles (see modules/db-roles/main.tf). Database names use
# hyphens (catalogix-users, catalogix-catalog, ...) and must match
# postgres-init/01-create-databases.sh and helm/catalogix-hc values-*.yaml `dbName`.
module "db_roles" {
  source = "../../modules/db-roles"

  services = {
    "user-svc"         = "catalogix-users"
    "catalog-svc"      = "catalogix-catalog"
    "inventory-svc"    = "catalogix-inventory"
    "cart-svc"         = "catalogix-cart"
    "payment-svc"      = "catalogix-payment"
    "checkout-svc"     = "catalogix-checkout"
    "notification-svc" = "catalogix-notification"
  }

  # Optional read-only login, active only when both keys are in the operator secret.
  readonly_username = local.db_readonly_username
  readonly_password = local.db_readonly_password

  depends_on = [module.rds]
}

# Secrets Manager: stores generated DB credentials for the app to read via ESO.
module "secrets" {
  source = "../../modules/secrets-manager"

  # Namespaced to avoid collisions across environments.
  secret_name = "${local.env_prefix}/db-credentials"

  secret_values = {
    db_user = local.db_username
    db_pass = local.db_master_password
  }
}

# JWT signing key and per-service DB credentials, separate from db-credentials so the
# two rotate independently. Key names are copied verbatim into the 'catalogix-secrets'
# K8s Secret by helm/catalogix-hc/templates/external-secrets.yaml; rename with care.
module "app_secrets" {
  source = "../../modules/secrets-manager"

  secret_name = "${local.env_prefix}/app-secrets"

  # Single merged map: one Terraform resource owns the whole secret version.
  secret_values = merge(
    {
      # Machine secrets only. Human-chosen passwords live in "<env_prefix>/operator-credentials";
      # helm/catalogix-hc's ExternalSecret merges the two.
      jwt_secret = random_password.jwt.result
    },
    { for svc, creds in module.db_roles.credentials : "db_user_${replace(svc, "-", "_")}" => creds.username },
    { for svc, creds in module.db_roles.credentials : "db_password_${replace(svc, "-", "_")}" => creds.password }
  )
}

# Separate secret because Alertmanager runs in the `monitoring` namespace and K8s
# Secrets are namespace-scoped, so it needs its own ExternalSecret.
module "alerting_secrets" {
  source = "../../modules/secrets-manager"

  secret_name = "${local.env_prefix}/alerting-secrets"

  secret_values = {
    # Empty until var.smtp_password is supplied; no mail is sent until then.
    smtp_password = var.smtp_password
  }
}

# External Secrets Operator: syncs Secrets Manager secrets into K8s Secrets.
module "eso" {
  source = "../../modules/eso"

  cluster_name             = module.eks.cluster_name
  oidc_provider_arn        = module.eks.oidc_provider_arn
  oidc_provider            = trimprefix(module.eks.oidc_provider_arn, "arn:aws:iam::${data.aws_caller_identity.current.account_id}:oidc-provider/")
  region                   = var.aws_region
  permissions_boundary_arn = local.permissions_boundary_arn

  providers = {
    kubernetes = kubernetes.after_eks
    helm       = helm.after_eks
    kubectl    = kubectl.after_eks
  }

  # ESO needs a stable cluster, so it waits for nodes and the ALB controller.
  depends_on = [module.eks, module.alb, module.sg]

}


# gp3 StorageClass for Prometheus and Grafana. Lives in the root module because the
# kubernetes provider needs the cluster endpoint, which is unknown during a targeted
# apply of module.eks. WaitForFirstConsumer creates the EBS volume in the pod's AZ.
resource "kubernetes_storage_class_v1" "gp3" {
  provider = kubernetes.after_eks

  metadata {
    name = "gp3-sc"
    annotations = {
      # Not default, to avoid provisioning volumes for unrelated workloads
      "storageclass.kubernetes.io/is-default-class" = "false"
    }
  }

  storage_provisioner = "ebs.csi.aws.com"
  # Delete reclaim policy removes the EBS volume with the PVC, avoiding orphans and charges.
  reclaim_policy         = "Delete"
  volume_binding_mode    = "WaitForFirstConsumer"
  allow_volume_expansion = true

  parameters = {
    type = "gp3"
  }

  lifecycle {
    prevent_destroy = false
  }

  # module.eks covers the ebs_csi addon.
  depends_on = [
    module.eks,
    module.alb,
    module.sg,
    module.eso
  ]
}

# ALB Controller: required for Ingress resources to produce ALBs.
resource "helm_release" "alb_controller" {
  provider   = helm.after_eks
  name       = "aws-load-balancer-controller"
  namespace  = "kube-system"
  repository = "https://aws.github.io/eks-charts"
  chart      = "aws-load-balancer-controller"
  version    = "1.11.0"

  wait    = true
  timeout = 300 # Good practice so it doesn't hang forever if it fails

  values = [
    yamlencode({
      clusterName = module.eks.cluster_name
      region      = var.aws_region
      vpcId       = local.vpc_id
      serviceAccount = {
        create = true
        name   = "aws-load-balancer-controller"
        annotations = {
          "eks.amazonaws.com/role-arn" = module.alb.alb_role_arn
        }
      }
    })
  ]

  depends_on = [module.eks, module.alb]
}

# aws-auth ConfigMap: lets EKS worker nodes join the cluster. Applied through the
# kubectl provider (exec-based token, no local kubeconfig or CLI dependency).
resource "kubectl_manifest" "aws_auth" {
  provider = kubectl.after_eks

  # Heredoc matches the format aws-iam-authenticator expects. force_conflicts makes
  # Terraform win over manual kubectl edits; add extra mapRoles entries here.
  yaml_body = <<-YAML
apiVersion: v1
kind: ConfigMap
metadata:
  name: aws-auth
  namespace: kube-system
data:
  mapRoles: |
    - rolearn: ${module.eks.node_role_arn}
      username: system:node:{{EC2PrivateDNSName}}
      groups:
        - system:bootstrappers
        - system:nodes
YAML

  force_conflicts = true

  depends_on = [module.eks]
}
