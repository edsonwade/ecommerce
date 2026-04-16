# Enterprise E-Commerce SaaS Architecture
**Technical Architecture and Internal Implementation Documentation**

## 1. GLOBAL ARCHITECTURE 

### `docker-compose.ha.yml`

```

###############################################################
# docker-compose.ha.yml — High Availability overlay
# Replaces single Kafka+Zookeeper with 3-broker KRaft cluster
# Adds Redis Sentinel (1 master + 2 replicas + 3 sentinels)
# Adds second Eureka instance for peer awareness
#
# Usage: docker compose -f docker-compose.yml -f docker-compose.ha.yml up
###############################################################

services:

  # ============================================================
  # Disable single-broker Kafka + Zookeeper (replaced by KRaft cluster)
  # ============================================================
  zookeeper:
    deploy:
      replicas: 0

  kafka:
    deploy:
      replicas: 0

  # ============================================================
  # Kafka KRaft — 3-broker cluster (no Zookeeper)
  # All brokers serve as combined controller + broker (KRaft combined mode)
  # ============================================================

  kafka1:
    image: confluentinc/cp-kafka:7.6.1
    container_name: kafka1
    environment:
      KAFKA_NODE_ID: 1
      KAFKA_PROCESS_ROLES: broker,controller
      KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka1:9093,2@kafka2:9095,3@kafka3:9097
      KAFKA_LISTENERS: SASL_PLAINTEXT://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093
      KAFKA_ADVERTISED_LISTENERS: SASL_PLAINTEXT://kafka1:9092
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: SASL_PLAINTEXT:SASL_PLAINTEXT,CONTROLLER:PLAINTEXT
      KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
      KAFKA_INTER_BROKER_LISTENER_NAME: SASL_PLAINTEXT
      KAFKA_SASL_ENABLED_MECHANISMS: PLAIN
      KAFKA_SASL_MECHANISM_INTER_BROKER_PROTOCOL: PLAIN
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 3
      KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 3
      KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 2
      KAFKA_DEFAULT_REPLICATION_FACTOR: 3
      KAFKA_MIN_INSYNC_REPLICAS: 2
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: "false"
      KAFKA_ENABLE_IDEMPOTENCE: "true"
      CLUSTER_ID: ${KAFKA_CLUSTER_ID:-MkU3OEVBNTcwNTJENDM2Qk}
      KAFKA_OPTS: "-Djava.security.auth.login.config=/etc/kafka/jaas/kafka_server_jaas.conf"
    volumes:
      - kafka1-data:/var/lib/kafka/data
      - ./kafka/jaas:/etc/kafka/jaas:ro
    networks: [infra-net]
    healthcheck:
      test: ["CMD", "kafka-broker-api-versions", "--bootstrap-server", "localhost:9092",
             "--command-config", "/etc/kafka/jaas/client.properties"]
      interval: 15s
      timeout: 10s
      retries: 8

  kafka2:
    image: confluentinc/cp-kafka:7.6.1
    container_name: kafka2
    environment:
      KAFKA_NODE_ID: 2
      KAFKA_PROCESS_ROLES: broker,controller
      KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka1:9093,2@kafka2:9095,3@kafka3:9097
      KAFKA_LISTENERS: SASL_PLAINTEXT://0.0.0.0:9094,CONTROLLER://0.0.0.0:9095
      KAFKA_ADVERTISED_LISTENERS: SASL_PLAINTEXT://kafka2:9094
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: SASL_PLAINTEXT:SASL_PLAINTEXT,CONTROLLER:PLAINTEXT
      KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
      KAFKA_INTER_BROKER_LISTENER_NAME: SASL_PLAINTEXT
      KAFKA_SASL_ENABLED_MECHANISMS: PLAIN
      KAFKA_SASL_MECHANISM_INTER_BROKER_PROTOCOL: PLAIN
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 3
      KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 3
      KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 2
      KAFKA_DEFAULT_REPLICATION_FACTOR: 3
      KAFKA_MIN_INSYNC_REPLICAS: 2
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: "false"
      KAFKA_ENABLE_IDEMPOTENCE: "true"
      CLUSTER_ID: ${KAFKA_CLUSTER_ID:-MkU3OEVBNTcwNTJENDM2Qk}
      KAFKA_OPTS: "-Djava.security.auth.login.config=/etc/kafka/jaas/kafka_server_jaas.conf"
    volumes:
      - kafka2-data:/var/lib/kafka/data
      - ./kafka/jaas:/etc/kafka/jaas:ro
    networks: [infra-net]
    healthcheck:
      test: ["CMD", "kafka-broker-api-versions", "--bootstrap-server", "localhost:9094",
             "--command-config", "/etc/kafka/jaas/client.properties"]
      interval: 15s
      timeout: 10s
      retries: 8

  kafka3:
    image: confluentinc/cp-kafka:7.6.1
    container_name: kafka3
    environment:
      KAFKA_NODE_ID: 3
      KAFKA_PROCESS_ROLES: broker,controller
      KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka1:9093,2@kafka2:9095,3@kafka3:9097
      KAFKA_LISTENERS: SASL_PLAINTEXT://0.0.0.0:9096,CONTROLLER://0.0.0.0:9097
      KAFKA_ADVERTISED_LISTENERS: SASL_PLAINTEXT://kafka3:9096
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: SASL_PLAINTEXT:SASL_PLAINTEXT,CONTROLLER:PLAINTEXT
      KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
      KAFKA_INTER_BROKER_LISTENER_NAME: SASL_PLAINTEXT
      KAFKA_SASL_ENABLED_MECHANISMS: PLAIN
      KAFKA_SASL_MECHANISM_INTER_BROKER_PROTOCOL: PLAIN
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 3
      KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 3
      KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 2
      KAFKA_DEFAULT_REPLICATION_FACTOR: 3
      KAFKA_MIN_INSYNC_REPLICAS: 2
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: "false"
      KAFKA_ENABLE_IDEMPOTENCE: "true"
      CLUSTER_ID: ${KAFKA_CLUSTER_ID:-MkU3OEVBNTcwNTJENDM2Qk}
      KAFKA_OPTS: "-Djava.security.auth.login.config=/etc/kafka/jaas/kafka_server_jaas.conf"
    volumes:
      - kafka3-data:/var/lib/kafka/data
      - ./kafka/jaas:/etc/kafka/jaas:ro
    networks: [infra-net]
    healthcheck:
      test: ["CMD", "kafka-broker-api-versions", "--bootstrap-server", "localhost:9096",
             "--command-config", "/etc/kafka/jaas/client.properties"]
      interval: 15s
      timeout: 10s
      retries: 8

  # ============================================================
  # Redis Sentinel — 1 master + 2 replicas + 3 sentinels
  # ============================================================

  redis:
    # Override base redis with explicit master config
    command: >
      redis-server
      --maxmemory 512mb
      --maxmemory-policy allkeys-lru
      --save 60 1
      --appendonly yes
      --appendfsync everysec
      --loglevel warning
      --requirepass ${REDIS_PASSWORD:-redis-secret-2024}
    volumes:
      - redis-data:/data

  redis-replica-1:
    image: redis:7.2-alpine
    container_name: redis-replica-1
    command: >
      redis-server
      --replicaof redis 6379
      --requirepass ${REDIS_PASSWORD:-redis-secret-2024}
      --masterauth ${REDIS_PASSWORD:-redis-secret-2024}
      --maxmemory 512mb
      --maxmemory-policy allkeys-lru
      --loglevel warning
    volumes:
      - redis-replica1-data:/data
    networks: [infra-net, services-net]
    depends_on:
      redis:
        condition: service_healthy

  redis-replica-2:
    image: redis:7.2-alpine
    container_name: redis-replica-2
    command: >
      redis-server
      --replicaof redis 6379
      --requirepass ${REDIS_PASSWORD:-redis-secret-2024}
      --masterauth ${REDIS_PASSWORD:-redis-secret-2024}
      --maxmemory 512mb
      --maxmemory-policy allkeys-lru
      --loglevel warning
    volumes:
      - redis-replica2-data:/data
    networks: [infra-net, services-net]
    depends_on:
      redis:
        condition: service_healthy

  sentinel-1:
    image: redis:7.2-alpine
    container_name: sentinel-1
    command: >
      redis-sentinel /etc/redis/sentinel.conf
    volumes:
      - ./config/redis/sentinel.conf:/etc/redis/sentinel.conf:ro
    networks: [infra-net, services-net]
    depends_on:
      - redis
      - redis-replica-1
      - redis-replica-2

  sentinel-2:
    image: redis:7.2-alpine
    container_name: sentinel-2
    command: >
      redis-sentinel /etc/redis/sentinel.conf
    volumes:
      - ./config/redis/sentinel.conf:/etc/redis/sentinel.conf:ro
    networks: [infra-net, services-net]
    depends_on:
      - redis
      - redis-replica-1
      - redis-replica-2

  sentinel-3:
    image: redis:7.2-alpine
    container_name: sentinel-3
    command: >
      redis-sentinel /etc/redis/sentinel.conf
    volumes:
      - ./config/redis/sentinel.conf:/etc/redis/sentinel.conf:ro
    networks: [infra-net, services-net]
    depends_on:
      - redis
      - redis-replica-1
      - redis-replica-2

  # ============================================================
  # Eureka — second instance for peer awareness
  # ============================================================

  discovery-service-2:
    image: ${DISCOVERY_SERVICE_IMAGE:-ecommerce/discovery-service:latest}
    container_name: discovery-service-2
    ports:
      - "8762:8761"
    depends_on:
      config-service:
        condition: service_healthy
    environment:
      SPRING_CONFIG_IMPORT: optional:configserver:http://config-service:8888
      EUREKA_HOST: discovery-service-2
      EUREKA_USERNAME: ${EUREKA_USERNAME:-eureka}
      EUREKA_PASSWORD: ${EUREKA_PASSWORD:-eureka-secret-2024}
      EUREKA_PEER_URL: http://${EUREKA_USERNAME:-eureka}:${EUREKA_PASSWORD:-eureka-secret-2024}@discovery-service:8761/eureka/
      VAULT_ENABLED: ${VAULT_ENABLED:-true}
      VAULT_HOST: ${VAULT_HOST:-vault}
      VAULT_TOKEN: ${VAULT_TOKEN:-root-token}
    networks: [services-net, infra-net]
    healthcheck:
      test: ["CMD-SHELL", "curl -f http://localhost:8761/actuator/health || exit 1"]
      interval: 15s
      timeout: 10s
      retries: 8
    restart: on-failure

  # ============================================================
  # MongoDB Replica Set — 3 nodes
  # ============================================================

  mongodb:
    # Override single instance to be named mongo1
    container_name: mongo1
    command: mongod --replSet rs0 --bind_ip_all

  mongo2:
    image: mongo:7.0
    container_name: mongo2
    environment:
      MONGO_INITDB_ROOT_USERNAME: ${MONGO_USERNAME:-vanilson}
      MONGO_INITDB_ROOT_PASSWORD: ${MONGO_PASSWORD:-vanilson}
    command: mongod --replSet rs0 --bind_ip_all
    volumes:
      - mongodb2-data:/data/db
    networks: [infra-net]
    healthcheck:
      test: ["CMD", "mongosh", "--eval", "db.adminCommand('ping')"]
      interval: 10s
      timeout: 5s
      retries: 5

  mongo3:
    image: mongo:7.0
    container_name: mongo3
    environment:
      MONGO_INITDB_ROOT_USERNAME: ${MONGO_USERNAME:-vanilson}
      MONGO_INITDB_ROOT_PASSWORD: ${MONGO_PASSWORD:-vanilson}
    command: mongod --replSet rs0 --bind_ip_all
    volumes:
      - mongodb3-data:/data/db
    networks: [infra-net]
    healthcheck:
      test: ["CMD", "mongosh", "--eval", "db.adminCommand('ping')"]
      interval: 10s
      timeout: 5s
      retries: 5

  # Initialize replica set after all 3 nodes are healthy
  mongo-rs-init:
    image: mongo:7.0
    container_name: mongo-rs-init
    depends_on:
      mongodb:
        condition: service_healthy
      mongo2:
        condition: service_healthy
      mongo3:
        condition: service_healthy
    command: >
      mongosh --host mongo1:27017
        -u ${MONGO_USERNAME:-vanilson}
        -p ${MONGO_PASSWORD:-vanilson}
        --authenticationDatabase admin
        /scripts/mongo-replicaset-init.js
    volumes:
      - ./scripts/mongo-replicaset-init.js:/scripts/mongo-replicaset-init.js:ro
    networks: [infra-net]
    restart: "no"

volumes:
  kafka1-data:
  kafka2-data:
  kafka3-data:
  redis-replica1-data:
  redis-replica2-data:
  mongodb2-data:
  mongodb3-data:


```

