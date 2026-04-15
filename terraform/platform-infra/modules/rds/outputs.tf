# Changed from aws_db_instance.postgres.endpoint to .address
# .endpoint returns "hostname:5432" (host + port combined).
# .address returns just the hostname.
#
# The Helm template builds the JDBC URL as:
#   jdbc:postgresql://{{ database.host }}:{{ database.port }}/{{ database.name }}
#
# If database.host were the endpoint (host:5432), the URL would become:
#   jdbc:postgresql://host:5432:5432/catalogix  ← BROKEN
#
# Using .address ensures database.host is just the hostname:
#   jdbc:postgresql://host:5432/catalogix  ← CORRECT

output "rds_endpoint" {
  description = "RDS hostname (no port) — use as database.host in Helm values"
  value       = aws_db_instance.postgres.address
}