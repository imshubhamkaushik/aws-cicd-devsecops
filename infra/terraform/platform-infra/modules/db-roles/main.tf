# Per-service databases + roles on the shared RDS instance.
#
# Each backend service gets its own database and login role, created through
# the shared master connection. Isolation is role-level on one RDS instance,
# not one instance per service.
# Database names (catalogix-users, catalogix-catalog, ...) use hyphens and
# must match the Helm values and postgres-init.
#
# Credentials are exposed as outputs; the root module merges them into the
# single secret managed by module.app_secrets.

variable "readonly_username" {
  description = "Optional read-only login for humans (psql/pgAdmin). Empty = do not create it."
  type        = string
  default     = ""
}

variable "readonly_password" {
  description = "Password of the read-only login. Required when readonly_username is set."
  type        = string
  default     = ""
  sensitive   = true
}

locals {
  create_readonly = var.readonly_username != "" && var.readonly_password != ""
}

variable "services" {
  description = "Map of service name => database name, e.g. { \"user-svc\" = \"catalogix-users\" }"
  type        = map(string)
}

resource "random_password" "service_db" {
  for_each = var.services

  length  = 20
  special = false # avoids JDBC URL encoding issues, same reasoning as the master DB password

  keepers = {
    service = each.key
  }
}

resource "postgresql_role" "service" {
  for_each = var.services

  name     = replace(each.key, "-", "_") # Postgres role names can't contain hyphens
  login    = true
  password = random_password.service_db[each.key].result

  # No elevated privileges; the role only gets the grants defined below.
  superuser       = false
  create_role     = false
  create_database = false
}

resource "postgresql_database" "service" {
  for_each = var.services

  name  = each.value
  owner = postgresql_role.service[each.key].name

  allow_connections = true
}

resource "postgresql_grant" "service_schema" {
  for_each = var.services

  database    = postgresql_database.service[each.key].name
  role        = postgresql_role.service[each.key].name
  schema      = "public"
  object_type = "schema"
  privileges  = ["CREATE", "USAGE"]

  depends_on = [postgresql_database.service]
}

# Optional read-only login for humans (psql/pgAdmin), so neither the master nor an
# application credential is needed. It can CONNECT and SELECT only. Tables are created
# later by Flyway, so default privileges also cover tables created in the future.
# The instance is private; connect through a tunnel (see README.md).
resource "postgresql_role" "readonly" {
  count = local.create_readonly ? 1 : 0

  name     = var.readonly_username
  login    = true
  password = var.readonly_password

  superuser       = false
  create_role     = false
  create_database = false
}

resource "postgresql_grant" "readonly_connect" {
  for_each = local.create_readonly ? var.services : {}

  database    = postgresql_database.service[each.key].name
  role        = postgresql_role.readonly[0].name
  object_type = "database"
  privileges  = ["CONNECT"]
}

resource "postgresql_grant" "readonly_schema" {
  for_each = local.create_readonly ? var.services : {}

  database    = postgresql_database.service[each.key].name
  role        = postgresql_role.readonly[0].name
  schema      = "public"
  object_type = "schema"
  privileges  = ["USAGE"]

  depends_on = [postgresql_grant.readonly_connect]
}

resource "postgresql_grant" "readonly_tables" {
  for_each = local.create_readonly ? var.services : {}

  database    = postgresql_database.service[each.key].name
  role        = postgresql_role.readonly[0].name
  schema      = "public"
  object_type = "table"
  objects     = []
  privileges  = ["SELECT"]

  depends_on = [postgresql_grant.readonly_schema]
}

resource "postgresql_default_privileges" "readonly_future_tables" {
  for_each = local.create_readonly ? var.services : {}

  database    = postgresql_database.service[each.key].name
  role        = postgresql_role.readonly[0].name
  owner       = postgresql_role.service[each.key].name
  schema      = "public"
  object_type = "table"
  privileges  = ["SELECT"]

  depends_on = [postgresql_grant.readonly_schema]
}
