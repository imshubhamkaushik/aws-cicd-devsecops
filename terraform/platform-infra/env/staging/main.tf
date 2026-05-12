# Read outputs from bootstrap-infra (VPC, subnets).
# bootstrap-infra must be applied first before running platform-infra.
#
# Staging shares the same VPC as dev (single bootstrap-infra layer).
# Cluster and RDS resources are fully isolated by separate module instances
# and name-prefixed with "catalogix-staging".

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

  # keepers tie the password lifecycle to the RDS instance name.
  # Without keepers, a terraform state refresh or re-import silently regenerates the password, rotating the secret and breaking the running app.
  # Password only changes if the RDS name changes — which is always intentional.
  keepers = {
    rds_name = "${local.env_prefix}-db"
  }
}

resource "aws_kms_key" "eks" {
  description             = "EKS secrets encryption key — staging"
  deletion_window_in_days = 7
}

locals {
  # Pulled from remote state so every module uses the same source of truth
  vpc_id          = data.terraform_remote_state.bootstrap.outputs.vpc_id
  vpc_cidr        = data.terraform_remote_state.bootstrap.outputs.vpc_cidr
  private_subnets = data.terraform_remote_state.bootstrap.outputs.private_subnets
  public_subnets  = data.terraform_remote_state.bootstrap.outputs.public_subnets

  # Pulled from EKS module output — used in providers.tf for kubernetes/helm
  cluster_name        = module.eks.cluster_name
  cluster_endpoint    = module.eks.cluster_endpoint
  cluster_certificate = module.eks.cluster_certificate

  # Change to "catalogix-prod" in the prod env directory
  env_prefix = "${var.cluster_name}-${var.environment}"

  # Single source of truth for the DB username - referenced by RDS, Secrets Manager, and Helm
  db_username = "catalogix"
}

# Security Groups
module "sg" {
  source   = "../../modules/security-groups"
  vpc_id   = local.vpc_id
  vpc_cidr = local.vpc_cidr

  jenkins_sg_id     = data.terraform_remote_state.bootstrap.outputs.jenkins_sg_id
  eks_cluster_sg_id = module.eks.cluster_sg_id
}

# EKS
# Staging uses slightly larger nodes (t3.medium - only in paid AWS tier // c7i-flex.large - only in free tier) and allows scaling to 3
# to validate the app under realistic load before production.
module "eks" {
  source = "../../modules/eks"

  cluster_name    = local.env_prefix
  cluster_version = "1.32"
  private_subnets = local.private_subnets

  # Staging: larger instance type + higher max to simulate prod-like load
  min_size     = 1
  max_size     = 3
  desired_size = 2

  kms_key_arn = aws_kms_key.eks.arn

  jenkins_role_arn  = data.terraform_remote_state.bootstrap.outputs.jenkins_role_arn
  jenkins_public_ip = data.terraform_remote_state.bootstrap.outputs.public_ip_jenkins

  console_iam_arn = data.aws_caller_identity.current.arn
}

data "aws_eks_cluster" "this" {
  name       = module.eks.cluster_name
  depends_on = [module.eks]
}

data "aws_eks_cluster_auth" "this" {
  name       = module.eks.cluster_name
  depends_on = [module.eks]
}

# ECR is shared between dev and staging — same images, different Helm releases.
# No separate ECR module needed for staging.

# ALB Controller
module "alb" {
  source = "../../modules/alb"

  cluster_name      = module.eks.cluster_name
  vpc_id            = local.vpc_id
  region            = var.aws_region
  oidc_provider_arn = module.eks.oidc_provider_arn
  oidc_provider     = trimprefix(module.eks.oidc_provider_arn, "arn:aws:iam::${data.aws_caller_identity.current.account_id}:oidc-provider/")

  providers = {
    kubernetes = kubernetes.after_eks
    helm       = helm.after_eks
  }

  depends_on = [module.eks, module.sg]
}

# RDS
# Staging uses the same instance class as dev (db.t4g.micro) to keep cost down.
# backup_retention_period = 1 unlike dev (0) — staging should catch data-loss bugs.
module "rds" {
  source = "../../modules/rds"

  name                    = "${local.env_prefix}-db"
  db_name                 = "catalogix"
  username                = local.db_username
  password                = random_password.db.result
  private_subnets         = local.private_subnets
  security_group_id       = module.sg.rds_sg
  backup_retention_period = 1 # 1-day backup window — unlike dev (0)

  ssm_parameter_path = "/${local.env_prefix}/rds-endpoint"
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

# External Secrets Operator
module "eso" {
  source = "../../modules/eso"

  cluster_name      = module.eks.cluster_name
  oidc_provider_arn = module.eks.oidc_provider_arn
  oidc_provider     = trimprefix(module.eks.oidc_provider_arn, "arn:aws:iam::${data.aws_caller_identity.current.account_id}:oidc-provider/")
  region            = var.aws_region

  providers = {
    kubernetes = kubernetes.after_eks
    helm       = helm.after_eks
  }

  depends_on = [module.eks, module.alb, module.sg]
}

# gp3 StorageClass — same reasoning as dev env.
# Kept in root module (not inside module.eks) so the kubernetes provider
# resolves only after the cluster endpoint is known.
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