### `docker-compose.prod.yml`

```

###############################################################
# docker-compose.prod.yml — Production hardening overlay
#
# Usage: docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d
#
# Changes from dev compose:
#   - Removes all exposed ports (except gateway:8222 and grafana:3000)
#   - Adds resource limits per container type
#   - Adds log rotation (json-file, max 10m, 3 files)
#   - Sets restart: unless-stopped for all services
#   - Adds read_only + tmpfs for stateless app containers
#   - Removes MailHog (use real SMTP in prod)
###############################################################

services:

  # ============================================================
  # INFRASTRUCTURE — secrets, messaging, cache, databases
  # ============================================================

  vault:
    ports: !reset []        # remove 8200 exposure in prod
    restart: unless-stopped
    deploy:
      resources:
        limits:
          memory: 512m
          cpus: "0.5"
    logging:
      driver: json-file
      options:
        max-size: 10m
        max-file: "3"

  zookeeper:
    restart: unless-stopped
    deploy:
      resources:
        limits:
          memory: 512m
          cpus: "0.5"
    logging:
      driver: json-file
      options:
        max-size: 10m
        max-file: "3"

  kafka:
    ports: !reset []        # remove 9092 exposure; services use kafka:29092 on infra-net
    restart: unless-stopped
    deploy:
      resources:
        limits:
          memory: 2g
          cpus: "2.0"
    logging:
      driver: json-file
      options:
        max-size: 10m
        max-file: "3"

  redis:
    ports: !reset []        # remove 6379 exposure
    restart: unless-stopped
    deploy:
      resources:
        limits:
          memory: 512m
          cpus: "0.5"
    logging:
      driver: json-file
      options:
        max-size: 10m
        max-file: "3"

  postgres-order:
    restart: unless-stopped
    deploy:
      resources:
        limits:
          memory: 2g
          cpus: "2.0"
    logging:
      driver: json-file
      options:
        max-size: 10m
        max-file: "3"

  postgres-product:
    restart: unless-stopped
    deploy:
      resources:
        limits:
          memory: 2g
          cpus: "2.0"
    logging:
      driver: json-file
      options:
        max-size: 10m
        max-file: "3"

  postgres-payment:
    restart: unless-stopped
    deploy:
      resources:
        limits:
          memory: 2g
          cpus: "2.0"
    logging:
      driver: json-file
      options:
        max-size: 10m
        max-file: "3"

  postgres-auth:
    restart: unless-stopped
    deploy:
      resources:
        limits:
          memory: 2g
          cpus: "2.0"
    logging:
      driver: json-file
      options:
        max-size: 10m
        max-file: "3"

  mongodb:
    restart: unless-stopped
    deploy:
      resources:
        limits:
          memory: 2g
          cpus: "2.0"
    logging:
      driver: json-file
      options:
        max-size: 10m
        max-file: "3"

  # ============================================================
  # OBSERVABILITY
  # ============================================================

  zipkin:
    ports: !reset []        # remove 9411 — access through monitoring-net only
    restart: unless-stopped
    deploy:
      resources:
        limits:
          memory: 512m
          cpus: "0.5"
    logging:
      driver: json-file
      options:
        max-size: 10m
        max-file: "3"

  prometheus:
    ports: !reset []        # internal only in prod
    restart: unless-stopped
    deploy:
      resources:
        limits:
          memory: 1g
          cpus: "1.0"
    logging:
      driver: json-file
      options:
        max-size: 10m
        max-file: "3"

  alertmanager:
    ports: !reset []
    restart: unless-stopped
    deploy:
      resources:
        limits:
          memory: 256m
          cpus: "0.25"
    logging:
      driver: json-file
      options:
        max-size: 10m
        max-file: "3"

  loki:
    ports: !reset []
    restart: unless-stopped
    deploy:
      resources:
        limits:
          memory: 512m
          cpus: "0.5"
    logging:
      driver: json-file
      options:
        max-size: 10m
        max-file: "3"

  promtail:
    restart: unless-stopped
    deploy:
      resources:
        limits:
          memory: 256m
          cpus: "0.25"
    logging:
      driver: json-file
      options:
        max-size: 10m
        max-file: "3"

  grafana:
    # Keep :3000 for dashboards — accessible only through VPN/firewall rule in prod
    restart: unless-stopped
    deploy:
      resources:
        limits:
          memory: 512m
          cpus: "0.5"
    logging:
      driver: json-file
      options:
        max-size: 10m
        max-file: "3"

  # MailHog removed in production — use real SMTP (set MAIL_HOST, MAIL_PORT, MAIL_USERNAME, MAIL_PASSWORD)
  mailhog:
    deploy:
      replicas: 0

  # ============================================================
  # SPRING CLOUD INFRASTRUCTURE
  # ============================================================

  config-service:
    ports: !reset []        # internal only — services access via service name
    restart: unless-stopped
    read_only: true
    tmpfs:
      - /tmp
    deploy:
      resources:
        limits:
          memory: 1g
          cpus: "1.0"
    logging:
      driver: json-file
      options:
        max-size: 10m
        max-file: "3"

  discovery-service:
    ports: !reset []
    restart: unless-stopped
    read_only: true
    tmpfs:
      - /tmp
    deploy:
      resources:
        limits:
          memory: 1g
          cpus: "1.0"
    logging:
      driver: json-file
      options:
        max-size: 10m
        max-file: "3"

  # ============================================================
  # APPLICATION SERVICES — stateless, read_only + tmpfs
  # ============================================================

  gateway-api-service:
    # Keep :8222 — external entry point
    restart: unless-stopped
    read_only: true
    tmpfs:
      - /tmp
    deploy:
      resources:
        limits:
          memory: 1g
          cpus: "1.0"
    logging:
      driver: json-file
      options:
        max-size: 10m
        max-file: "3"

  authentication-service:
    ports: !reset []
    restart: unless-stopped
    read_only: true
    tmpfs:
      - /tmp
    deploy:
      resources:
        limits:
          memory: 1g
          cpus: "1.0"
    logging:
      driver: json-file
      options:
        max-size: 10m
        max-file: "3"

  customer-service:
    ports: !reset []
    restart: unless-stopped
    read_only: true
    tmpfs:
      - /tmp
    deploy:
      resources:
        limits:
          memory: 1g
          cpus: "1.0"
    logging:
      driver: json-file
      options:
        max-size: 10m
        max-file: "3"

  product-service:
    ports: !reset []
    restart: unless-stopped
    read_only: true
    tmpfs:
      - /tmp
    deploy:
      resources:
        limits:
          memory: 1g
          cpus: "1.0"
    logging:
      driver: json-file
      options:
        max-size: 10m
        max-file: "3"

  order-service:
    ports: !reset []
    restart: unless-stopped
    read_only: true
    tmpfs:
      - /tmp
    deploy:
      resources:
        limits:
          memory: 1g
          cpus: "1.0"
    logging:
      driver: json-file
      options:
        max-size: 10m
        max-file: "3"

  payment-service:
    ports: !reset []
    restart: unless-stopped
    read_only: true
    tmpfs:
      - /tmp
    deploy:
      resources:
        limits:
          memory: 1g
          cpus: "1.0"
    logging:
      driver: json-file
      options:
        max-size: 10m
        max-file: "3"

  cart-service:
    ports: !reset []
    restart: unless-stopped
    read_only: true
    tmpfs:
      - /tmp
    deploy:
      resources:
        limits:
          memory: 1g
          cpus: "1.0"
    logging:
      driver: json-file
      options:
        max-size: 10m
        max-file: "3"

  notification-service:
    ports: !reset []
    restart: unless-stopped
    read_only: true
    tmpfs:
      - /tmp
    deploy:
      resources:
        limits:
          memory: 1g
          cpus: "1.0"
    logging:
      driver: json-file
      options:
        max-size: 10m
        max-file: "3"


```

