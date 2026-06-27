variable "project_name" {
  description = "Base name for all resources"
  type        = string
}

variable "db_name" {
  description = "Name of the database to create"
  type        = string
}

variable "username" {
  description = "Master username for the database"
  type        = string
}

variable "password" {
  description = "Master password for the database"
  type        = string
  sensitive   = true
}

variable "private_subnets" {
  description = "List of private subnet IDs for the RDS instance"
  type        = list(string)
}

variable "security_group_id" {
  description = "ID of the security group for the RDS instance"
  type        = string
}

variable "ssm_parameter_path" {
  description = "SSM Parameter Store path for the RDS endpoint"
  type        = string
}

variable "backup_retention_period" {
  description = <<-EOT
    Days to retain automated backups (0-35).
    Set to 0 to disable — required on AWS free-tier accounts.
    No default: every environment must set this explicitly.
    dev/test: 0  |  staging: 3  |  production: 7+
  EOT
  type        = number
}

variable "db_engine_version" {
  description = "Version of the database engine to use"
  type        = string
}

variable "multi_az" {
  description = <<-EOT
    Whether to provision a synchronous standby in a second AZ for automatic
    failover. Roughly doubles RDS cost for this instance.
    No default: every environment must decide explicitly.
    dev/test: false  |  staging: false (unless testing failover itself)  |  production: true
  EOT
  type        = bool
}