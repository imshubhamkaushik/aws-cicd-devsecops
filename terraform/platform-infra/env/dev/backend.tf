terraform {
  backend "s3" {
    bucket       = "catalogix-tfstate"
    key          = "platform/dev/terraform.tfstate"
    region       = "ap-south-1"
    encrypt      = true
    use_lockfile = true
  }

  required_version = ">= 1.12.0"
}

