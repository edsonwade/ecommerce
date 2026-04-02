# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

```bash
# Build all modules (skip tests)
mvn clean package -DskipTests

# Build a single service
mvn clean package -DskipTests -pl order-service

# Run all tests
mvn test

# Run tests for a single module
mvn test -pl order-service

# Run a single test class
mvn test -pl order-service -Dtest=OrderServiceAsyncTest

# Run a single test method
mvn test -pl order-service -Dtest=OrderServiceAsyncTest#methodName

# Start full stack (all services + infrastructure)
docker-compose up -d

# Start only infrastructure (DBs, Kafka, Redis, etc.)
docker-compose up -d zookeeper kafka redis postgres-order postgres-product postgres-payment postgres-auth mongodb zipkin
```

## Architecture Overview

**Multi-module Maven project** — Java 17, Spring Boot 3.2.5, Spring Cloud 2023.0.1.

### Service Startup Order (critical dependency chain)
Config-service must be healthy before discovery-service starts. Both must be healthy before all application services start. Services fetch their config via `SPRING_CONFIG_IMPORT=optional:configserver:http://config-service:8888`. Per-service YAML configs live in `config-service/src/main/resources/configurations/<service-name>.yml`.

### Service Ports
| Service | Port |
|---|---|
| config-service | 8888 |
| discovery-service (Eureka) | 8761 |
| gateway-api-service | 8222 |
| authentication-service | 8085 |
| customer-service | 8090 |
| product-service | 8082 |
| order-service | 8083 |
| payment-service | 8086 |
| cart-service | 8091 |
| notification-service | 8040 |

### Database Strategy (one DB per service)
- **PostgreSQL**: order-service (`:5432`), product-service (`:5433`), payment-service (`:5434`), authentication-service (`:5435`)
- **MongoDB**: customer-service, notification-service (`:27017`)
- **Redis**: cart-service (session store), gateway (rate limiting), customer-service (caching) — `:6379`

Database migrations for PostgreSQL services use **Flyway**, with migration scripts in `<service>/src/main/resources/db/migration/`.

### Inter-Service Communication
- **Synchronous**: OpenFeign clients for service-to-service HTTP calls (e.g., order-service calls customer-service and product-service via Feign). Feign interceptors automatically propagate `X-Tenant-ID`.
- **Asynchronous**: Kafka for the order saga and notifications. Topics: `order.requested`, payment/inventory events.

### Order Saga (Transactional Outbox Pattern)
`createOrder()` returns HTTP 202 with a `correlationId`. The flow:
1. Validate customer (sync Feign call)
2. Persist `Order` (status=`REQUESTED`) + `OutboxEvent` in a **single transaction**
3. `OutboxEventPublisher` (scheduled every 5s) polls `outbox_event` table and publishes to Kafka
4. `OrderSagaConsumer` processes downstream saga events (payment, inventory) and updates order status
5. Client polls `GET /api/v1/orders/status/{correlationId}` for updates

After 5 failed Kafka publish retries, the `OutboxEvent` is marked `FAILED`.

### Gateway Filter Chain (Spring Cloud Gateway — reactive)
Two global filters run in order:
1. `JwtAuthenticationFilter` — `HIGHEST_PRECEDENCE + 10`: validates JWT, extracts `X-Tenant-ID` from claims and injects it as a request header
2. `TenantValidationFilter` — `HIGHEST_PRECEDENCE + 20`: reads `X-Tenant-ID`, calls `tenant-service` to verify the tenant is `ACTIVE`; enriches request with `X-Tenant-Rate-Limit`

Public paths (configured via `gateway.public-paths`) bypass both filters.

### Phase 4: SaaS Multi-Tenancy
`tenant-context` is a **shared library module** (not a standalone service) depended on by all application services. Key components:
- `TenantContext` — `InheritableThreadLocal<String>` holding the current tenant ID per request thread
- `TenantInterceptor` — Spring MVC `HandlerInterceptor` that sets/clears `TenantContext` from the `X-Tenant-ID` header
- `TenantHibernateFilterActivator` — activates a Hibernate `@Filter` on read operations to scope queries to the current tenant
- `TenantFeignInterceptor` — propagates `X-Tenant-ID` to all outgoing Feign calls automatically
- `@EnableMultiTenancy` — annotation to opt a service into multi-tenancy auto-configuration

`tenant-service` is the standalone CRUD service for managing tenant records (domain/application/infrastructure/presentation layered architecture).

### Error Messages
All user-facing and log messages are externalised to `messages.properties` per service (located in `src/main/resources/`). No hardcoded strings in business logic — always use `MessageSource.getMessage(key, args, locale)`.

### Testing Strategy
- **Unit tests**: JUnit 5 + Mockito (`*Test.java`)
- **Integration tests**: Testcontainers for real Docker instances of MongoDB/Redis/PostgreSQL (`*IntegrationTest.java`, extend `AbstractIntegrationTest`)
- **BDD**: product-service uses Cucumber (`CucumberTestRunner`)

### Observability
- **Distributed tracing**: Zipkin at `:9411`
- **Metrics**: Prometheus (`:9090`) + Grafana (`:3000`); Prometheus config at `config-service/src/main/resources/prometheus/prometheus.yml`
- **Email (dev)**: MailHog UI at `:8025`, SMTP at `:1025`
