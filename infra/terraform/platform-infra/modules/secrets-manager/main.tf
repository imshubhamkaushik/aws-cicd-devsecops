data "aws_region" "current" {}

# Deleted immediately on destroy so a later apply can reuse the same name.

resource "aws_secretsmanager_secret" "this" {
  name        = var.secret_name
  description = "Application database secrets for ${var.project_name}"

  recovery_window_in_days = 0 # no recovery window; use a non-zero value in production
}

resource "aws_secretsmanager_secret_version" "value" {
  secret_id     = aws_secretsmanager_secret.this.id
  secret_string = jsonencode(var.secret_values)
}