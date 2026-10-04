provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = var.project_tag
      ManagedBy   = "Terraform"
      Environment = "bootstrap"
    }
  }
}

resource "aws_s3_bucket" "tf_state" {
  bucket = "catalogix-tfstate"

  lifecycle {
    prevent_destroy = true # 
  }

  tags = {
    Name = "Terraform State Bucket - Catalogix (aws-cicd-devsecops)"
  }
}

resource "aws_s3_bucket_public_access_block" "tf_state" {
  bucket = aws_s3_bucket.tf_state.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_versioning" "versioning" {
  bucket = aws_s3_bucket.tf_state.id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "encryption" {
  bucket = aws_s3_bucket.tf_state.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

# Was missing entirely. Modern best practice for any bucket that doesn't
# need ACLs (this one never did — access is IAM-only) is to disable them
# outright rather than leave the door open for someone to attach one by
# hand later.
resource "aws_s3_bucket_ownership_controls" "tf_state" {
  bucket = aws_s3_bucket.tf_state.id

  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

# Was missing entirely. Denies any request to this bucket that isn't over
# TLS — the state file contains the RDS password, JWT secret, and
# RabbitMQ credentials in plaintext (Terraform state is never encrypted at
# the field level), so this is a meaningful control, not boilerplate.
resource "aws_s3_bucket_policy" "tf_state" {
  bucket = aws_s3_bucket.tf_state.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid       = "DenyInsecureTransport"
        Effect    = "Deny"
        Principal = "*"
        Action    = "s3:*"
        Resource = [
          aws_s3_bucket.tf_state.arn,
          "${aws_s3_bucket.tf_state.arn}/*"
        ]
        Condition = {
          Bool = {
            "aws:SecureTransport" = "false"
          }
        }
      }
    ]
  })
}

# Was missing entirely. Versioning alone means every state write ever made
# stays in the bucket forever — each apply produces a new version, and
# with no expiration this is unbounded storage growth for objects nobody
# will ever restore from once they're this old. 90 days keeps a generous
# recovery window (state corruption is rare, but when it happens you
# usually notice within days, not months) while capping the growth.
# Applies only to noncurrent versions — the current, live state file is
# never touched by this rule regardless of age.
resource "aws_s3_bucket_lifecycle_configuration" "tf_state" {
  bucket = aws_s3_bucket.tf_state.id

  rule {
    id     = "expire-old-state-versions"
    status = "Enabled"

    noncurrent_version_expiration {
      noncurrent_days = 90
    }
  }
}