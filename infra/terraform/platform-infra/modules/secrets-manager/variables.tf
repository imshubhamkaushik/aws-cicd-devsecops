variable "secret_name" {
  description = "Secret Name of the DB Credentials in Secrets Manager"
  type        = string
}

variable "project_name" {
  description = "Logical name of the project"
  type        = string
  default     = "catalogix"
}

variable "secret_values" {
  description = "Secret Values of the DB Credentials"
  type        = map(string)
  sensitive   = true
}