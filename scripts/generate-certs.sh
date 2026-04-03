#!/bin/bash
# ==============================================================
# generate-certs.sh — Generate self-signed TLS certificates
# for development/staging. Use a proper CA in production.
#
# Usage: ./scripts/generate-certs.sh
# Output: certs/ directory with all certificates
# ==============================================================

set -euo pipefail

CERTS_DIR="./certs"
mkdir -p "$CERTS_DIR"/{kafka,redis,postgres,vault}

echo "[certs] Generating CA key and certificate..."
openssl genrsa -out "$CERTS_DIR/ca.key" 4096
openssl req -new -x509 -days 3650 -key "$CERTS_DIR/ca.key" \
  -out "$CERTS_DIR/ca.crt" \
  -subj "/CN=ecommerce-ca/O=VanilsonShop/C=US"

# -------------------------------------------------------
# Kafka TLS
# -------------------------------------------------------
echo "[certs] Generating Kafka certificates..."
keytool -genkey -noprompt \
  -alias kafka \
  -dname "CN=kafka,O=VanilsonShop,C=US" \
  -keystore "$CERTS_DIR/kafka/kafka.keystore.jks" \
  -keyalg RSA -keysize 2048 \
  -storepass changeit -keypass changeit

keytool -export -noprompt \
  -alias kafka \
  -keystore "$CERTS_DIR/kafka/kafka.keystore.jks" \
  -file "$CERTS_DIR/kafka/kafka.crt" \
  -storepass changeit

keytool -import -noprompt \
  -alias ca \
  -file "$CERTS_DIR/ca.crt" \
  -keystore "$CERTS_DIR/kafka/kafka.truststore.jks" \
  -storepass changeit

# -------------------------------------------------------
# Redis TLS
# -------------------------------------------------------
echo "[certs] Generating Redis certificates..."
openssl genrsa -out "$CERTS_DIR/redis/redis.key" 2048
openssl req -new -key "$CERTS_DIR/redis/redis.key" \
  -out "$CERTS_DIR/redis/redis.csr" \
  -subj "/CN=redis/O=VanilsonShop/C=US"
openssl x509 -req -days 365 \
  -in "$CERTS_DIR/redis/redis.csr" \
  -CA "$CERTS_DIR/ca.crt" \
  -CAkey "$CERTS_DIR/ca.key" \
  -CAcreateserial \
  -out "$CERTS_DIR/redis/redis.crt"

# -------------------------------------------------------
# PostgreSQL TLS
# -------------------------------------------------------
echo "[certs] Generating PostgreSQL certificates..."
openssl genrsa -out "$CERTS_DIR/postgres/server.key" 2048
openssl req -new -key "$CERTS_DIR/postgres/server.key" \
  -out "$CERTS_DIR/postgres/server.csr" \
  -subj "/CN=postgres/O=VanilsonShop/C=US"
openssl x509 -req -days 365 \
  -in "$CERTS_DIR/postgres/server.csr" \
  -CA "$CERTS_DIR/ca.crt" \
  -CAkey "$CERTS_DIR/ca.key" \
  -CAcreateserial \
  -out "$CERTS_DIR/postgres/server.crt"
chmod 600 "$CERTS_DIR/postgres/server.key"

# -------------------------------------------------------
# Vault TLS
# -------------------------------------------------------
echo "[certs] Generating Vault certificates..."
openssl genrsa -out "$CERTS_DIR/vault/vault.key" 2048
openssl req -new -key "$CERTS_DIR/vault/vault.key" \
  -out "$CERTS_DIR/vault/vault.csr" \
  -subj "/CN=vault/O=VanilsonShop/C=US"
openssl x509 -req -days 365 \
  -in "$CERTS_DIR/vault/vault.csr" \
  -CA "$CERTS_DIR/ca.crt" \
  -CAkey "$CERTS_DIR/ca.key" \
  -CAcreateserial \
  -out "$CERTS_DIR/vault/vault.crt"

echo "[certs] All certificates generated in $CERTS_DIR/"
echo "[certs] NOTE: These are self-signed. Use a proper CA in production."
