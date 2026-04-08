#!/bin/bash
# ==============================================================
# vault-init.sh — Initialize Vault and populate secrets
# Run once after Vault container first starts.
# Usage: ./vault/scripts/vault-init.sh
# ==============================================================

set -euo pipefail

VAULT_ADDR="${VAULT_ADDR:-http://localhost:8200}"
VAULT_TOKEN="${VAULT_TOKEN:-root-token}"

export VAULT_ADDR
export VAULT_TOKEN

echo "[vault-init] Waiting for Vault to be ready..."
until vault status 2>/dev/null | grep -q "Initialized"; do
  sleep 2
done

echo "[vault-init] Enabling KV v2 secrets engine..."
vault secrets enable -path=secret kv-v2 2>/dev/null || echo "KV already enabled"

echo "[vault-init] Writing shared secrets..."
vault kv put secret/ecommerce/shared \
  jwt_secret="${JWT_SECRET:-bXlTdXBlclNlY3VyZVNlY3JldEtleUZvckpXVEF1dGgxMjM0NTY3ODk=}" \
  kafka_username="${KAFKA_USERNAME:-kafka}" \
  kafka_password="${KAFKA_PASSWORD:-kafka-secret}" \
  redis_password="${REDIS_PASSWORD:-redis-secret}"

echo "[vault-init] Writing database secrets..."
vault kv put secret/ecommerce/authentication-service \
  postgres_username="${POSTGRES_USERNAME:-vanilson}" \
  postgres_password="${POSTGRES_PASSWORD:-vanilson}"

vault kv put secret/ecommerce/order-service \
  postgres_username="${POSTGRES_USERNAME:-vanilson}" \
  postgres_password="${POSTGRES_PASSWORD:-vanilson}"

vault kv put secret/ecommerce/payment-service \
  postgres_username="${POSTGRES_USERNAME:-vanilson}" \
  postgres_password="${POSTGRES_PASSWORD:-vanilson}"

vault kv put secret/ecommerce/product-service \
  postgres_username="${POSTGRES_USERNAME:-vanilson}" \
  postgres_password="${POSTGRES_PASSWORD:-vanilson}"

vault kv put secret/ecommerce/customer-service \
  mongo_username="${MONGO_USERNAME:-vanilson}" \
  mongo_password="${MONGO_PASSWORD:-vanilson}"

vault kv put secret/ecommerce/notification-service \
  mongo_username="${MONGO_USERNAME:-vanilson}" \
  mongo_password="${MONGO_PASSWORD:-vanilson}"

vault kv put secret/ecommerce/cart-service \
  redis_password="${REDIS_PASSWORD:-redis-secret}"

vault kv put secret/ecommerce/gateway-api-service \
  jwt_secret="${JWT_SECRET:-bXlTdXBlclNlY3VyZVNlY3JldEtleUZvckpXVEF1dGgxMjM0NTY3ODk=}" \
  redis_password="${REDIS_PASSWORD:-redis-secret}"

vault kv put secret/ecommerce/discovery-service \
  eureka_username="${EUREKA_USERNAME:-eureka}" \
  eureka_password="${EUREKA_PASSWORD:-eureka-secret-2024}"

vault kv put secret/ecommerce/config-service \
  placeholder="config-service-secrets"

echo "[vault-init] Enabling database secrets engine for dynamic credentials..."
vault secrets enable database 2>/dev/null || echo "Database engine already enabled"

# Configure PostgreSQL dynamic credentials for each database
for SERVICE_DB in "order-service:order_service_db:5432" "product-service:product_service_db:5433" "payment-service:payment_db:5434" "authentication-service:auth_db:5435"; do
  IFS=':' read -r SERVICE DB PORT <<< "$SERVICE_DB"
  CONN_NAME="${SERVICE}-db"

  vault write "database/config/${CONN_NAME}" \
    plugin_name=postgresql-database-plugin \
    allowed_roles="${SERVICE}-role" \
    connection_url="postgresql://{{username}}:{{password}}@postgres-${SERVICE%-service}:${PORT}/${DB}?sslmode=disable" \
    username="${POSTGRES_USERNAME:-vanilson}" \
    password="${POSTGRES_PASSWORD:-vanilson}"

  vault write "database/roles/${SERVICE}-role" \
    db_name="${CONN_NAME}" \
    creation_statements="CREATE ROLE \"{{name}}\" WITH LOGIN PASSWORD '{{password}}' VALID UNTIL '{{expiration}}'; GRANT ALL PRIVILEGES ON DATABASE ${DB} TO \"{{name}}\";" \
    default_ttl="1h" \
    max_ttl="24h"

  echo "[vault-init] Dynamic credentials configured for ${SERVICE} (${DB})"
done

echo "[vault-init] Writing app policy..."
vault policy write app-policy /vault/policies/app-policy.hcl

echo "[vault-init] Creating app token..."
vault token create \
  -policy=app-policy \
  -period=24h \
  -display-name=spring-boot-apps \
  -field=token > /tmp/app-token.txt

echo "[vault-init] App token saved to /tmp/app-token.txt"
echo "[vault-init] Vault initialization complete."
