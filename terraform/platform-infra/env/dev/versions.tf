terraform {
  backend "s3" {
    bucket       = "catalogix-tfstate"
    key          = "platform/dev/terraform.tfstate"
    region       = "ap-south-1"
    encrypt      = true
    use_lockfile = true
  }

  required_version = ">= 1.12.0"

  required_providers {
    aws        = { source = "hashicorp/aws", version = "~> 6.0" }
    kubernetes = { source = "hashicorp/kubernetes", version = "~> 3.0" }
    helm       = { source = "hashicorp/helm", version = "~> 3.0" }
    tls        = { source = "hashicorp/tls", version = "~> 4.0" }
  }
}