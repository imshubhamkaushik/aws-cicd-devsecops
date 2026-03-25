terraform {
  backend "s3" {
    bucket       = "catalogix-tfstate"
    key          = "bootstrap/terraform.tfstate"
    region       = "ap-south-1"
    encrypt      = true
    use_lockfile = true
  }
}

