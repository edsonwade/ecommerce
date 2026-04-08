# Policy: app-policy
# Grants Spring Boot microservices read-only access to their secrets

# Shared secrets (JWT, Kafka, Redis)
path "secret/data/ecommerce/shared" {
  capabilities = ["read"]
}

# Per-service secrets
path "secret/data/ecommerce/authentication-service" {
  capabilities = ["read"]
}

path "secret/data/ecommerce/order-service" {
  capabilities = ["read"]
}

path "secret/data/ecommerce/payment-service" {
  capabilities = ["read"]
}

path "secret/data/ecommerce/product-service" {
  capabilities = ["read"]
}

path "secret/data/ecommerce/customer-service" {
  capabilities = ["read"]
}

path "secret/data/ecommerce/cart-service" {
  capabilities = ["read"]
}

path "secret/data/ecommerce/notification-service" {
  capabilities = ["read"]
}

path "secret/data/ecommerce/gateway-api-service" {
  capabilities = ["read"]
}

path "secret/data/ecommerce/discovery-service" {
  capabilities = ["read"]
}

path "secret/data/ecommerce/config-service" {
  capabilities = ["read"]
}

# Dynamic database credentials
path "database/creds/order-service-role" {
  capabilities = ["read"]
}

path "database/creds/product-service-role" {
  capabilities = ["read"]
}

path "database/creds/payment-service-role" {
  capabilities = ["read"]
}

path "database/creds/auth-service-role" {
  capabilities = ["read"]
}
