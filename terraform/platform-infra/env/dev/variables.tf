variable "aws_region" {
  description = "AWS region to deploy into"
  type        = string
  default     = "ap-south-1"
}

variable "project_name" {
  description = "Logical name of the project — used in Secrets Manager path"
  type        = string
}

# db_password has been intentionally removed. The DB password is now generated automatically by random_password.db in main.tf and stored in AWS Secrets Manager. No human input or pipeline credential is needed.