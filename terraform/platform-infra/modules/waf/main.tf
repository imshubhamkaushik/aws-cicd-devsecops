# =============================================================================
# WAFv2 Web ACL for the application ALB
# =============================================================================
#
# This module deliberately does NOT create an aws_wafv2_web_acl_association.
# The ALB itself is created dynamically by the AWS Load Balancer Controller
# (a Kubernetes controller, not Terraform) whenever an Ingress is applied —
# Terraform has no ALB ARN to associate against at apply time, and re-running
# this module on every Ingress change isn't a workable loop.
#
# Instead, the association happens the same way the rest of this project
# already wires WAF in: this module creates the Web ACL and publishes its ARN
# to SSM. Jenkinsfile.app-cicd reads that ARN and passes it to Helm as
# --set ingress.wafAclArn=..., and helm/catalogix-hc/templates/ingress-alb.yaml
# already renders it as the alb.ingress.kubernetes.io/wafv2-acl-arn annotation
# — the AWS Load Balancer Controller reads that annotation and performs the
# association itself when it provisions/reconciles the ALB. That annotation
# existed before this module did; this module is what makes it stop being a
# no-op (SSM_WAF_PATH in the Jenkinsfiles always resolved to empty before,
# since no Terraform resource ever wrote anything there).
#
# Scope is REGIONAL, not CLOUDFRONT — this protects an Application Load
# Balancer, not a CloudFront distribution.

resource "aws_wafv2_web_acl" "this" {
  name        = "${var.name}-waf"
  description = "WAF ACL for the ${var.name} application ALB."
  scope       = "REGIONAL"

  default_action {
    allow {}
  }

  # AWS-managed Core Rule Set — generic protections (SQLi, XSS, oversized
  # bodies, no-UA requests, and other OWASP Top 10-adjacent patterns).
  rule {
    name     = "AWSManagedRulesCommonRuleSet"
    priority = 0

    override_action {
      none {}
    }

    statement {
      managed_rule_group_statement {
        name        = "AWSManagedRulesCommonRuleSet"
        vendor_name = "AWS"
      }
    }

    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "${var.name}-common-rule-set"
      sampled_requests_enabled   = true
    }
  }

  # Blocks requests matching known-malicious request patterns (exploit kit
  # signatures, known bad request bodies) — distinct from the generic CRS above.
  rule {
    name     = "AWSManagedRulesKnownBadInputsRuleSet"
    priority = 1

    override_action {
      none {}
    }

    statement {
      managed_rule_group_statement {
        name        = "AWSManagedRulesKnownBadInputsRuleSet"
        vendor_name = "AWS"
      }
    }

    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "${var.name}-known-bad-inputs"
      sampled_requests_enabled   = true
    }
  }

  # Rate-based rule — not part of either managed group above. Blocks a single
  # IP that crosses the request-count threshold; AWS WAF unblocks it
  # automatically once that IP's rate drops back below the limit.
  rule {
    name     = "RateLimitPerIP"
    priority = 2

    action {
      block {}
    }

    statement {
      rate_based_statement {
        limit              = var.rate_limit_requests_per_5min
        aggregate_key_type = "IP"
      }
    }

    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "${var.name}-rate-limit"
      sampled_requests_enabled   = true
    }
  }

  visibility_config {
    cloudwatch_metrics_enabled = true
    metric_name                = "${var.name}-waf-acl"
    sampled_requests_enabled   = true
  }

  tags = {
    Name = "${var.name}-waf"
  }
}

resource "aws_ssm_parameter" "waf_acl_arn" {
  name        = var.ssm_parameter_path
  type        = "String"
  value       = aws_wafv2_web_acl.this.arn
  description = "WAFv2 Web ACL ARN for ${var.name} (region ${var.region}) — written by Terraform, consumed by Jenkins and rendered into the ALB Ingress annotation by Helm."

  tags = {
    Name = "${var.name}-waf-acl-arn"
  }
}