### `docker-compose.yml`

```

###############################################################
# docker-compose.yml — SaaS eCommerce Platform (Phase 2)
# Services: 10 Spring Boot apps + full infrastructure
#
# Ports (external access):
#   8888 config-service    8761 discovery-service
#   8222 gateway           8085 authentication-service
#   8090 customer-service  8082 product-service
#   8083 order-service     8086 payment-service
#   8091 cart-service      8040 notification-service
#
# Infrastructure (internal only — no external ports):
#   Kafka, Zookeeper, Redis, PostgreSQL x4, MongoDB, Vault
#
# Observability:
#   9090 Prometheus  3000 Grafana  9411 Zipkin  8025 MailHog
#   8200 Vault UI (dev only — remove in production)
#
# Networks:
#   infra-net     → databases, kafka, redis, vault (internal)
#   services-net  → Spring Boot services + config + discovery
#   monitoring-net→ prometheus, grafana, zipkin
###############################################################

services:

  # ============================================================
  # INFRASTRUCTURE — Secrets Management
  # ============================================================

  vault:
    image: hashicorp/vault:1.16
    container_name: vault
    ports:
      - "8200:8200"   # UI + API — restrict access in production
    environment:
      VAULT_DEV_ROOT_TOKEN_ID: ${VAULT_TOKEN:-root-token}
      VAULT_DEV_LISTEN_ADDRESS: 0.0.0.0:8200
    cap_add:
      - IPC_LOCK
    volumes:
      - vault-data:/vault/data
      - ./vault/config:/vault/config:ro
      - ./vault/policies:/vault/policies:ro
    command: [ "vault", "server", "-dev" ]
    # Production: use -config=/vault/config/vault.hcl instead of -dev
    networks: [ infra-net, services-net ]
    healthcheck:
      test: [ "CMD-SHELL", "vault status 2>/dev/null | grep -q 'Initialized'" ]
      interval: 10s
      timeout: 5s
      retries: 5

  # ============================================================
  # INFRASTRUCTURE — Message Bus
  # ============================================================

  zookeeper:
    image: confluentinc/cp-zookeeper:7.6.1
    container_name: zookeeper
    # No external port — internal only
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
      ZOOKEEPER_TICK_TIME: 2000
    networks: [ infra-net ]
    healthcheck:
      test: [ "CMD", "nc", "-z", "localhost", "2181" ]
      interval: 10s
      timeout: 5s
      retries: 5

  kafka:
    image: confluentinc/cp-kafka:7.6.1
    container_name: kafka
    depends_on:
      zookeeper:
        condition: service_healthy
    # Port 9092 exposed for local dev tools (Kafka UI, kafka-cli)
    # Remove in production — services communicate via infra-net on port 29092
    ports:
      - "9092:9092"
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      # SASL/PLAIN listeners — PLAINTEXT_HOST for local dev, SASL_PLAINTEXT for internal
      KAFKA_ADVERTISED_LISTENERS: SASL_PLAINTEXT://kafka:29092,PLAINTEXT_HOST://localhost:9092
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: SASL_PLAINTEXT:SASL_PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT
      KAFKA_INTER_BROKER_LISTENER_NAME: SASL_PLAINTEXT
      KAFKA_SASL_ENABLED_MECHANISMS: PLAIN
      KAFKA_SASL_MECHANISM_INTER_BROKER_PROTOCOL: PLAIN
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 1
      KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 1
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"
      KAFKA_ENABLE_IDEMPOTENCE: "true"
      KAFKA_JMX_PORT: 9101
      KAFKA_JMX_HOSTNAME: localhost
      KAFKA_OPTS: "-Djava.security.auth.login.config=/etc/kafka/jaas/kafka_server_jaas.conf"
    volumes:
      - ./kafka/jaas:/etc/kafka/jaas:ro
    networks: [ infra-net ]
    healthcheck:
      test: [ "CMD", "kafka-broker-api-versions", "--bootstrap-server", "localhost:9092" ]
      interval: 15s
      timeout: 10s
      retries: 8

  # ============================================================
  # INFRASTRUCTURE — Cache + Rate Limiting
  # ============================================================

  redis:
    image: redis:7.2-alpine
    container_name: redis
    # No external port in production — internal only
    # Keeping for local dev access; remove port binding in production
    ports:
      - "6379:6379"
    command: >
      redis-server
      --maxmemory 512mb
      --maxmemory-policy allkeys-lru
      --save 60 1
      --loglevel warning
      --requirepass ${REDIS_PASSWORD:-redis-secret-2024}
    volumes:
      - redis-data:/data
    networks: [ infra-net, services-net ]
    healthcheck:
      test: [ "CMD", "redis-cli", "-a", "${REDIS_PASSWORD:-redis-secret-2024}", "ping" ]
      interval: 10s
      timeout: 5s
      retries: 5

  # ============================================================
  # INFRASTRUCTURE — Databases (one per service, internal only)
  # ============================================================

  postgres-order:
    image: postgres:15-alpine
    container_name: postgres-order
    environment:
      POSTGRES_DB: order_service_db
      POSTGRES_USER: ${POSTGRES_USERNAME:-vanilson}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-vanilson}
    # No external port — internal only. Access via: docker exec postgres-order psql
    volumes:
      - postgres-order-data:/var/lib/postgresql/data
    networks: [ infra-net ]
    healthcheck:
      test: [ "CMD-SHELL", "pg_isready -U ${POSTGRES_USERNAME:-vanilson} -d order_service_db" ]
      interval: 10s
      timeout: 5s
      retries: 5

  postgres-product:
    image: postgres:15-alpine
    container_name: postgres-product
    environment:
      POSTGRES_DB: product_service_db
      POSTGRES_USER: ${POSTGRES_USERNAME:-vanilson}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-vanilson}
    volumes:
      - postgres-product-data:/var/lib/postgresql/data
    networks: [ infra-net ]
    healthcheck:
      test: [ "CMD-SHELL", "pg_isready -U ${POSTGRES_USERNAME:-vanilson} -d product_service_db" ]
      interval: 10s
      timeout: 5s
      retries: 5

  postgres-payment:
    image: postgres:15-alpine
    container_name: postgres-payment
    environment:
      POSTGRES_DB: payment_db
      POSTGRES_USER: ${POSTGRES_USERNAME:-vanilson}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-vanilson}
    volumes:
      - postgres-payment-data:/var/lib/postgresql/data
    networks: [ infra-net ]
    healthcheck:
      test: [ "CMD-SHELL", "pg_isready -U ${POSTGRES_USERNAME:-vanilson} -d payment_db" ]
      interval: 10s
      timeout: 5s
      retries: 5

  postgres-auth:
    image: postgres:15-alpine
    container_name: postgres-auth
    environment:
      POSTGRES_DB: auth_db
      POSTGRES_USER: ${POSTGRES_USERNAME:-vanilson}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-vanilson}
    volumes:
      - postgres-auth-data:/var/lib/postgresql/data
    networks: [ infra-net ]
    healthcheck:
      test: [ "CMD-SHELL", "pg_isready -U ${POSTGRES_USERNAME:-vanilson} -d auth_db" ]
      interval: 10s
      timeout: 5s
      retries: 5

  mongodb:
    image: mongo:7.0
    container_name: mongodb
    # No external port — internal only
    environment:
      MONGO_INITDB_ROOT_USERNAME: ${MONGO_USERNAME:-vanilson}
      MONGO_INITDB_ROOT_PASSWORD: ${MONGO_PASSWORD:-vanilson}
    volumes:
      - mongodb-data:/data/db
      - ./mongo-init.js:/docker-entrypoint-initdb.d/mongo-init.js:ro
    networks: [ infra-net ]
    healthcheck:
      test: [ "CMD", "mongosh", "--eval", "db.adminCommand('ping')" ]
      interval: 10s
      timeout: 5s
      retries: 5

  # ============================================================
  # INFRASTRUCTURE — Observability
  # ============================================================

  zipkin:
    image: openzipkin/zipkin:3
    container_name: zipkin
    ports:
      - "9411:9411"
    volumes:
      - zipkin-data:/zipkin
    networks: [ monitoring-net, services-net ]
    healthcheck:
      test: [ "CMD-SHELL", "curl -f http://localhost:9411/health || exit 1" ]
      interval: 10s
      timeout: 5s
      retries: 5

  prometheus:
    image: prom/prometheus:v2.51.2
    container_name: prometheus
    ports:
      - "9090:9090"
    volumes:
      - ./config-service/src/main/resources/prometheus/prometheus.yml:/etc/prometheus/prometheus.yml:ro
      - ./config-service/src/main/resources/prometheus/alert.rules.yml:/etc/prometheus/alert.rules.yml:ro
      - prometheus-data:/prometheus
    command:
      - "--config.file=/etc/prometheus/prometheus.yml"
      - "--storage.tsdb.path=/prometheus"
      - "--storage.tsdb.retention.time=15d"
      - "--web.enable-lifecycle"
    networks: [ monitoring-net, services-net ]
    healthcheck:
      test: [ "CMD-SHELL", "wget -qO- http://localhost:9090/-/healthy | grep -q Prometheus" ]
      interval: 15s
      timeout: 5s
      retries: 5

  alertmanager:
    image: prom/alertmanager:v0.27.0
    container_name: alertmanager
    ports:
      - "9093:9093"
    volumes:
      - ./alertmanager/alertmanager.yml:/etc/alertmanager/alertmanager.yml:ro
      - alertmanager-data:/alertmanager
    command:
      - "--config.file=/etc/alertmanager/alertmanager.yml"
      - "--storage.path=/alertmanager"
    networks: [ monitoring-net, services-net ]
    healthcheck:
      test: [ "CMD-SHELL", "wget -qO- http://localhost:9093/-/healthy || exit 1" ]
      interval: 15s
      timeout: 5s
      retries: 5

  loki:
    image: grafana/loki:2.9.8
    container_name: loki
    ports:
      - "3100:3100"
    volumes:
      - ./loki/loki-config.yml:/etc/loki/loki-config.yml:ro
      - loki-data:/tmp/loki
    command: -config.file=/etc/loki/loki-config.yml
    networks: [ monitoring-net, services-net ]
    healthcheck:
      test: [ "CMD-SHELL", "wget -qO- http://localhost:3100/ready || exit 1" ]
      interval: 15s
      timeout: 5s
      retries: 5

  promtail:
    image: grafana/promtail:2.9.8
    container_name: promtail
    volumes:
      - ./promtail/promtail-config.yml:/etc/promtail/promtail-config.yml:ro
      - /var/lib/docker/containers:/var/lib/docker/containers:ro
      - /var/run/docker.sock:/var/run/docker.sock:ro
    command: -config.file=/etc/promtail/promtail-config.yml
    depends_on:
      - loki
    networks: [ monitoring-net, services-net ]

  grafana:
    image: grafana/grafana:10.4.2
    container_name: grafana
    ports:
      - "3000:3000"
    environment:
      GF_SECURITY_ADMIN_USER: ${GRAFANA_USER:-admin}
      GF_SECURITY_ADMIN_PASSWORD: ${GRAFANA_PASSWORD:-Gr@fana#Secure2024}
      GF_USERS_ALLOW_SIGN_UP: "false"
      GF_AUTH_ANONYMOUS_ENABLED: "false"
      GF_SECURITY_DISABLE_GRAVATAR: "true"
      GF_PATHS_PROVISIONING: /etc/grafana/provisioning
    volumes:
      - grafana-data:/var/lib/grafana
      - ./grafana/provisioning:/etc/grafana/provisioning:ro
    depends_on:
      - prometheus
      - loki
    networks: [ monitoring-net ]

  mailhog:
    image: mailhog/mailhog:v1.0.1
    container_name: mailhog
    ports:
      - "1025:1025"
      - "8025:8025"
    networks: [ services-net ]

  # ============================================================
  # SPRING CLOUD INFRASTRUCTURE
  # ============================================================

  config-service:
    build:
      context: ./config-service
      dockerfile: Dockerfile
    container_name: config-service
    ports:
      - "8888:8888"
    environment:
      SPRING_PROFILES_ACTIVE: native
      VAULT_ENABLED: ${VAULT_ENABLED:-true}
      VAULT_HOST: ${VAULT_HOST:-vault}
      VAULT_PORT: ${VAULT_PORT:-8200}
      VAULT_TOKEN: ${VAULT_TOKEN:-root-token}
    networks: [ services-net, infra-net ]
    healthcheck:
      test: [ "CMD-SHELL", "curl -f http://localhost:8888/actuator/health || exit 1" ]
      interval: 15s
      timeout: 10s
      retries: 8
    restart: on-failure

  discovery-service:
    build:
      context: ./discovery-service
      dockerfile: Dockerfile
    container_name: discovery-service
    ports:
      - "8761:8761"
    depends_on:
      config-service:
        condition: service_healthy
    environment:
      SPRING_CONFIG_IMPORT: optional:configserver:http://config-service:8888
      EUREKA_HOST: discovery-service
      EUREKA_USERNAME: ${EUREKA_USERNAME:-eureka}
      EUREKA_PASSWORD: ${EUREKA_PASSWORD:-eureka-secret-2024}
      VAULT_ENABLED: ${VAULT_ENABLED:-true}
      VAULT_HOST: ${VAULT_HOST:-vault}
      VAULT_TOKEN: ${VAULT_TOKEN:-root-token}
    networks: [ services-net, infra-net ]
    healthcheck:
      test: [ "CMD-SHELL", "curl -f http://localhost:8761/actuator/health || exit 1" ]
      interval: 15s
      timeout: 10s
      retries: 8
    restart: on-failure

  # ============================================================
  # APPLICATION SERVICES
  # ============================================================

  gateway-api-service:
    build:
      context: ./gateway-api-service
      dockerfile: Dockerfile
    container_name: gateway-api-service
    ports:
      - "8222:8222"
    depends_on:
      config-service:
        condition: service_healthy
      discovery-service:
        condition: service_healthy
      redis:
        condition: service_healthy
    environment:
      SPRING_CONFIG_IMPORT: optional:configserver:http://config-service:8888
      EUREKA_HOST: discovery-service
      EUREKA_USERNAME: ${EUREKA_USERNAME:-eureka}
      EUREKA_PASSWORD: ${EUREKA_PASSWORD:-eureka-secret-2024}
      REDIS_HOST: redis
      REDIS_PORT: 6379
      REDIS_PASSWORD: ${REDIS_PASSWORD:-redis-secret-2024}
      JWT_SECRET: ${JWT_SECRET:-bXlTdXBlclNlY3VyZVNlY3JldEtleUZvckpXVEF1dGgxMjM0NTY3ODk=}
      GATEWAY_MAX_CONCURRENT: 5000
      VAULT_ENABLED: ${VAULT_ENABLED:-true}
      VAULT_HOST: ${VAULT_HOST:-vault}
      VAULT_TOKEN: ${VAULT_TOKEN:-root-token}
    networks: [ services-net, infra-net ]
    healthcheck:
      test: [ "CMD-SHELL", "curl -f http://localhost:8222/actuator/health || exit 1" ]
      interval: 15s
      timeout: 10s
      retries: 8
    restart: on-failure

  frontend:
    build:
      context: ./frontend
      dockerfile: Dockerfile
    container_name: frontend
    ports:
      - "80:80"
    depends_on:
      gateway-api-service:
        condition: service_healthy
    networks: [ services-net ]
    restart: unless-stopped

  authentication-service:
    build:
      context: ./authentication-service
      dockerfile: Dockerfile
    container_name: authentication-service
    ports:
      - "8085:8085"
    depends_on:
      config-service:
        condition: service_healthy
      postgres-auth:
        condition: service_healthy
      discovery-service:
        condition: service_healthy
    environment:
      SPRING_CONFIG_IMPORT: optional:configserver:http://config-service:8888
      EUREKA_HOST: discovery-service
      EUREKA_USERNAME: ${EUREKA_USERNAME:-eureka}
      EUREKA_PASSWORD: ${EUREKA_PASSWORD:-eureka-secret-2024}
      POSTGRES_HOST: postgres-auth
      POSTGRES_USERNAME: ${POSTGRES_USERNAME:-vanilson}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-vanilson}
      JWT_SECRET: ${JWT_SECRET:-bXlTdXBlclNlY3VyZVNlY3JldEtleUZvckpXVEF1dGgxMjM0NTY3ODk=}
      VAULT_ENABLED: ${VAULT_ENABLED:-true}
      VAULT_HOST: ${VAULT_HOST:-vault}
      VAULT_TOKEN: ${VAULT_TOKEN:-root-token}
    networks: [ services-net, infra-net ]
    healthcheck:
      test: [ "CMD-SHELL", "curl -f http://localhost:8085/actuator/health || exit 1" ]
      interval: 15s
      timeout: 10s
      retries: 8
    restart: on-failure

  customer-service:
    build:
      context: ./customer-service
      dockerfile: Dockerfile
    container_name: customer-service
    ports:
      - "8090:8090"
    depends_on:
      config-service:
        condition: service_healthy
      mongodb:
        condition: service_healthy
      redis:
        condition: service_healthy
    environment:
      SPRING_CONFIG_IMPORT: optional:configserver:http://config-service:8888
      EUREKA_HOST: discovery-service
      EUREKA_USERNAME: ${EUREKA_USERNAME:-eureka}
      EUREKA_PASSWORD: ${EUREKA_PASSWORD:-eureka-secret-2024}
      MONGO_HOST: mongodb
      REDIS_HOST: redis
      REDIS_PASSWORD: ${REDIS_PASSWORD:-redis-secret-2024}
      VAULT_ENABLED: ${VAULT_ENABLED:-true}
      VAULT_HOST: ${VAULT_HOST:-vault}
      VAULT_TOKEN: ${VAULT_TOKEN:-root-token}
    networks: [ services-net, infra-net ]
    healthcheck:
      test: [ "CMD-SHELL", "curl -f http://localhost:8090/actuator/health || exit 1" ]
      interval: 15s
      timeout: 10s
      retries: 8
    restart: on-failure

  product-service:
    build:
      context: ./product-service
      dockerfile: Dockerfile
    container_name: product-service
    ports:
      - "8082:8082"
    depends_on:
      config-service:
        condition: service_healthy
      postgres-product:
        condition: service_healthy
      redis:
        condition: service_healthy
    environment:
      SPRING_CONFIG_IMPORT: optional:configserver:http://config-service:8888
      EUREKA_HOST: discovery-service
      EUREKA_USERNAME: ${EUREKA_USERNAME:-eureka}
      EUREKA_PASSWORD: ${EUREKA_PASSWORD:-eureka-secret-2024}
      POSTGRES_HOST: postgres-product
      POSTGRES_USERNAME: ${POSTGRES_USERNAME:-vanilson}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-vanilson}
      REDIS_HOST: redis
      REDIS_PASSWORD: ${REDIS_PASSWORD:-redis-secret-2024}
      KAFKA_BOOTSTRAP_SERVERS: kafka:29092
      KAFKA_USERNAME: ${KAFKA_USERNAME:-kafka}
      KAFKA_PASSWORD: ${KAFKA_PASSWORD:-kafka-secret-2024}
      VAULT_ENABLED: ${VAULT_ENABLED:-true}
      VAULT_HOST: ${VAULT_HOST:-vault}
      VAULT_TOKEN: ${VAULT_TOKEN:-root-token}
    networks: [ services-net, infra-net ]
    healthcheck:
      test: [ "CMD-SHELL", "curl -f http://localhost:8082/actuator/health || exit 1" ]
      interval: 15s
      timeout: 10s
      retries: 8
    restart: on-failure

  order-service:
    build:
      context: ./order-service
      dockerfile: Dockerfile
    container_name: order-service
    ports:
      - "8083:8083"
    depends_on:
      config-service:
        condition: service_healthy
      postgres-order:
        condition: service_healthy
      kafka:
        condition: service_healthy
    environment:
      SPRING_CONFIG_IMPORT: optional:configserver:http://config-service:8888
      EUREKA_HOST: discovery-service
      EUREKA_USERNAME: ${EUREKA_USERNAME:-eureka}
      EUREKA_PASSWORD: ${EUREKA_PASSWORD:-eureka-secret-2024}
      POSTGRES_HOST: postgres-order
      POSTGRES_USERNAME: ${POSTGRES_USERNAME:-vanilson}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-vanilson}
      KAFKA_BOOTSTRAP_SERVERS: kafka:29092
      KAFKA_USERNAME: ${KAFKA_USERNAME:-kafka}
      KAFKA_PASSWORD: ${KAFKA_PASSWORD:-kafka-secret-2024}
      GATEWAY_HOST: gateway-api-service
      VAULT_ENABLED: ${VAULT_ENABLED:-true}
      VAULT_HOST: ${VAULT_HOST:-vault}
      VAULT_TOKEN: ${VAULT_TOKEN:-root-token}
    networks: [ services-net, infra-net ]
    healthcheck:
      test: [ "CMD-SHELL", "curl -f http://localhost:8083/actuator/health || exit 1" ]
      interval: 15s
      timeout: 10s
      retries: 8
    restart: on-failure

  payment-service:
    build:
      context: ./payment-service
      dockerfile: Dockerfile
    container_name: payment-service
    ports:
      - "8086:8086"
    depends_on:
      config-service:
        condition: service_healthy
      postgres-payment:
        condition: service_healthy
      kafka:
        condition: service_healthy
    environment:
      SPRING_CONFIG_IMPORT: optional:configserver:http://config-service:8888
      EUREKA_HOST: discovery-service
      EUREKA_USERNAME: ${EUREKA_USERNAME:-eureka}
      EUREKA_PASSWORD: ${EUREKA_PASSWORD:-eureka-secret-2024}
      POSTGRES_HOST: postgres-payment
      POSTGRES_USERNAME: ${POSTGRES_USERNAME:-vanilson}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-vanilson}
      KAFKA_BOOTSTRAP_SERVERS: kafka:29092
      KAFKA_USERNAME: ${KAFKA_USERNAME:-kafka}
      KAFKA_PASSWORD: ${KAFKA_PASSWORD:-kafka-secret-2024}
      VAULT_ENABLED: ${VAULT_ENABLED:-true}
      VAULT_HOST: ${VAULT_HOST:-vault}
...[truncated]

```

