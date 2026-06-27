output "web_acl_arn" {
  description = "ARN of the WAFv2 Web ACL — also published to SSM at var.ssm_parameter_path."
  value       = aws_wafv2_web_acl.this.arn
}

output "web_acl_id" {
  value = aws_wafv2_web_acl.this.id
}
