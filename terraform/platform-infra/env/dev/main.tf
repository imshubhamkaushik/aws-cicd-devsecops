# This is the root module for the "platform-infra" Terraform project, which provisions the shared infrastructure components for the EKS cluster and its dependencies.

# terraform_remote_state - Read outputs from bootstrap-infra (VPC, subnets).
# bootstrap-infra must be applied first before running platform-infra.
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

# random_password - Generates a random password for the RDS instance, stored in Secrets Manager and synced to K8s via ESO.
resource "random_password" "db" {
  length  = 16
  special = false # avoids JDBC URL encoding issues with special characters 

  # keepers tie the password lifecycle to the RDS instance name.
  # Without keepers, a terraform state refresh or re-import silently regenerates the password, rotating the secret and breaking the running app.
  # Password only changes if the RDS name changes — which is always intentional.
  keepers = {
    rds_name = "${local.env_prefix}-db"
  }
}

# KMS key for EKS secrets encryption. Using a customer-managed key is a best practice for production workloads, but not strictly required for this demo since the default AWS-managed key would work fine for encrypting EKS secrets.
resource "aws_kms_key" "eks" {
  description             = "EKS secrets encryption key - dev environment"
  deletion_window_in_days = 7
  # enable_key_rotation is a best practice for long-lived keys, but not required for this demo since the key is only used to encrypt EKS secrets and doesn't have any direct human access or permissions attached to it. Enabling rotation adds complexity by creating new key versions every year, which would require updating the eks module with the new key ARN to avoid breaking changes.
  enable_key_rotation = true
}

# locals - centralize commonly used values and outputs from other modules to avoid duplication and ensure consistency across the configuration. This is a best practice for larger Terraform projects to improve maintainability and reduce the risk of errors from hardcoding values or referencing outputs directly in multiple places.
locals {
  # Pulled from remote state so every module uses the same source of truth
  vpc_id          = data.terraform_remote_state.bootstrap.outputs.vpc_id
  private_subnets = data.terraform_remote_state.bootstrap.outputs.private_subnets
  public_subnets  = data.terraform_remote_state.bootstrap.outputs.public_subnets

  # Pulled from EKS module output — used in providers.tf for kubernetes/helm
  cluster_name        = module.eks.cluster_name
  cluster_endpoint    = module.eks.cluster_endpoint
  cluster_certificate = module.eks.cluster_certificate

  # Required on every IAM role created below — see bootstrap-infra/iam.tf
  # for why, and modules/eks/variables.tf for the per-module rationale.
  permissions_boundary_arn = data.terraform_remote_state.bootstrap.outputs.jenkins_boundary_arn

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
  vpc_cidr     = data.terraform_remote_state.bootstrap.outputs.vpc_cidr

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
  max_size     = 4
  desired_size = 2

  # KMS key for EKS secrets
  kms_key_arn = aws_kms_key.eks.arn

  jenkins_role_arn  = data.terraform_remote_state.bootstrap.outputs.jenkins_role_arn
  jenkins_public_ip = data.terraform_remote_state.bootstrap.outputs.public_ip_jenkins

  # my_ip_cidr comes from bootstrap-infra's remote state, captured
  # once at bootstrap-infra apply time, instead of this module independently
  # querying checkip.amazonaws.com again at a potentially much later time.
  # See modules/eks/main.tf for the full rationale.
  my_ip_cidr = data.terraform_remote_state.bootstrap.outputs.jenkins_my_ip_cidr

  # Whoever runs terraform apply automatically gets console access.
  # No variable or tfvars entry needed.
  console_iam_arn = data.aws_iam_session_context.current.issuer_arn

  # permissions_boundary_arn is required on every IAM role created in this module.
  permissions_boundary_arn = local.permissions_boundary_arn
}

# EKS DATA (safe, resolves after creation)
data "aws_eks_cluster" "this" {
  name = module.eks.cluster_name

  depends_on = [module.eks]
}