### `pom.xml`

```

<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.5</version>
    </parent>

    <groupId>code.with.vanilson</groupId>
    <artifactId>e-commerce-microservice</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <packaging>pom</packaging>

    <modules>
        <!-- Phase 4: SaaS Multi-Tenancy — build first, all services depend on tenant-context -->
        <module>tenant-context</module>
        <module>tenant-service</module>
        <!-- Discovery and Configuration services — start before other services -->
        <module>discovery-service</module>
        <module>config-service</module>
        <!-- Application services -->
        <module>customer-service</module>
        <module>order-service</module>
        <module>product-service</module>
        <module>payment-service</module>
        <module>authentication-service</module>
        <module>cart-service</module>
        <module>notification-service</module>
        <module>gateway-api-service</module>
    </modules>

    <properties>
        <java.version>17</java.version>
        <spring-cloud.version>2023.0.1</spring-cloud.version>
        <springdoc-openapi.version>2.3.0</springdoc-openapi.version>
        <jjwt.version>0.12.5</jjwt.version>
        <mockito.version>5.12.0</mockito.version>
        <spring-cloud-vault.version>4.1.1</spring-cloud-vault.version>
        <logstash-logback-encoder.version>7.4</logstash-logback-encoder.version>
    </properties>

    <dependencies>

        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
    </dependencies>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.cloud</groupId>
                <artifactId>spring-cloud-dependencies</artifactId>
                <version>${spring-cloud.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
            <dependency>
                <groupId>org.springframework.cloud</groupId>
                <artifactId>spring-cloud-vault-config</artifactId>
                <version>${spring-cloud-vault.version}</version>
            </dependency>
            <dependency>
                <groupId>net.logstash.logback</groupId>
                <artifactId>logstash-logback-encoder</artifactId>
                <version>${logstash-logback-encoder.version}</version>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <executions>
                    <execution>
                        <id>repackage</id>
                        <goals>
                            <goal>repackage</goal>
                        </goals>
                        <configuration>
                            <skip>true</skip>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
            <!-- Maven Compiler Plugin -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.11.0</version>
                <configuration>
                    <source>17</source>
                    <target>17</target>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.3.0</version>
                <configuration>
                    <excludes>
                        <exclude>**/*IntegrationTest.java</exclude>
                    </excludes>
                    <properties>
                        <configurationParameters>
                            cucumber.junit-platform.naming-strategy=long
                        </configurationParameters>
                    </properties>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-failsafe-plugin</artifactId>
                <version>3.2.5</version>
                <executions>
                    <execution>
                        <goals>
                            <goal>integration-test</goal>
                            <goal>verify</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>


```

