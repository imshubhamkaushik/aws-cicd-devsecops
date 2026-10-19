-- Local-only initialisation: one database and one least-privilege login per service.
-- The official postgres image runs this file only when the data directory is empty.
-- Password is passed through the container environment; psql reads it without shell interpolation.
\getenv service_db_password SERVICE_DB_PASSWORD

-- user_svc / catalogix-users
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', 'user_svc', :'service_db_password')
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'user_svc') \gexec
SELECT format('CREATE DATABASE %I OWNER %I', 'catalogix-users', 'user_svc')
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'catalogix-users') \gexec

-- catalog_svc / catalogix-catalog
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', 'catalog_svc', :'service_db_password')
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'catalog_svc') \gexec
SELECT format('CREATE DATABASE %I OWNER %I', 'catalogix-catalog', 'catalog_svc')
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'catalogix-catalog') \gexec

-- inventory_svc / catalogix-inventory
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', 'inventory_svc', :'service_db_password')
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'inventory_svc') \gexec
SELECT format('CREATE DATABASE %I OWNER %I', 'catalogix-inventory', 'inventory_svc')
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'catalogix-inventory') \gexec

-- cart_svc / catalogix-cart
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', 'cart_svc', :'service_db_password')
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'cart_svc') \gexec
SELECT format('CREATE DATABASE %I OWNER %I', 'catalogix-cart', 'cart_svc')
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'catalogix-cart') \gexec

-- payment_svc / catalogix-payment
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', 'payment_svc', :'service_db_password')
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'payment_svc') \gexec
SELECT format('CREATE DATABASE %I OWNER %I', 'catalogix-payment', 'payment_svc')
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'catalogix-payment') \gexec

-- checkout_svc / catalogix-checkout
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', 'checkout_svc', :'service_db_password')
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'checkout_svc') \gexec
SELECT format('CREATE DATABASE %I OWNER %I', 'catalogix-checkout', 'checkout_svc')
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'catalogix-checkout') \gexec

-- notification_svc / catalogix-notification
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', 'notification_svc', :'service_db_password')
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'notification_svc') \gexec
SELECT format('CREATE DATABASE %I OWNER %I', 'catalogix-notification', 'notification_svc')
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'catalogix-notification') \gexec
