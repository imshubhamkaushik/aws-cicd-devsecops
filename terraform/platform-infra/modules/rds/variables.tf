variable "name" {
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
    sensitive = true
}
variable "private_subnets" {
    description = "List of private subnet IDs for the RDS instance"
    type        = list(string)
}

variable "security_group_id" {
    description = "ID of the security group for the RDS instance"
    type        = string
}