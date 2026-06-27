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

    Required — no default. Terraform previously generated the key pair
    itself (tls_private_key) and wrote the private key to disk, which means
    the private key material ended up in the Terraform state file in
    plaintext (state is read by every principal with s3:GetObject on the
    tfstate bucket, including the Jenkins role). Generating it yourself and
    handing Terraform only the public half keeps the private key off of
    every machine Terraform touches.

    Generate one with: ssh-keygen -t ed25519 -f ./catalogix-key -C "catalogix"
    then set public_key = file("./catalogix-key.pub") in terraform.tfvars.
  EOT
  type        = string
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