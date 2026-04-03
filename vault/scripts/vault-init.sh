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
