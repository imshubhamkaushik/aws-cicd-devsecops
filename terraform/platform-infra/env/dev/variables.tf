variable "aws_region" {
  description = "AWS region to deploy into"
  type        = string
  default     = "ap-south-1"
}

variable "db_password" {
  description = "Master password for the RDS Postgres instance"
  type        = string
  sensitive   = true
}

variable "project_name" {
  description = "Logical name of the project — used in Secrets Manager path"
  type        = string
}