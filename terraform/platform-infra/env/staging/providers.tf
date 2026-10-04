terraform {
  backend "s3" {
    bucket       = "catalogix-tfstate"
    key          = "platform-infra/staging/terraform.tfstate"
    region       = "ap-south-1"
    encrypt      = true
    use_lockfile = true
  }

  required_version = ">= 1.12.0"

  required_providers {
    aws        = { source = "hashicorp/aws", version = "~> 6.0" }
    kubernetes = { source = "hashicorp/kubernetes", version = "~> 3.0" }
    helm       = { source = "hashicorp/helm", version = "~> 3.0" }
    kubectl    = { source = "alekc/kubectl", version = "~> 2.0" }
    tls        = { source = "hashicorp/tls", version = "~> 4.0" }
    random     = { source = "hashicorp/random", version = "~> 3.0" }
    postgresql = { source = "cyrilgdn/postgresql", version = "~> 1.25" }
  }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = "Catalogix"
      ManagedBy   = "Terraform"
      Environment = "staging"
    }
  }
}

# Kubernetes and Helm providers are aliased so they only resolve
# after module.eks is created and the cluster endpoint is known.
provider "kubernetes" {
  alias                  = "after_eks"
  host                   = data.aws_eks_cluster.this.endpoint
  cluster_ca_certificate = base64decode(data.aws_eks_cluster.this.certificate_authority[0].data)

  exec {
    api_version = "client.authentication.k8s.io/v1beta1"
    command     = "aws"
    args        = ["eks", "get-token", "--cluster-name", data.aws_eks_cluster.this.name]
  }
}

provider "helm" {
  alias = "after_eks"

  kubernetes = {
    host                   = data.aws_eks_cluster.this.endpoint
    cluster_ca_certificate = base64decode(data.aws_eks_cluster.this.certificate_authority[0].data)

    exec = {
      api_version = "client.authentication.k8s.io/v1beta1"
      command     = "aws"
      args        = ["eks", "get-token", "--cluster-name", data.aws_eks_cluster.this.name]
    }
  }
}

# kubectl provider alias used by module.eso and the aws_auth ConfigMap.
provider "kubectl" {
  alias                  = "after_eks"
  host                   = module.eks.cluster_endpoint
  cluster_ca_certificate = base64decode(data.aws_eks_cluster.this.certificate_authority[0].data)
  load_config_file       = false # never reads ~/.kube/config — fully self-contained

  exec {
    api_version = "client.authentication.k8s.io/v1beta1"
    command     = "aws"
    args = [
      "eks", "get-token",
      "--cluster-name", data.aws_eks_cluster.this.name,
      "--region", var.aws_region
    ]
  }
}

# Connects as the RDS master user to create per-service databases + roles. Jenkins' SG
# already reaches RDS on 5432 (rds_ingress_jenkins).
provider "postgresql" {
  host            = module.rds.rds_address
  port            = 5432
  username        = local.db_username
  password        = random_password.db.result
  superuser       = false
  connect_timeout = 15
  sslmode         = "require"
}
