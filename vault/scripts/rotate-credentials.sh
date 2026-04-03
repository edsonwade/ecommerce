#!/bin/bash
# ==============================================================
# rotate-credentials.sh — Rotate database/Redis/Kafka credentials
# Usage: ./vault/scripts/rotate-credentials.sh
# ==============================================================

set -euo pipefail

VAULT_ADDR="${VAULT_ADDR:-http://localhost:8200}"
VAULT_TOKEN="${VAULT_TOKEN:-root-token}"

export VAULT_ADDR
export VAULT_TOKEN

echo "[rotate] Generating new PostgreSQL password..."
NEW_PG_PASS=$(openssl rand -base64 24)

echo "[rotate] Generating new MongoDB password..."
NEW_MONGO_PASS=$(openssl rand -base64 24)

echo "[rotate] Generating new Redis password..."
NEW_REDIS_PASS=$(openssl rand -base64 24)

echo "[rotate] Generating new JWT secret..."
NEW_JWT_SECRET=$(openssl rand -base64 32)

echo "[rotate] Updating secrets in Vault..."
vault kv patch secret/ecommerce/shared \
  jwt_secret="$NEW_JWT_SECRET" \
  redis_password="$NEW_REDIS_PASS"

vault kv patch secret/ecommerce/authentication-service \
  postgres_password="$NEW_PG_PASS"

vault kv patch secret/ecommerce/order-service \
  postgres_password="$NEW_PG_PASS"

vault kv patch secret/ecommerce/payment-service \
  postgres_password="$NEW_PG_PASS"

vault kv patch secret/ecommerce/product-service \
  postgres_password="$NEW_PG_PASS"

vault kv patch secret/ecommerce/customer-service \
  mongo_password="$NEW_MONGO_PASS"

vault kv patch secret/ecommerce/notification-service \
  mongo_password="$NEW_MONGO_PASS"

echo "[rotate] Credential rotation complete. Restart services to pick up new secrets."
echo "[rotate] New PostgreSQL password: $NEW_PG_PASS"
echo "[rotate] New MongoDB password:    $NEW_MONGO_PASS"
echo "[rotate] New Redis password:      $NEW_REDIS_PASS"
echo "[rotate] New JWT secret:          $NEW_JWT_SECRET"
