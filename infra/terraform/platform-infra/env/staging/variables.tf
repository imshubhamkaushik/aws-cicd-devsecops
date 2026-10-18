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
  description = "Deployment environment (dev/staging/prod)"
  type        = string
  default     = "staging"
}

variable "project_name" {
  description = "Project name prefix"
  type        = string
  default     = "catalogix"
}

# db_password is intentionally absent: random_password in main.tf generates it and
# stores it in Secrets Manager.

variable "smtp_password" {
  description = "Same as env/dev — see that file's comment. Left empty by default."
  type        = string
  default     = ""
  sensitive   = true
}
