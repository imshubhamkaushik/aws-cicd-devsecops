variable "aws_region" {
  description = "AWS region to deploy into"
  type        = string
  default     = "ap-south-1"
}

variable "cluster_name" {
  description = "Logical name of the cluster — used as a prefix for all resources and tags"
  type        = string
  default     = "catalogix-cluster"
}

variable "environment" {
  description = "Deployment environment (dev/staging/prod) — used as a prefix for all resources and tags"
  type        = string
  default     = "dev"
}

variable "project_name" {
  description = "Project name prefix"
  type        = string
  default     = "catalogix"
  
}
# db_password has been intentionally removed. The DB password is now generated automatically by random_password.db in main.tf and stored in AWS Secrets Manager. No human input or pipeline credential is needed.