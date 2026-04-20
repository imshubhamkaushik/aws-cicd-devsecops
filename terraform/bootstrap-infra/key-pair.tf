# Key Pair
# Terraform generates an RSA key pair, registers the public key with AWS, and saves the private key locally — no manual Console steps needed.

resource "tls_private_key" "catalogix" {
  algorithm = "RSA"
  rsa_bits  = 4096
}

resource "aws_key_pair" "catalogix" {
  key_name   = var.key_name
  public_key = tls_private_key.catalogix.public_key_openssh
}

# Save the private key to disk so Ansible / SSH can use it.
# The file is created at apply time — add it to .gitignore.
resource "local_sensitive_file" "private_key" {
  content         = tls_private_key.catalogix.private_key_pem
  filename        = "${path.module}/${var.key_name}.pem"
  file_permission = "0600" # owner read-only — required by ssh/ansible
}