**Overall System Design (Microservices Architecture)**
The system is fundamentally organized as a distributed, 12-node Spring Boot Microservice cluster architected around Domain-Driven Design (DDD). It utilizes a Choreography Saga pattern to handle distributed transactions where services react to domain events rather than relying on a central orchestrator. Complete multi-tenant isolation is supported at the thread and database level via a shared `tenant-context` library.

**Service-to-Service Communication**
- **Synchronous**: Executed strictly over HTTP using Spring Cloud OpenFeign. Used predominantly for cross-boundary queries that require immediate consistency (e.g., Gateway querying the Tenant service to validate an active `X-Tenant-ID`).
- **Asynchronous**: Executed using Apache Kafka topics. Required for all state-mutating transactional flows across domains to guarantee eventual consistency and fault tolerance through retry backoffs.

**API Gateway**
Provides the single ingress point on port `8222` utilizing Spring Cloud Gateway. The gateway implements an ordered filter chain:
1. `TenantValidationFilter` (Validates header existence and queries Tenant Service)
2. `JwtAuthenticationFilter` (Verifies JWT payload without DB lookup)
3. `LoadSheddingFilter` (Drops connections based on thread/queue thresholds)
4. `RequestIdFilter` (Appends distributed tracing UUIDs)

**Infrastructure Patterns**
- **Service Discovery**: Netflix Eureka.
- **Circuit Breaking**: Resilience4J limits cascading failures from upstream Feign clients.
- **Secret Management**: HashiCorp Vault.
- **Log Aggregation & Tracing**: Zipkin, Loki, and Promtail.

