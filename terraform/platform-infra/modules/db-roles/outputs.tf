output "credentials" {
  description = "Map of service name => { username, password, database } — merge into module.app_secrets' secret_values in the root module, don't write to Secrets Manager from here."
  sensitive   = true
  value = {
    for svc, dbname in var.services : svc => {
      username = postgresql_role.service[svc].name
      password = random_password.service_db[svc].result
      database = dbname
    }
  }
}
