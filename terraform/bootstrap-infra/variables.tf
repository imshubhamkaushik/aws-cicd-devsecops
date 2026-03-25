variable "name" {
  description = "Name of the EC2 instance"
  type        = string
  default     = "catalogix"
}

variable "ami" {
  description = "AMI ID for the EC2 instance"
  type        = string
}

variable "instance_type" {
  description = "EC2 instance type"
  type        = string
}

variable "vpc_name" {
  description = "Base name for VPC resources"
  type        = string
  default     = "catalogix-vpc"
}

variable "vpc_cidr" {
  description = "CIDR block for the VPC"
  type        = string
}

variable "admin_cidr" {
  description = "Your IP in CIDR notation for SSH/UI access"
  type        = string
  validation {
    condition     = var.admin_cidr != "0.0.0.0/0" && can(cidrhost(var.admin_cidr, 0))
    error_message = "admin_cidr must be your specific IP in CIDR notation (e.g. 203.0.113.42/32). Never use 0.0.0.0/0."
  }
}

variable "key_name" {
  description = "Name of the EC2 key pair to use for SSH access"
  type        = string
}

variable "project_tag" {
  description = "Project tag for the EC2 instance and associated resources"
  type        = string
  default     = "Catalogix"
}

variable "azs" {
  description = "List of availability zones to use for the subnets"
  type        = list(string)
}

variable "public_subnets" {
  description = "List of CIDR blocks for the public subnets"
  type        = list(string)
}

variable "private_subnets" {
  description = "List of CIDR blocks for the private subnets"
  type        = list(string)
}

variable "cluster_name" {
  description = "Name of the EKS cluster"
  type        = string
}

variable "aws_region" {
  description = "AWS region"
  type        = string
  default     = "ap-south-1"
}