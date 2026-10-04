# Key Pair
#
# Only the public half is managed by Terraform, so the private key never
# lands in state (state is readable by every principal with s3:GetObject
# on the state bucket). Generate the pair locally:
#   ssh-keygen -t ed25519 -f ./catalogix-key -C "catalogix" -N ""
# then set public_key = file("./catalogix-key.pub") in terraform.tfvars.

resource "aws_key_pair" "catalogix" {
  key_name   = var.key_name
  public_key = var.public_key
}
