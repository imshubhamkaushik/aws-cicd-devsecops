# Read outputs from bootstrap-infra (VPC, subnets).
# bootstrap-infra must be applied first before running platform-infra.

data "terraform_remote_state" "bootstrap" {
  backend = "s3"
  config = {
    bucket = "catalogix-tfstate"
    key    = "bootstrap-infra/terraform.tfstate"
    region = "ap-south-1"
  }
}

data "aws_caller_identity" "current" {}

data "aws_iam_session_context" "current" {
  arn = data.aws_caller_identity.current.arn
}

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
  description             = "EKS secrets encryption key - dev"
  deletion_window_in_days = 7
  # enable_key_rotation is a best practice for long-lived keys, but not required for this demo since the key is only used to encrypt EKS secrets and doesn't have any direct human access or permissions attached to it. Enabling rotation adds complexity by creating new key versions every year, which would require updating the eks module with the new key ARN to avoid breaking changes.
  enable_key_rotation = true
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
  env_prefix = "${var.cluster_name}-${var.environment}"

  # Single source of truth for the DB username - referenced by RDS, Secrets Manager, and Helm
  db_username = "catalogix"
}

# Security Groups — all in the shared VPC from bootstrap
module "sg" {
  source       = "../../modules/security-groups"
  project_name = local.env_prefix
  vpc_id       = local.vpc_id
  vpc_cidr     = local.vpc_cidr

  jenkins_sg_id     = data.terraform_remote_state.bootstrap.outputs.jenkins_sg_id
  eks_cluster_sg_id = module.eks.cluster_sg_id # implicit depends_on module.eks
}

# EKS
module "eks" {
  source = "../../modules/eks"

  cluster_name    = local.env_prefix
  cluster_version = "1.35"
  private_subnets = local.private_subnets

  min_size     = 1
  max_size     = 2
  desired_size = 2

  # KMS key for EKS secrets
  kms_key_arn = aws_kms_key.eks.arn

  jenkins_role_arn  = data.terraform_remote_state.bootstrap.outputs.jenkins_role_arn
  jenkins_public_ip = data.terraform_remote_state.bootstrap.outputs.public_ip_jenkins

  # Whoever runs terraform apply automatically gets console access.
  # No variable or tfvars entry needed.
  console_iam_arn = data.aws_iam_session_context.current.issuer_arn
}

# EKS DATA (safe, resolves after creation)
data "aws_eks_cluster" "this" {
  name = module.eks.cluster_name

  depends_on = [module.eks]
}

data "aws_eks_cluster_auth" "this" {
  name = module.eks.cluster_name

  depends_on = [module.eks]
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

  depends_on = [module.eks, module.sg]
}

# RDS
module "rds" {
  source = "../../modules/rds"

  project_name            = "${local.env_prefix}-db"
  db_name                 = "catalogix"
  username                = local.db_username
  password                = random_password.db.result
  private_subnets         = local.private_subnets
  security_group_id       = module.sg.rds_sg
  backup_retention_period = 0

  ssm_parameter_path = "/${local.env_prefix}/rds-endpoint"
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

  cluster_name      = module.eks.cluster_name
  oidc_provider_arn = module.eks.oidc_provider_arn
  oidc_provider     = trimprefix(module.eks.oidc_provider_arn, "arn:aws:iam::${data.aws_caller_identity.current.account_id}:oidc-provider/")
  region            = var.aws_region

  providers = {
    kubernetes = kubernetes.after_eks
    helm       = helm.after_eks
  }

  # ESO must come after EKS nodes and ALB controller so the cluster is stable
  depends_on = [module.eks, module.alb, module.sg]

}

# gp3 StorageClass — moved here from modules/eks/main.tf.
#
# This resource uses the kubernetes provider. Keeping it inside module.eks caused it to be included in the targeted apply (-target=module.eks),
# where the kubernetes provider resolves to localhost:80 because local.cluster_endpoint was "(known after apply)" at plan time.
# Placing it in the root module ensures it runs only in (full apply), when module.eks is in state, the endpoint is known, and the provider connects to the real cluster.
#
# WaitForFirstConsumer ensures the EBS volume is created in the same AZ as
# the pod that claims it — required for single-AZ deployments.
resource "kubernetes_storage_class_v1" "gp3" {
  provider = kubernetes.after_eks

  metadata {
    name = "gp3-sc"
    annotations = {
      # Not set as default to avoid silently provisioning volumes for other workloads
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

  # depends_on updated from aws_eks_addon.ebs_csi (module-internal ref)
  # to module.eks — the root-level handle for the entire EKS module,
  # which includes the ebs_csi addon internally.
  depends_on = [
    module.eks,
    module.alb,
    module.sg,
    module.eso
  ]
}

resource "helm_release" "alb_controller" {
  provider   = helm.after_eks
  name       = "aws-load-balancer-controller"
  namespace  = "kube-system"
  repository = "https://aws.github.io/eks-charts"
  chart      = "aws-load-balancer-controller"
  version    = "1.11.0"

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
