# Read outputs from bootstrap-infra (VPC, subnets).
# bootstrap-infra must be applied first before running platform-infra.

data "terraform_remote_state" "bootstrap" {
  backend = "s3"
  config = {
    bucket = "catalogix-tfstate"
    key    = "bootstrap/terraform.tfstate"
    region = "ap-south-1"
  }
}

data "aws_caller_identity" "current" {}

resource "random_password" "db" {
  length = 24
  special = false # avoids JDBC URL encoding issues with special characters  
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

  # Env-specific prefix — change to "catalogix-staging" or "catalogix-prod" in other workspaces
  env_prefix = "${var.project_name}-dev"

  # Single source of truth for the DB username - referenced by RDS, Secrets Manager, and Helm
  db_username = "catalogix"
}

# Security Groups — all in the shared VPC from bootstrap
module "sg" {
  source   = "../../modules/security-groups"
  vpc_id   = local.vpc_id
  vpc_cidr = local.vpc_cidr
}

# EKS
module "eks" {
  source = "../../modules/eks"

  cluster_name    = local.env_prefix
  cluster_version = "1.32"
  private_subnets = local.private_subnets

  min_size     = 1
  max_size     = 2
  desired_size = 2
}

# ECR — global, no VPC dependency
module "ecr" {
  source       = "../../modules/ecr"
  repositories = ["frontend-svc", "user-svc", "product-svc"]
}

# ALB Controller (installs the AWS Load Balancer Controller into EKS)
module "alb" {
  source = "../../modules/alb"

  cluster_name      = module.eks.cluster_name
  vpc_id            = local.vpc_id
  region            = var.aws_region
  oidc_provider_arn = module.eks.oidc_provider_arn
  oidc_provider     = trimprefix(module.eks.oidc_provider_arn, "arn:aws:iam::${data.aws_caller_identity.current.account_id}:oidc-provider/")

  depends_on = [module.eks]
}

# RDS
module "rds" {
  source = "../../modules/rds"

  name              = "${local.env_prefix}-db"
  db_name           = "catalogix"
  username          = local.db_username
  password          = random_password.db.result
  private_subnets   = local.private_subnets
  security_group_id = module.sg.rds_sg
}

# Secrets Manager
# Stores the generated credentials so the ap can read them via ESO
# No one ever needs to know or handle the password except the app itself
module "secrets" {
  source = "../../modules/secrets-manager"

  # Namespaced name avoids collision if you add more envs (staging, prod).
  secret_name = "${local.env_prefix}/db-credentials"

  secret_values = {
    db_user = local.db_username
    db_pass = random_password.db.result
  }
}

# External Secrets Operator - syncs Secrets Manager secrets into K8s Secrets
# This replaces the manual process of creating K8s Secrets wth `kubectl create secrets` in the Jenkins pipeline
module "eso" {
  source = "../../modules/eso"

  
  
}