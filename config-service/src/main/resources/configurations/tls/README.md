# TLS Configuration Guide

## Overview

TLS is **disabled by default** in development. To enable in staging/production:

1. Run `./scripts/generate-certs.sh` to create self-signed certs (or provide CA-signed certs).
2. Set `TLS_ENABLED=true` environment variable.
3. Each service will pick up TLS settings from their config.

---

## Redis TLS

Add to service configs that use Redis (gateway, cart, product, customer):

```yaml
spring:
  data:
    redis:
      ssl:
        enabled: ${TLS_ENABLED:false}
```

Mount the CA cert into containers and set `spring.data.redis.ssl.trust-store` in production.

---

## Kafka TLS (SSL listener)

Add to Kafka producer/consumer properties in service configs:

```yaml
spring:
  kafka:
    properties:
      security.protocol: SASL_SSL           # upgrade from SASL_PLAINTEXT
      ssl.truststore.location: /etc/kafka/certs/kafka.truststore.jks
      ssl.truststore.password: changeit
```

---

## PostgreSQL SSL

Add to datasource URL in service configs:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://${POSTGRES_HOST}:5432/db?sslmode=require
```

Mount `certs/postgres/server.crt` and `server.key` into the postgres containers.

---

## Vault TLS (Production)

In `vault/config/vault.hcl`, uncomment the TLS block:

```hcl
listener "tcp" {
  address     = "0.0.0.0:8200"
  tls_disable = 0
  tls_cert_file = "/vault/certs/vault.crt"
  tls_key_file  = "/vault/certs/vault.key"
}
```

Then update bootstrap.yml in each service:
```yaml
spring.cloud.vault.scheme: https
```