---

## 2. MICROSERVICES 

### 2.1 gateway-api-service
- **Purpose**: Global border boundary, ingress routing, authentication offloading, rate limiting, and tenant restriction.
- **Internal Structure**: Employs Reactive programming (WebFlux) with custom GlobalFilters (`JwtAuthenticationFilter`, `TenantValidationFilter`, `LoadSheddingFilter`).
- **Endpoints and Contracts**: Implicit proxying based on YAML patterns (e.g., `/api/v1/orders/**` routes to `order-service`).
- **Data Models and Database**: NOT FOUND (Stateless proxy).
- **Dependencies**: Spring Cloud Gateway, Resilience4j, Eureka Client.
- **Inter-service Communication**: FeignClient to `tenant-service` for validation.

### 2.2 order-service
- **Purpose**: Central node for order placements and instigating the e-commerce purchase saga.
- **Internal Structure**: Controller, Service, Repository, Outbox, and Kafka Consumer layers.
- **Endpoints**: `POST /api/v1/orders` (Async Accepted 202), `GET /api/v1/orders/status/{correlationId}` (Polling), `GET /api/v1/orders`.
- **Data Models**: `Order` (relational), `OrderLine`, `OutboxEvent` (for transactional outbox pattern). Database: PostgreSQL (`order_db`).
- **Dependencies**: Spring Data JPA, Kafka.
- **Inter-service Communication**:
  - Outbound (Async): Produces `order.requested` via OutboxEventPublisher.
  - Inbound (Async): Consumes `payment.authorized`, `payment.failed`, `inventory.insufficient` via `OrderSagaConsumer` to update aggregate state.

