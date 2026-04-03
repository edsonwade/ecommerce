# Vault server configuration for local dev/staging
# For production, use HA Raft storage with TLS

storage "file" {
  path = "/vault/data"
}

listener "tcp" {
  address     = "0.0.0.0:8200"
  tls_disable = 1  # Set to 0 in production with cert_file/key_file
  # tls_cert_file = "/vault/certs/vault.crt"
  # tls_key_file  = "/vault/certs/vault.key"
}

api_addr     = "http://0.0.0.0:8200"
cluster_addr = "http://0.0.0.0:8201"
ui           = true

# Disable mlock for Docker (requires IPC_LOCK capability otherwise)
disable_mlock = true

# Default lease TTL and max lease TTL
default_lease_ttl = "24h"
max_lease_ttl     = "720h"
