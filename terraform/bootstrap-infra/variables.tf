variable "project_name" {
  description = "Name of the project — used as a prefix for all resources and tags"
  type        = string
  default     = "catalogix"
}

variable "ami" {
  description = "AMI ID for the EC2 instance"
  type        = string
  default     = "ami-07216ac99dc46a187" // Ubuntu 22.04 LTS in ap-south-1
}

variable "instance_type" {
  description = "EC2 instance type"
  type        = string
  default     = "c7i-flex.large"
}

variable "vpc_name" {
  description = "Base name for VPC resources"
  type        = string
  default     = "catalogix-vpc"
}

variable "vpc_cidr" {
  description = "CIDR block for the VPC"
  type        = string
  default     = "10.0.0.0/16"
}

variable "key_name" {
  description = "Name of the EC2 key pair to use for SSH access"
  type        = string
  default     = "catalogix-key"
}

variable "public_key" {
  description = <<-EOT
    SSH public key material (e.g. contents of ~/.ssh/id_ed25519.pub),
    generated OUTSIDE Terraform on your own machine.

    Required — no default. Provide only the public half so the private key
    stays outside Terraform state and CI infrastructure.

    Generate one with scripts/bash/create-key-pair.sh, then either:
      - export TF_VAR_public_key="$(cat ~/.ssh/catalogix-key.pub)", or
      - paste the literal key text into terraform.tfvars (tfvars files cannot
        call functions such as file()), or
      - do nothing: scripts/python/bootstrap_infra.py reads
        ~/.ssh/catalogix-key.pub automatically when neither is set.
  EOT
  type        = string
}

variable "allowed_cidrs" {
  description = <<-EOT
    Operator IP ranges allowed to reach Jenkins (SSH 22, UI 8080) and the EKS
    public API. Normally NOT set by hand: scripts/python/allowlist.py keeps the
    list in the state bucket (access/allowed-cidrs.json), adds your current IP
    when it is new, and writes it to allowed_cidrs.auto.tfvars.json. Entries are
    only ever added there until you prune them or destroy the deployment.

    If left empty (for example when you run terraform by hand), the IP detected
    at apply time is used instead, which REPLACES any earlier entries.
  EOT
  type        = list(string)
  default     = []

  validation {
    condition     = alltrue([for c in var.allowed_cidrs : can(cidrhost(c, 0))])
    error_message = "Every allowed_cidrs entry must be a valid CIDR, for example 203.0.113.5/32."
  }

  validation {
    condition     = !contains(var.allowed_cidrs, "0.0.0.0/0")
    error_message = "0.0.0.0/0 would open Jenkins and SSH to the whole internet and is not accepted."
  }
}

variable "project_tag" {
  description = "Project tag for the EC2 instance and associated resources"
  type        = string
  default     = "catalogix"
}

variable "azs" {
  description = "List of availability zones to use for the subnets"
  type        = list(string)
  default     = ["ap-south-1a", "ap-south-1b"]
}

variable "public_subnets" {
  description = "List of CIDR blocks for the public subnets"
  type        = list(string)
  default     = ["10.0.1.0/24", "10.0.2.0/24"]
}

variable "private_subnets" {
  description = "List of CIDR blocks for the private subnets"
  type        = list(string)
  default     = ["10.0.3.0/24", "10.0.4.0/24"]
}

variable "cluster_name" {
  description = "Name of the EKS cluster"
  type        = string
  default     = "catalogix-cluster"
}

variable "environment" {
  description = "Environment name (e.g., dev, staging, prod)"
  type        = string
  default     = "dev"
}

variable "aws_region" {
  description = "AWS region"
  type        = string
  default     = "ap-south-1"
}

variable "nat_gateway_count" {
  description = <<-EOT
    Number of NAT Gateways to create, one per AZ up to this count.
    Default 1 = single NAT Gateway (cost-effective, dev default — ~$32/mo).
    Set to length(var.azs) for one NAT Gateway per AZ (production pattern —
    survives a single-AZ outage without losing outbound connectivity in
    other AZs). Must be <= length(var.azs).
  EOT
  type        = number
  default     = 1

  validation {
    condition     = var.nat_gateway_count >= 1
    error_message = "nat_gateway_count must be at least 1."
  }
}