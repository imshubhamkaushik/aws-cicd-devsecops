data "aws_caller_identity" "current" {}
data "aws_region" "current" {}

resource "aws_ecr_repository" "repos" {
  for_each = toset(var.repositories)

  name = each.value

  image_tag_mutability = "IMMUTABLE"

  # Lets Terraform delete a repository that still contains images; set to false in production.
  force_delete = true

  image_scanning_configuration {
    scan_on_push = true
  }

  encryption_configuration {
    encryption_type = "AES256"
  }

  # Only env/dev creates these repos; staging pulls from them. Destroying dev
  # would silently break staging, so prevent_destroy makes it a hard error.
  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_ecr_lifecycle_policy" "repos" {
  for_each   = toset(var.repositories)
  repository = aws_ecr_repository.repos[each.key].name

  policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "Keep last 5 tagged images"
        selection = {
          tagStatus      = "tagged"
          tagPatternList = ["*"]
          countType      = "imageCountMoreThan"
          countNumber    = 5
        }
        action = { type = "expire" }
      },
      {
        rulePriority = 2
        description  = "Expire untagged images after 1 day"
        selection = {
          tagStatus   = "untagged"
          countType   = "sinceImagePushed"
          countUnit   = "days"
          countNumber = 1
        }
        action = { type = "expire" }
      }
    ]
  })
}