# Key Pair

# Previous version used tls_private_key + local_sensitive_file to generate
# the key pair in Terraform and write the private key to disk. That put the
# private key PEM into the Terraform state file in plaintext (state lives in
# S3, readable by every principal with s3:GetObject on catalogix-tfstate/*,
# which today includes the entire Jenkins IAM role). There was also no
# .gitignore enforcing the local file stayed out of version control.
#
# Generate your own key pair locally and never let Terraform see the
# private half:
#   ssh-keygen -t ed25519 -f ./catalogix-key -C "catalogix" -N ""
#   # then in terraform.tfvars:
#   public_key = file("./catalogix-key.pub")

# resource "tls_private_key" "catalogix" {
#   algorithm = "RSA"
#   rsa_bits  = 4096
# }

resource "aws_key_pair" "catalogix" {
  key_name = var.key_name
  # public_key = tls_private_key.catalogix.public_key_openssh
  public_key = var.public_key
}

# Save the private key to disk so Ansible / SSH can use it.
# The file is created at apply time — add it to .gitignore.
# resource "local_sensitive_file" "private_key" {
#   content         = tls_private_key.catalogix.private_key_pem
#   filename        = "${path.module}/${var.key_name}.pem"
#   file_permission = "0600" # owner read-only — required by ssh/ansible
# }