### 2.3 product-service
- **Purpose**: Manages global catalog inventory and stock reservation logic.
- **Internal Structure**: Layers managing the Catalog and Inventory.
- **Endpoints**: Standard CRUD endpoints for products (`/api/v1/products`).
- **Data Models**: Relational `Product` entity. Pessimistic locking strategies are utilized for inventory columns. Database: PostgreSQL (`product_db`) + Redis Cache.
- **Dependencies**: Spring Data JPA, Spring Data Redis, Kafka.
- **Inter-service Communication**: Consumes `order.requested` -> Reserves stock -> Produces `inventory.reserved` or `inventory.insufficient`.

### 2.4 payment-service
- **Purpose**: Settles financial records based on external processor mocked logic.
- **Internal Structure**: Employs an exact `orderReference` idempotency cache to prevent double-charging.
- **Endpoints**: Standard API controllers for Payment processing (`/api/v1/payments`).
- **Data Models**: Relational `Payment` elements. Database: PostgreSQL (`payment_db`).
- **Dependencies**: Spring Data JPA, Kafka.
- **Inter-service Communication**: Consumes `inventory.reserved` -> Posts `payment.authorized` or `payment.failed`.

### 2.5 cart-service
- **Purpose**: Handles highly volatile user cart staging data before checkout.
- **Internal Structure**: Pure Key-Value operations handling TTLs (24h).
- **Endpoints**: CRUD against `/api/v1/carts`.
- **Data Models**: Flat Hash structures. Database: Redis Sentinel.
- **Inter-service Communication**: NOT FOUND for Kafka. Communicates restfully with Frontend.