# # EKS AUTH (safe, resolves after creation)
# data "aws_eks_cluster_auth" "this" {
#   name = module.eks.cluster_name

#   depends_on = [module.eks]
# }

# ECR — global, no VPC dependency
module "ecr" {
  source       = "../../modules/ecr"
  repositories = ["frontend-svc", "user-svc", "product-svc"]
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

# WAF — creates the Web ACL and publishes its ARN to SSM. The ALB itself is
# created later by the AWS Load Balancer Controller (via the Ingress in
# helm/catalogix-hc), which is what actually performs the association using
# the wafv2-acl-arn annotation. See modules/waf/main.tf for the full reasoning.
module "waf" {
  source = "../../modules/waf"

  name               = local.env_prefix
  region             = var.aws_region
  ssm_parameter_path = "/${local.env_prefix}/waf-acl-arn"

  depends_on = [module.alb]
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
  db_engine_version       = "18.1"
  backup_retention_period = 0
  multi_az                = false

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

  # ESO must come after EKS nodes and ALB controller so the cluster is stable
  depends_on = [module.eks, module.alb, module.sg]

}

# gp3 StorageClass — used by Prometheus and Grafana — moved here from modules/eks/main.tf.
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

  storage_provisioner = "ebs.csi.aws.com"
  # reclaim_policy = Delete ensures the EBS volume is deleted when the PVC is deleted, avoiding orphaned volumes and unexpected AWS charges.
  reclaim_policy         = "Delete"
  volume_binding_mode    = "WaitForFirstConsumer"
  allow_volume_expansion = true

  parameters = {
    type = "gp3"
  }

  lifecycle {
    prevent_destroy = false
  }

  # depends_on updated from aws_eks_addon.ebs_csi (module-internal ref)
  # to module.eks — the root-level handle for the entire EKS module, which includes the ebs_csi addon internally.
  depends_on = [
    module.eks,
    module.alb,
    module.sg,
    module.eso
  ]
}

# ALB Controller - Helm release to install the AWS Load Balancer Controller into the EKS cluster. This is required for the Ingress resources in the app to work, and is a common component in EKS clusters that use ALB for ingress.
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

# aws-auth ConfigMap — allows EKS worker nodes to join the cluster.
#
# Moved here from modules/eks/main.tf (was terraform_data + local-exec).
# Reason: local-exec required kubectl and the aws CLI to be installed on
# whichever machine runs terraform apply — a hidden runtime dependency that
# broke on clean CI runners and the Jenkins EC2 after a fresh Ansible run.
#
# The alecks/kubectl provider (alias = after_eks) is already configured in providers.tf and already used for the ClusterSecretStore in the ESO module.
# It authenticates via "aws eks get-token" exec block, so no local kubeconfig file is needed (load_config_file = false). 
# This is consistent with how every other Kubernetes resource is managed in this root module.
#
# replace_on_change = [module.eks.node_role_arn, module.eks.cluster_name]
# mirrors the triggers_replace on the old terraform_data: if the node role or cluster is replaced, the ConfigMap is re-applied automatically.
resource "kubectl_manifest" "aws_auth" {
  provider = kubectl.after_eks

  # Heredoc keeps the format identical to what aws-iam-authenticator expects.
  # Double-yamlencode (outer manifest + inner mapRoles) introduces key-ordering and quoting edge cases with yamlencode — this is simpler and explicit.
  #
  # force_conflicts = true: if someone runs `kubectl apply` on this ConfigMap manually (e.g. to add a mapUsers entry), Terraform wins the field-manager conflict on the next apply and restores the desired state. 
  # This is intentional — additional mapRoles entries (e.g. for Fargate profiles or cluster access entries) should be added here, not outside Terraform.
  #
  # Drift detection: if the ConfigMap is deleted or corrupted, Terraform detects the diff via normal state comparison and re-applies on the next apply.
  # No triggers_replace needed — kubectl_manifest tracks real resource state, unlike terraform_data + local-exec which tracked nothing.
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
