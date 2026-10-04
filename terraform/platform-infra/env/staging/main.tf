# Staging root module. Reads VPC outputs from bootstrap-infra (apply it first) and
# shares dev's VPC; cluster and RDS are isolated and prefixed "catalogix-staging".

data "terraform_remote_state" "bootstrap" {
  backend = "s3"
  config = {
    bucket = "catalogix-tfstate"
    key    = "bootstrap-infra/terraform.tfstate"
    region = "ap-south-1"
  }
}

data "aws_caller_identity" "current" {}

resource "random_password" "db" {
  length  = 24
  special = false # avoids JDBC URL encoding issues with special characters

  # keepers tie the password to the RDS name so a refresh or re-import cannot rotate it.
  keepers = {
    rds_name = "${local.env_prefix}-db"
  }
}

resource "random_password" "jwt" {
  length  = 48
  special = false

  keepers = {
    cluster_name = local.env_prefix
  }
}

resource "random_password" "rabbitmq" {
  length  = 24
  special = false

  keepers = {
    cluster_name = local.env_prefix
  }
}

resource "aws_kms_key" "eks" {
  description             = "EKS secrets encryption key — staging"
  deletion_window_in_days = 7
}

locals {
  # From remote state, shared by every module
  vpc_id          = data.terraform_remote_state.bootstrap.outputs.vpc_id
  vpc_cidr        = data.terraform_remote_state.bootstrap.outputs.vpc_cidr
  private_subnets = data.terraform_remote_state.bootstrap.outputs.private_subnets
  public_subnets  = data.terraform_remote_state.bootstrap.outputs.public_subnets

  # From the EKS module; used by the kubernetes/helm providers
  cluster_name        = module.eks.cluster_name
  cluster_endpoint    = module.eks.cluster_endpoint
  cluster_certificate = module.eks.cluster_certificate

  # Change to "catalogix-prod" in a prod env
  env_prefix = "${var.cluster_name}-${var.environment}"

  # Single source of truth for the DB username
  db_username = "catalogix"

  # Required on every IAM role created below; see bootstrap-infra/iam.tf.
  permissions_boundary_arn = data.terraform_remote_state.bootstrap.outputs.jenkins_boundary_arn
}

# Security Groups
module "sg" {
  source       = "../../modules/security-groups"
  project_name = local.env_prefix
  vpc_id       = local.vpc_id
  vpc_cidr     = local.vpc_cidr

  jenkins_sg_id     = data.terraform_remote_state.bootstrap.outputs.jenkins_sg_id
  eks_cluster_sg_id = module.eks.cluster_sg_id
}

# EKS
# Staging uses larger nodes (t3.medium on paid tier, c7i-flex.large on free tier) and scales to 5.
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

data "aws_eks_cluster" "this" {
  name       = module.eks.cluster_name
  depends_on = [module.eks]
}

data "aws_eks_cluster_auth" "this" {
  name       = module.eks.cluster_name
  depends_on = [module.eks]
}

# ECR is shared with dev; no separate module needed.

# ALB 
module "alb" {
  source = "../../modules/alb"

  cluster_name             = module.eks.cluster_name
  oidc_provider_arn        = module.eks.oidc_provider_arn
  oidc_provider            = trimprefix(module.eks.oidc_provider_arn, "arn:aws:iam::${data.aws_caller_identity.current.account_id}:oidc-provider/")
  permissions_boundary_arn = local.permissions_boundary_arn

  depends_on = [module.eks, module.sg]
}

# WAF ACL for the ALB Ingress (helm/catalogix-hc/templates/ingress-alb.yaml).
module "waf" {
  source = "../../modules/waf"

  name               = local.env_prefix
  region             = var.aws_region
  ssm_parameter_path = "/${local.env_prefix}/waf-acl-arn"

  depends_on = [module.alb]
}

# RDS
# Same instance class as dev (db.t4g.micro) to keep cost down.
module "rds" {
  source = "../../modules/rds"

  project_name = "${local.env_prefix}-db"
  # Initial database required by RDS; unused. Per-service databases come from
  # module.db_roles.
  db_name                 = "catalogix-admin"
  username                = local.db_username
  password                = random_password.db.result
  private_subnets         = local.private_subnets
  security_group_id       = module.sg.rds_sg
  db_engine_version       = "18.1"
  backup_retention_period = 0
  multi_az                = false
  skip_final_snapshot     = true

  ssm_parameter_path = "/${local.env_prefix}/rds-endpoint"
}

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

  depends_on = [module.rds]
}

# Secrets Manager
module "secrets" {
  source = "../../modules/secrets-manager"

  secret_name = "${local.env_prefix}/db-credentials"

  secret_values = {
    db_user = local.db_username
    db_pass = random_password.db.result
  }
}

module "app_secrets" {
  source = "../../modules/secrets-manager"

  secret_name = "${local.env_prefix}/app-secrets"

  secret_values = merge(
    {
      jwt_secret        = random_password.jwt.result
      rabbitmq_user     = "catalogix"
      rabbitmq_password = random_password.rabbitmq.result
    },
    { for svc, creds in module.db_roles.credentials : "db_user_${replace(svc, "-", "_")}" => creds.username },
    { for svc, creds in module.db_roles.credentials : "db_password_${replace(svc, "-", "_")}" => creds.password }
  )
}

# Separate secret: Alertmanager runs in the monitoring namespace, and K8s Secrets are namespace-scoped.
module "alerting_secrets" {
  source = "../../modules/secrets-manager"

  secret_name = "${local.env_prefix}/alerting-secrets"

  secret_values = {
    smtp_password = var.smtp_password
  }
}

# External Secrets Operator
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

  depends_on = [module.eks, module.alb, module.sg]
}

# gp3 StorageClass, in the root module so the kubernetes provider resolves only once the cluster endpoint is known.
resource "kubernetes_storage_class_v1" "gp3" {
  provider = kubernetes.after_eks

  metadata {
    name = "gp3-sc"
    annotations = {
      "storageclass.kubernetes.io/is-default-class" = "false"
    }
  }

  storage_provisioner    = "ebs.csi.aws.com"
  reclaim_policy         = "Retain"
  volume_binding_mode    = "WaitForFirstConsumer"
  allow_volume_expansion = true

  parameters = {
    type = "gp3"
  }

  lifecycle {
    prevent_destroy = false
  }

  depends_on = [module.eks, module.alb, module.sg]
}

# AWS Load Balancer Controller; without it the Ingress is never reconciled into an ALB.
resource "helm_release" "alb_controller" {
  provider   = helm.after_eks
  name       = "aws-load-balancer-controller"
  namespace  = "kube-system"
  repository = "https://aws.github.io/eks-charts"
  chart      = "aws-load-balancer-controller"
  version    = "1.11.0"

  wait    = true
  timeout = 300

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

# aws-auth ConfigMap; without it worker nodes cannot join the cluster.
resource "kubectl_manifest" "aws_auth" {
  provider = kubectl.after_eks

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
