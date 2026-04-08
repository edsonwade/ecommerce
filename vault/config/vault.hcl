# Vault server configuration
# Dev mode: TLS disabled (tls_disable = 1)
# Production: Set VAULT_TLS=true and provide cert/key paths
#
# To enable TLS for production:
#   1. Run ./scripts/generate-tls-certs.sh to generate certs
#   2. Copy certs/vault/vault.crt and vault.key to vault/certs/
#   3. Set tls_disable = 0 and uncomment tls_cert_file/tls_key_file
#   4. Update api_addr and cluster_addr to use https://
#   5. Update all services' VAULT_SCHEME=https

storage "file" {
  path = "/vault/data"
}

listener "tcp" {
  address = "0.0.0.0:8200"

  # DEV: TLS disabled — use PLAINTEXT for local development
  tls_disable = 1

  # PRODUCTION: Uncomment and set cert paths after generating with generate-tls-certs.sh
  # tls_disable     = 0
  # tls_cert_file   = "/vault/certs/vault.crt"
  # tls_key_file    = "/vault/certs/vault.key"
  # tls_min_version = "tls12"
}

# PRODUCTION api_addr: change http → https when TLS is enabled
api_addr     = "http://0.0.0.0:8200"
cluster_addr = "http://0.0.0.0:8201"

ui = true

# Disable mlock for Docker (requires IPC_LOCK capability otherwise)
disable_mlock = true

# Default lease TTL and max lease TTL
default_lease_ttl = "24h"
max_lease_ttl     = "720h"
