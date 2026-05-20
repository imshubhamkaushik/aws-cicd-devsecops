data "aws_region" "current" {}

# Force-delete any same-named secret that is pending deletion.
# Secrets Manager holds deleted secrets for up to 30 days by default.
# Without this, terraform apply fails with InvalidRequestException when
# re-applying after a destroy if the previous deletion used a recovery window.
resource "terraform_data" "force_delete_pending_secret" {
  triggers_replace = {
    secret_name = var.secret_name
  }

  provisioner "local-exec" {
    command = <<-EOF
      set -e
      DELETED_DATE=$(aws secretsmanager describe-secret \
        --secret-id "${var.secret_name}" \
        --region "${data.aws_region.current.region}" \
        --query 'DeletedDate' \
        --output text 2>/dev/null || echo "NOT_FOUND")

      if [ "$DELETED_DATE" = "NOT_FOUND" ] || [ "$DELETED_DATE" = "None" ]; then
        echo "Secret '${var.secret_name}' is active or does not exist — skipping force-delete."
      else
        echo "Secret '${var.secret_name}' is pending deletion — force-deleting..."
        aws secretsmanager delete-secret \
          --secret-id "${var.secret_name}" \
          --force-delete-without-recovery \
          --region "${data.aws_region.current.region}"
        sleep 5
      fi
    EOF
  }
}

resource "aws_secretsmanager_secret" "this" {
  name        = var.secret_name
  description = "Application database secrets for ${var.project_name}"

  recovery_window_in_days = 0 # Disable deletion protection, fine for dev not for production

  depends_on = [terraform_data.force_delete_pending_secret]
}

resource "aws_secretsmanager_secret_version" "value" {
  secret_id     = aws_secretsmanager_secret.this.id
  secret_string = jsonencode(var.secret_values)
}