#!/usr/bin/env bash
# generate-tls-certs.sh — Generate TLS certificates for all services
# Dev: self-signed certs from a local CA
# Prod: replace with CA-signed certs (Let's Encrypt via cert-manager or your PKI)
#
# Usage: ./scripts/generate-tls-certs.sh
# Output: certs/ directory with per-service keystores and truststores
set -euo pipefail

CERTS_DIR="./certs"
CA_DIR="$CERTS_DIR/ca"
KEYSTORE_PASS="${SSL_KEYSTORE_PASSWORD:-changeit}"
VALIDITY_DAYS=365

SERVICES=(
  "gateway-api-service:8222"
  "config-service:8888"
  "discovery-service:8761"
  "authentication-service:8085"
  "customer-service:8090"
  "product-service:8082"
  "order-service:8083"
  "payment-service:8086"
  "cart-service:8091"
  "notification-service:8040"
  "kafka-broker:9093"
  "vault:8200"
)

log() { echo "[$(date +'%Y-%m-%d %H:%M:%S')] $*"; }

mkdir -p "$CA_DIR"
for svc_port in "${SERVICES[@]}"; do
  svc="${svc_port%%:*}"
  mkdir -p "$CERTS_DIR/$svc"
done

# ---------------------------------------------------------------------------
# Step 1: Generate Root CA
# ---------------------------------------------------------------------------
log "Generating Root CA..."
if [[ ! -f "$CA_DIR/ca.key" ]]; then
  openssl genrsa -out "$CA_DIR/ca.key" 4096

  openssl req -new -x509 \
    -key "$CA_DIR/ca.key" \
    -out "$CA_DIR/ca.crt" \
    -days $((VALIDITY_DAYS * 3)) \
    -subj "/C=PT/ST=Lisbon/O=VanilsonShop/CN=VanilsonShop-Root-CA"

  log "Root CA generated: $CA_DIR/ca.crt"
else
  log "Root CA already exists — reusing $CA_DIR/ca.crt"
fi

# ---------------------------------------------------------------------------
# Step 2: Generate per-service certificates
# ---------------------------------------------------------------------------
generate_service_cert() {
  local svc="$1" port="$2"
  local svc_dir="$CERTS_DIR/$svc"

  log "Generating cert for $svc (port $port)..."

  # Generate private key
  openssl genrsa -out "$svc_dir/$svc.key" 2048

  # Generate CSR with SAN
  openssl req -new \
    -key "$svc_dir/$svc.key" \
    -out "$svc_dir/$svc.csr" \
    -subj "/C=PT/ST=Lisbon/O=VanilsonShop/CN=$svc" \
    -config <(cat <<EOF
[req]
distinguished_name = req_distinguished_name
req_extensions = v3_req
prompt = no
[req_distinguished_name]
CN = $svc
[v3_req]
subjectAltName = DNS:$svc,DNS:localhost,IP:127.0.0.1
EOF
)

  # Sign with CA
  openssl x509 -req \
    -in "$svc_dir/$svc.csr" \
    -CA "$CA_DIR/ca.crt" \
    -CAkey "$CA_DIR/ca.key" \
    -CAcreateserial \
    -out "$svc_dir/$svc.crt" \
    -days $VALIDITY_DAYS \
    -extensions v3_req \
    -extfile <(echo "[v3_req]
subjectAltName = DNS:$svc,DNS:localhost,IP:127.0.0.1")

  # Create PKCS12 keystore for Spring Boot
  openssl pkcs12 -export \
    -in "$svc_dir/$svc.crt" \
    -inkey "$svc_dir/$svc.key" \
    -certfile "$CA_DIR/ca.crt" \
    -name "$svc" \
    -out "$svc_dir/keystore.p12" \
    -passout pass:"$KEYSTORE_PASS"

  # Create truststore (CA cert)
  keytool -importcert \
    -file "$CA_DIR/ca.crt" \
    -keystore "$svc_dir/truststore.p12" \
    -storetype PKCS12 \
    -storepass "$KEYSTORE_PASS" \
    -alias root-ca \
    -noprompt 2>/dev/null

  rm -f "$svc_dir/$svc.csr"
  log "  keystore: $svc_dir/keystore.p12"
  log "  truststore: $svc_dir/truststore.p12"
}

for svc_port in "${SERVICES[@]}"; do
  svc="${svc_port%%:*}"
  port="${svc_port##*:}"
  generate_service_cert "$svc" "$port"
done

# ---------------------------------------------------------------------------
# Step 3: Kafka broker keystore/truststore (JKS format for Confluent)
# ---------------------------------------------------------------------------
log "Generating Kafka JKS keystore..."
KAFKA_DIR="$CERTS_DIR/kafka-broker"

keytool -importkeystore \
  -srckeystore "$KAFKA_DIR/keystore.p12" \
  -srcstoretype PKCS12 \
  -srcstorepass "$KEYSTORE_PASS" \
  -destkeystore "$KAFKA_DIR/kafka.keystore.jks" \
  -deststoretype JKS \
  -deststorepass "$KEYSTORE_PASS" \
  -noprompt 2>/dev/null

keytool -importcert \
  -file "$CA_DIR/ca.crt" \
  -keystore "$KAFKA_DIR/kafka.truststore.jks" \
  -storetype JKS \
  -storepass "$KEYSTORE_PASS" \
  -alias root-ca \
  -noprompt 2>/dev/null

log "Kafka JKS keystores: $KAFKA_DIR/"

# ---------------------------------------------------------------------------
# Step 4: Generate RSA key pair for JWT RS256
# ---------------------------------------------------------------------------
JWT_DIR="$CERTS_DIR/jwt"
mkdir -p "$JWT_DIR"
log "Generating RSA key pair for JWT RS256..."

openssl genrsa -out "$JWT_DIR/jwt-private.pem" 2048
openssl rsa -in "$JWT_DIR/jwt-private.pem" -pubout -out "$JWT_DIR/jwt-public.pem"

# Base64-encode for environment variable injection
base64 -w0 "$JWT_DIR/jwt-private.pem" > "$JWT_DIR/jwt-private.b64"
base64 -w0 "$JWT_DIR/jwt-public.pem"  > "$JWT_DIR/jwt-public.b64"

log "JWT private key (base64): $JWT_DIR/jwt-private.b64 → set as JWT_PRIVATE_KEY env var"
log "JWT public key (base64):  $JWT_DIR/jwt-public.b64  → set as JWT_PUBLIC_KEY env var"

echo ""
log "=== Certificate generation complete ==="
log "All certs in: $CERTS_DIR/"
log ""
log "Next steps:"
log "  1. Copy keystore.p12/truststore.p12 into each service's src/main/resources/"
log "  2. Set SSL_KEYSTORE_PASSWORD and SSL_TRUSTSTORE_PASSWORD env vars"
log "  3. Set JWT_PRIVATE_KEY=\$(cat $JWT_DIR/jwt-private.b64) in auth-service"
log "  4. Set JWT_PUBLIC_KEY=\$(cat $JWT_DIR/jwt-public.b64) in gateway"
log "  5. For Vault TLS: copy $CERTS_DIR/vault/vault.crt and vault.key to vault/certs/"
log ""
log "PRODUCTION: Replace self-signed certs with CA-signed certs from your PKI or cert-manager."
