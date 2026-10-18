# Key Pair
#
# Only the public half is managed by Terraform, so the private key never
# lands in state (state is readable by every principal with s3:GetObject
# on the state bucket). Generate the pair locally with
# python3 scripts/python/setup/create_ssh_key.py (writes ~/.ssh/catalogix-key{,.pub}); the
# bootstrap script reads the .pub automatically. By hand, use
# TF_VAR_public_key or a literal value in terraform.tfvars (functions such as
# file() are not allowed in tfvars).

resource "aws_key_pair" "catalogix" {
  key_name   = var.key_name
  public_key = var.public_key
}
