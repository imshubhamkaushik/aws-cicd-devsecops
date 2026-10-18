output "alb_role_arn" {
  description = "ARN of the IAM role for the AWS Load Balancer Controller — use in ALB Helm chart values for IRSA"
  value       = aws_iam_role.alb.arn
}