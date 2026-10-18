terraform {
  required_providers {
    postgresql = { source = "cyrilgdn/postgresql", version = "~> 1.25" }
    random     = { source = "hashicorp/random", version = "~> 3.0" }
  }
}