### 2.6 customer-service
- **Purpose**: Maintains User Profiles and Shipping Addresses.
- **Internal Structure**: Document mapping layer.
- **Endpoints**: Standard CRUD REST endpoints on `/api/v1/customers`.
- **Data Models**: Document based profile. Database: MongoDB Replica Set.
- **Dependencies**: Spring Data MongoDB, Redis cache for L2 lookups.
- **Inter-service Communication**: FeignClient targets for `order-service` to validate Identity attributes.

### 2.7 notification-service
- **Purpose**: Dispatches transactional alert emails based on system state changes. Also functions as the Dead Letter Queue monitor.
- **Internal Structure**: Driven exclusively by `NotificationsConsumer.java` and `DlqConsumer.java`.
- **Endpoints**: NOT FOUND (Strictly Event-Driven).
- **Data Models**: `ProcessedEvent` (Idempotency storage), `DlqEvent` (Error trace storage). Database: MongoDB.
- **Dependencies**: Spring Data MongoDB, JavaMailSender.
- **Inter-service Communication**: Consumes `order-topic`, `payment-topic`, `*.DLQ` topics. Connects externally to SMTP/MailHog.

### 2.8 authentication-service
- **Purpose**: Manges identities, RBAC credentials, outputs cryptographically signed JSON Web Tokens (RS256/HS256).
- **Internal Structure**: Standard MVC wrapped around Spring Security Provider managers.
- **Endpoints**: `/api/v1/auth/login`, `/api/v1/auth/register`.
- **Data Models**: `User`, `Role`. Database: PostgreSQL (`auth_db`).
- **Dependencies**: Spring Security, JJWT.

### 2.9 tenant-service
- **Purpose**: Enforces SaaS restrictions; controls lifecycle environments (Free/Premium plans) per organization.
- **Internal Structure**: Maps API Key mappings to database bounds.
- **Endpoints**: Internal API surface for Gateway filtering.
- **Data Models**: `Tenant`, `Subscription`. Database: PostgreSQL.

### 2.10 config-service & discovery-service
- **config-service**: Bound to Port 8888. Houses environment parameters, connects to Vault to dynamically push secrets into contexts over Spring Cloud bus.
- **discovery-service**: Port 8761. Provides Eureka Heartbeat tracking.

---

## 3. FRONTEND

**Structure and Architecture**
- Framed in **React 19** using **TypeScript** and built strictly using **Vite**.
- Component directory isolates dumb Views from Smart Pages (`/pages` vs `/components`).
- Styling is predominantly handled using TailwindCSS augmented by Material-UI (MUI) components and framer-motion for micro-animations.

**How it interacts with each microservice**
- Direct browser calls reach only the `gateway-api-service`.
- Proxies `X-Tenant-ID` natively from standard `localStorage`/Zustand.

**State Management and API Consumption**
- **Zustand** is utilized as the global state machine (e.g., `useCartStore`, `useAuthStore`) instead of Redux.
- **Tanstack React Query** coupled with **Axios** handles remote procedure API consumption. Forms are structured using `react-hook-form` executing client-side validation rules enforced by `Zod` schemas.

---

## 4. DATA FLOW

**End-to-End Request Flow (Order Pipeline Example)**
1. Frontend executes an `Axios` POST to `gateway-api-service:8222/api/v1/orders`.
2. Gateway verifies Identity and routes the TCP stream to `order-service:8083`.
3. `order-service` writes the order payload to PostgreSQL (`REQUESTED` status) alongside an `OutboxEvent` row.
4. `order-service` returns an HTTP 202 Async Accepted containing a polling `correlationId`.
5. Background thread in `order-service` reads the Outbox row and throws an `order.requested` Kafka topic event.
6. `product-service` consumes event -> checks DB -> pessimistic locks inventory -> throws `inventory.reserved` event.
7. `payment-service` consumes event -> executes logical mock settlement -> throws `payment.authorized` event.
8. `order-service` and `notification-service` simultaneously receive the final event. Order updates database to `CONFIRMED`. Notification sends an email to the user.
9. Frontend, continuously querying the GET poll endpoint, sees Status transition from `REQUESTED` to `CONFIRMED` and breaks the UI loading loop.

**Authentication and Authorization Flow**
1. User supplies Username/Password to `/api/v1/auth/login`.
2. `authentication-service` verifies hashes against PostgreSQL. Packages User ID, Tenant ID, and Roles (USER/SELLER/ADMIN) into a JWT. Cryptographically signs it utilizing a Private RSA key stored in HashiCorp Vault.
3. User includes this JWT internally into an `Authorization: Bearer` header on subsequent operations.
4. Incoming Gateway request hits `JwtAuthenticationFilter`. Decrypts using Public RSA Key (Validating Signature without requiring a database check).
5. Gateway strips the JWT, unpacks the text payloads into standard headers (`X-User-ID`, `X-User-Role`) allowing internal Microservices inside the private subnet to avoid repeated security context validation.

---

## 5. INFRASTRUCTURE & CONFIG

**Environment Variables**
Managed via distributed `.properties`/`.yml` pulled natively out of Vault mapping variables like:
- `JWT_PUBLIC_KEY`, `VAULT_TOKEN`, DB bounds (`POSTGRES_USERNAME`, `MONGO_PASSWORD`), Cluster Keys (`KAFKA_BOOTSTRAP_SERVERS`).

**Deployment Configs**
- **Docker Compose**: Holds three operational states (`docker-compose.yml`, `docker-compose.ha.yml` for high-availability Kafka Kraft setups, `docker-compose.prod.yml` for hardened stateless resource mounts).
- **Helm**: Parameterizes generic K8s yaml structures (found in `/helm/ecommerce`). Environment matrices (`values-staging.yaml`, `values-production.yaml`).
- **Kubernetes (K8s) directory**: Raw YAMLs. Explicit use of network isolation (`default-deny.yml`, `database-policy.yml`) and `Istio` strict `mTLS` mesh communication models. HPA deployed matching memory utilization.

**External Services**
- **Relational Databases (PostgreSQL)**: Segregated databases across `order_db`, `product_db`, `payment_db`, `auth_db` forcing complete strict isolation (No JOINs). 
- **Document Store (MongoDB)**: Exists as a Replica Set (`rs0`) for high availability of large-structured items (`customer-service` profiles, `notification-service` idempotency).
- **In-Memory Store (Redis)**: Runs in Sentinel logic (Master/Replica) serving as high-speed Cache endpoints for the `cart-service`.
- **Event Streaming (Kafka)**: Utilizes Kafka in KRaft mode (no Zookeeper) as the core distributed queue engine.
- **Observability Stack**: Prometheus (Timeseries scraping), Grafana (Dashboard graphs mapping Spring Boot Micrometer outputs), Zipkin (UUID Distributed traces mapped between Feign requests), Loki/Promtail (syslog aggregation framework).
