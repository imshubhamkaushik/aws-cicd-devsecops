variable "aws_region" {
  description = "The AWS region where the S3 bucket for Terraform state will be created."
  type        = string
  default     = "ap-south-1"
}

variable "project_tag" {
  description = "The project name to be used in tags for AWS resources."
  type        = string
  default     = "catalogix"
}