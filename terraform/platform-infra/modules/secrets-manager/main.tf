resource "aws_secretsmanager_secret" "this" {
  name = var.secret_name
  description = "Application database secrets for ${var.project_name}"

  recovery_window_in_days = 0 # Disable deletion protection, fine for dev not for production
}

resource "aws_secretsmanager_secret_version" "value" {
  secret_id     = aws_secretsmanager_secret.this.id
  secret_string = jsonencode(var.secret_values)
}