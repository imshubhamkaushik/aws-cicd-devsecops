variable "name" {
  description = "Name prefix for the Web ACL and SSM parameter (typically local.env_prefix from the caller)."
  type        = string
}

variable "region" {
  description = "AWS region — only used for the SSM parameter description, not for any region-scoped resource (Web ACL is REGIONAL scope, valid in any region)."
  type        = string
}

variable "rate_limit_requests_per_5min" {
  description = "Max requests from a single IP in a 5-minute window before AWS WAF starts blocking it. AWS WAF's rate-based rules are always evaluated over a fixed 5-minute window — this isn't a per-second or per-minute setting."
  type        = number
  default     = 2000
}

variable "ssm_parameter_path" {
  description = "SSM path the Web ACL ARN is written to. Jenkinsfile.app-cicd and Jenkinsfile.platform-infra both read this at /${CLUSTER_NAME}/waf-acl-arn — keep this matching that pattern unless you change both Jenkinsfiles too."
  type        = string
}
