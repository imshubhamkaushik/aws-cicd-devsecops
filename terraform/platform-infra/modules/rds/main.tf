resource "aws_db_subnet_group" "this" {
  name       = "${var.project_name}-subnet-group"
  subnet_ids = var.private_subnets
}

resource "aws_db_instance" "postgres" {
  identifier = var.project_name

  engine         = "postgres"
  engine_version = "17.2"
  instance_class = "db.t4g.micro"

  allocated_storage     = 20
  max_allocated_storage = 100
  storage_encrypted     = true

  db_name  = var.db_name
  username = var.username
  password = var.password

  db_subnet_group_name   = aws_db_subnet_group.this.name
  vpc_security_group_ids = [var.security_group_id]

  publicly_accessible     = false
  backup_retention_period = var.backup_retention_period
  # DEV NOTE: skip_final_snapshot = true means no backup snapshot is taken when this RDS instance is destroyed. Fine for dev — acceptable to lose the data. 
  # For production set to false and set final_snapshot_identifier.
  skip_final_snapshot = true # Fine for dev, but not for production

  # DEV NOTE: lifecycle { prevent_destroy=true } prevents accidental deletion via a mistyped terraform destroy or workspace destroy. 
  # To intentionally delete: comment this bock out, apply, then destroy .
  # lifecycle {
  #   prevent_destroy = true
  # }
}

resource "aws_ssm_parameter" "rds_endpoint" {
  name        = var.ssm_parameter_path
  type        = "String"
  value       = aws_db_instance.postgres.address
  description = "RDS endpoint for ${var.project_name} — written by Terraform, consumed by Jenkins"

  tags = {
    Name = "${var.project_name}-rds-endpoint"
  }
}