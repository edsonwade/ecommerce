# How It Works — SaaS E-Commerce Microservice Platform

> A production-grade, multi-tenant SaaS e-commerce platform built on Spring Boot 3, Spring Cloud, Kafka, and React 19.
> This document explains every architectural decision, runtime behavior, and production deployment procedure.

---

## Table of Contents

1. [System Overview](#1-system-overview)
2. [Service Topology & Startup Order](#2-service-topology--startup-order)
3. [Multi-Tenancy Architecture](#3-multi-tenancy-architecture)
4. [Authentication & Authorization Flow](#4-authentication--authorization-flow)
5. [API Gateway — The Front Door](#5-api-gateway--the-front-door)
6. [Order Lifecycle — Choreography Saga](#6-order-lifecycle--choreography-saga)
7. [Service Deep Dives](#7-service-deep-dives)
8. [Event-Driven Architecture (Kafka)](#8-event-driven-architecture-kafka)
9. [Data Layer](#9-data-layer)
10. [Frontend Architecture](#10-frontend-architecture)
11. [Observability Stack](#11-observability-stack)
12. [Secrets Management (Vault)](#12-secrets-management-vault)
13. [Infrastructure & Networking](#13-infrastructure--networking)
14. [Running in Production](#14-running-in-production)
15. [Kubernetes Deployment](#15-kubernetes-deployment)
16. [CI/CD Pipeline](#16-cicd-pipeline)
17. [Security Considerations](#17-security-considerations)
18. [Developer Onboarding](#18-developer-onboarding)

---

## 1. System Overview

This platform is a fully event-driven, multi-tenant SaaS e-commerce system composed of independently deployable microservices. Each tenant (a merchant or organization) gets full data isolation through a shared-schema approach powered by Hibernate filters and JWT-propagated tenant context.

### High-Level Architecture

```
                           ┌─────────────────────────────────────────┐
                           │          EXTERNAL CLIENTS                │
                           │  Browser (React SPA) / Mobile / API      │
                           └─────────────────┬───────────────────────┘
                                             │ HTTPS
                                    ┌────────▼────────┐
                                    │  API Gateway     │  :8222
                                    │  Spring Cloud    │
                                    │  Gateway         │
                                    └────────┬────────┘
                                             │ Load balanced via Eureka
                    ┌────────────────────────┼────────────────────────┐
                    │                        │                         │
          ┌─────────▼──────┐    ┌────────────▼────────┐    ┌─────────▼──────┐
          │  Auth Service   │    │  Customer Service    │    │ Product Service │
          │  :8085 (PgSQL)  │    │  :8090 (MongoDB)    │    │  :8082 (PgSQL)  │
          └────────────────┘    └────────────────────┘    └─────────────────┘
                                                                     │
                    ┌────────────────────────┬───────────────────────┘
                    │                        │
          ┌─────────▼──────┐    ┌────────────▼────────┐
          │  Order Service  │    │  Payment Service     │
          │  :8083 (PgSQL)  │    │  :8086 (PgSQL)      │
          └─────────┬──────┘    └────────────┬────────┘
                    │                         │
                    └─────────┬───────────────┘
                              │ Apache Kafka
                    ┌─────────▼────────────────────────┐
                    │         Event Bus (Kafka)          │
                    │  order.requested                   │
                    │  inventory.reserved                │
                    │  payment.authorized / failed       │
                    │  inventory.released (compensation) │
                    └─────────┬────────────────────────┘
                              │
          ┌───────────────────┼───────────────────┐
          │                   │                   │
 ┌────────▼───────┐  ┌────────▼──────┐  ┌────────▼────────┐
 │  Cart Service  │  │  Notification  │  │  Tenant Service  │
 │  :8091 (Redis) │  │  :8040 (Mongo) │  │  :8081           │
 └────────────────┘  └───────────────┘  └─────────────────┘

Spring Cloud Infrastructure:
  Config Server :8888 ─── Central configuration for all services
  Eureka Server  :8761 ─── Service discovery & load balancing
```

### Technology Stack

| Layer | Technology |
|---|---|
| Language | Java 17 (backend), TypeScript 6 (frontend) |
| Framework | Spring Boot 3.x, Spring Cloud 2023.x |
| API Gateway | Spring Cloud Gateway (reactive, WebFlux) |
| Service Discovery | Netflix Eureka |
| Config Management | Spring Cloud Config + HashiCorp Vault |
| Messaging | Apache Kafka 3.x (3-broker cluster) |
| Databases | PostgreSQL 15 (transactional), MongoDB (document), Redis (cache/session) |
| Security | Spring Security 6, JWT (JJWT 0.12.5), BCrypt cost=12 |
| Frontend | React 19, TypeScript, Vite 8, MUI, Zustand, React Query |
| Containerization | Docker + Docker Compose, Kubernetes (Kind / EKS / GKE) |
| Observability | Prometheus + Grafana + Loki + Zipkin + AlertManager |
| Secrets | HashiCorp Vault (dev: dev-mode, prod: HA cluster) |
| Build | Maven (multi-module), GitHub Actions CI/CD |

---

## 2. Service Topology & Startup Order

Services **must** start in dependency order. Violating this causes connection-refused failures during startup.

```
1. Infrastructure (no Spring dependencies)
   ├── Vault               (secrets backend)
   ├── Zookeeper           (Kafka coordination)
   ├── Kafka brokers 1/2/3 (event bus)
   ├── PostgreSQL x4       (auth, order, product, payment databases)
   ├── MongoDB             (customer, notification, tenant)
   └── Redis Sentinel      (cart, rate limiting, caching)

2. Spring Cloud Infrastructure
   ├── config-service   :8888  (must be healthy before other services read config)
   └── discovery-service :8761  (Eureka registry — services register here)

3. Application Services (can start in parallel once config+discovery are up)
   ├── authentication-service :8085
   ├── tenant-service         :8081
   ├── customer-service       :8090
   ├── product-service        :8082
   ├── order-service          :8083
   ├── payment-service        :8086
   ├── cart-service           :8091
   └── notification-service   :8040

4. API Gateway (starts last — needs all service registrations in Eureka)
   └── gateway-api-service :8222

5. Frontend
   └── React SPA (Nginx :80 → proxies /api/* to gateway :8222)
```

Each service reads its full configuration from `config-service` on startup via:

```yaml
spring:
  config:
    import: optional:configserver:http://config-service:8888
```

The `optional:` prefix means services start with local defaults if the config server is temporarily unavailable — critical for resilient cold starts.

---

## 3. Multi-Tenancy Architecture

The platform uses a **shared-schema, row-level isolation** multi-tenancy model. Every data row carries a `tenant_id` column that is automatically filtered at the persistence layer.

### How Tenant Context Flows Through the System

```
HTTP Request
  │
  │  Header: X-Tenant-ID: <uuid>
  │  Header: Authorization: Bearer <jwt>   (JWT also contains tenantId claim)
  ▼
API Gateway (gateway-api-service)
  ├── TenantValidationFilter  → validates X-Tenant-ID against tenant-service
  ├── JwtAuthenticationFilter → decodes JWT, extracts tenantId claim
  └── Passes X-Tenant-ID header downstream to all services
  ▼
Individual Service (e.g., order-service)
  ├── TenantContextFilter (servlet filter)
  │   ├── Reads X-Tenant-ID header
  │   ├── Calls TenantContext.setCurrentTenantId(tenantId)  [ThreadLocal]
  │   └── Enables Hibernate filter: "tenantFilter" with parameter tenantId
  ▼
JPA Repository
  ├── @FilterDef on entity: name="tenantFilter", param="tenantId"
  ├── @Filter on entity: condition="tenant_id = :tenantId"
  └── All queries automatically append: WHERE tenant_id = '<uuid>'
  ▼
TenantContextFilter (finally block)
  └── TenantContext.clear()  → prevent ThreadLocal leaks between requests
```

### Tenant Library (`tenant-context` module)

The `tenant-context` module is a shared Java library (not a Spring Boot app) included as a dependency by every service.

```java
// TenantContext.java — ThreadLocal tenant isolation
public class TenantContext {
    private static final ThreadLocal<String> TENANT_ID = new ThreadLocal<>();

    public static void setCurrentTenantId(String tenantId) { TENANT_ID.set(tenantId); }
    public static String getCurrentTenantId() { return TENANT_ID.get(); }
    public static void clear() { TENANT_ID.remove(); }
}
```

```java
// Entity annotation pattern — applied to every JPA entity
@FilterDef(name = "tenantFilter",
           parameters = @ParamDef(name = "tenantId", type = String.class))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Entity
public class Order {
    @Column(name = "tenant_id", nullable = false)
    private String tenantId;
    // ...
}
```

### Tenant Lifecycle

1. **Tenant Registration** → `POST /api/v1/tenants` creates a new tenant record with a UUID and plan type (FREE, STARTER, PROFESSIONAL, ENTERPRISE)
2. **User Registration** → Users register under a specific tenant; their `tenantId` is embedded in the JWT
3. **Request Routing** → Every API call carries `X-Tenant-ID`; the gateway validates this header against the tenant registry
4. **Data Isolation** → All SQL queries and MongoDB queries are automatically scoped to the tenant's data

---

## 4. Authentication & Authorization Flow

### Registration

```
POST /api/v1/auth/register
  │
  ├── Validate request body (email format, password strength)
  ├── Check email uniqueness (throws 409 if duplicate)
  ├── Hash password: BCryptPasswordEncoder(cost=12)
  ├── Assign role: USER (default) or ADMIN/SELLER (explicit)
  ├── Persist User entity (PostgreSQL, auth_db)
  └── Return 201 Created
```

### Login (Token Issuance)

```
POST /api/v1/auth/login
  │
  ├── Load UserDetails by email (UserDetailsServiceImpl → DB query)
  ├── AuthenticationManager.authenticate() → BCrypt compare
  ├── On success:
  │   ├── Generate accessToken (JWT, 24h TTL)
  │   │   Claims: sub=email, tenantId, role, iat, exp
  │   ├── Generate refreshToken (JWT, 7d TTL)
  │   ├── Persist Token record (linked to User, for revocation)
  │   └── Return { accessToken, refreshToken, role, tenantId }
  └── On failure: 401 Unauthorized
```

### Request Authorization

```
Any Protected Endpoint
  │
  ├── JwtAuthFilter.doFilterInternal()
  │   ├── Extract "Authorization: Bearer <token>" header
  │   ├── JwtService.extractUsername(token)  → decode subject
  │   ├── JwtService.isTokenValid(token, userDetails)
  │   │   ├── Check token not expired
  │   │   ├── Check token not revoked (Token table lookup)
  │   │   └── Check subject matches loaded UserDetails
  │   └── SecurityContextHolder.setAuthentication(UsernamePasswordAuthToken)
  ▼
SecurityFilterChain evaluates @PreAuthorize / hasRole() expressions
  └── Proceed to Controller or return 403 Forbidden
```

### Token Refresh

```
POST /api/v1/auth/refresh   (permit-all — no JWT required)
  │
  ├── Extract refresh token from request body
  ├── RefreshTokenService.rotate():
  │   ├── Validate refresh token (not expired, not revoked)
  │   ├── Revoke old access tokens for this user
  │   ├── Issue new accessToken + refreshToken pair
  │   └── Persist new Token records
  └── Return new { accessToken, refreshToken }
```

### JWT Structure

```json
Header:  { "alg": "HS256", "typ": "JWT" }
Payload: {
  "sub": "user@example.com",
  "tenantId": "550e8400-e29b-41d4-a716-446655440000",
  "role": "USER",
  "iat": 1712345678,
  "exp": 1712432078
}
Signature: HMACSHA256(base64(header) + "." + base64(payload), JWT_SECRET)
```

### Role-Based Access Control

| Role | Access |
|---|---|
| `USER` | Own orders, cart, profile, product catalog |
| `SELLER` | Product management, inventory, own store orders |
| `ADMIN` | All tenant data, user management, analytics, tenant administration |

---

## 5. API Gateway — The Front Door

The gateway is the **single entry point** for all client traffic. It is built on Spring Cloud Gateway (reactive, WebFlux-based) and enforces security, rate limiting, circuit breaking, and retry before requests reach any service.

### Filter Execution Chain

Every request passes through these filters in order:

```
Incoming HTTP Request
        │
        ▼
1. TenantValidationFilter
   ├── Reads X-Tenant-ID header
   ├── Calls TenantServiceClient.validateTenant(tenantId)
   └── Returns 400 if header missing, 403 if tenant not found/inactive

2. JwtAuthenticationFilter
   ├── Reads Authorization: Bearer <token>
   ├── Validates token (expiry, signature, revocation)
   └── Returns 401 if invalid/missing (except public paths)

3. LoadSheddingFilter
   ├── Tracks active concurrent requests
   ├── Rejects new requests if above max-concurrent-requests (5000)
   └── Returns 503 if threshold exceeded (circuit-level protection)

4. RequestIdFilter
   └── Injects X-Request-Id: <uuid> header (used in distributed tracing)

5. Route Matching & Forwarding
   ├── RequestRateLimiter (Redis token bucket per tenant)
   ├── CircuitBreaker (Resilience4j per service)
   ├── Retry (exponential backoff, GET-only)
   └── lb://{SERVICE-NAME} (Eureka-based load balancing)
```

### Rate Limiting

Rate limits are enforced per-tenant using Redis as the token bucket backend:

| Service | Replenish Rate | Burst Capacity | Rationale |
|---|---|---|---|
| Auth | 20/s | 40/s | Prevent brute-force |
| Payment | 20/s | 30/s | Financial endpoint protection |
| Cart | 200/s | 400/s | High-frequency UX operations |
| Others | 100/s | 200/s | Default |

The key resolver uses the `X-Tenant-ID` header so tenants are rate-limited independently — a misbehaving tenant cannot starve others.

### Circuit Breakers

Each downstream service has an independent Resilience4j circuit breaker. When a service is failing, the circuit opens and the gateway immediately returns a fallback response instead of queuing requests:

| Service | Failure Threshold | Open State Duration | Slow Call Threshold |
|---|---|---|---|
| customer-cb | 50% | 30s | 80% > 3s |
| cart-cb | 60% | 15s | 90% > 1s |
| order-cb | 50% | 30s | 70% > 5s |
| product-cb | 60% | 20s | 80% > 2s |
| payment-cb | 30% | 60s | 50% > 10s |

The payment circuit breaker uses the strictest threshold (30% failure → open) because a slow payment service is worse than a fast failure — we never want to retry payment requests blindly.

### Service Discovery

The gateway does **not** have hardcoded service addresses. All routing uses Eureka:

```
lb://AUTHENTICATION-SERVICE  →  Eureka resolves to running instance IP:port
lb://ORDER-SERVICE           →  Eureka load-balances across multiple replicas
```

When a service pod scales up, it registers itself with Eureka within 10–30 seconds, and the gateway automatically starts routing to it. When a pod dies, Eureka removes it after the lease expiration (90s default, 30s in our config).

---

## 6. Order Lifecycle — Choreography Saga

The order flow is implemented as a **Choreography Saga** — a distributed transaction pattern where each service publishes events and reacts to others without a central coordinator. This ensures all services remain decoupled and independently deployable.

### Full Order Flow

```
CUSTOMER                ORDER-SERVICE           PRODUCT-SERVICE         PAYMENT-SERVICE
   │                         │                        │                        │
   │  POST /api/v1/orders     │                        │                        │
   │─────────────────────────▶│                        │                        │
   │                         │ Persist Order           │                        │
   │                         │ Status: REQUESTED       │                        │
   │                         │                        │                        │
   │  202 Accepted            │                        │                        │
   │  { correlationId }       │                        │                        │
   │◀─────────────────────────│                        │                        │
   │                         │ Publish: order.requested│                        │
   │                         │─────────────────────────▶                        │
   │                         │  [OrderRequestedEvent]  │                        │
   │                         │  { correlationId,       │                        │
   │                         │    products[], tenantId }│                        │
   │                         │                        │ Reserve inventory       │
   │                         │                        │ Check availableQty      │
   │                         │                        │                        │
   │                         │              [SUCCESS]  │                        │
   │                         │                        │ Publish: inventory.reserved
   │                         │◀────────────────────────│──────────────────────▶ │
   │                         │                        │  [InventoryReservedEvent]│
   │                         │ Update Order status     │                        │
   │                         │ INVENTORY_RESERVED      │                        │
   │                         │                        │ Process payment         │
   │                         │                        │ (Idempotency key check) │
   │                         │                        │                        │
   │                         │              [SUCCESS]  │                        │
   │                         │◀────────────────────────│────────────────────────│
   │                         │                        │ Publish: payment.authorized
   │                         │                        │                        │
   │                         │ Update Order status     │                        │
   │                         │ CONFIRMED               │                        │
   │                         │ Commit DB offset        │                        │
   │                         │                        │                        │
   │  GET /orders/status/{id} │                        │                        │
   │─────────────────────────▶│                        │                        │
   │  { status: CONFIRMED }   │                        │                        │
   │◀─────────────────────────│                        │                        │

── COMPENSATION FLOW (payment failure) ─────────────────────────────────────────

   │                         │◀────────────────────────│────────────────────────│
   │                         │                        │ Publish: payment.failed │
   │                         │                        │                        │
   │                         │ Update Order status     │                        │
   │                         │ FAILED                  │                        │
   │                         │ Publish: inventory.released                      │
   │                         │─────────────────────────▶                        │
   │                         │                        │ Release reserved stock  │
   │                         │                        │ (Compensation TX)       │
```

### Outbox Pattern (At-Least-Once Delivery)

The order service uses the **Transactional Outbox Pattern** to guarantee that published Kafka events are never lost:

```
1. Order is created (DB write)
2. OutboxEvent is written IN THE SAME TRANSACTION
3. OutboxEventPublisher (scheduled job, every 100ms) polls outbox table
4. Publishes events to Kafka
5. Marks events as published
```

This prevents the dual-write problem: if Kafka is unavailable at order creation time, the event is safely stored in the database and published when Kafka recovers.

### Manual Kafka Acknowledgement

Order saga consumers use `MANUAL_IMMEDIATE` acknowledgement mode:

```java
// Kafka offset committed ONLY after successful DB write
@KafkaListener(topics = "payment.authorized", ...)
public void handlePaymentAuthorized(PaymentAuthorizedEvent event, Acknowledgment ack) {
    orderRepository.updateStatus(event.correlationId(), OrderStatus.CONFIRMED);  // DB write
    ack.acknowledge();  // Only commit Kafka offset if DB write succeeded
}
```

This ensures that if the service crashes after receiving the Kafka message but before updating the database, the message will be re-delivered (at-least-once semantics).

### Correlation ID

Every order receives a unique `correlationId` (UUID) on creation. This ID ties together all saga events across services and allows clients to poll the final order status:

```
GET /api/v1/orders/status/{correlationId}
→ { status: "CONFIRMED" | "FAILED" | "INVENTORY_RESERVED" | "REQUESTED" }
```

---

## 7. Service Deep Dives

### Authentication Service (:8085)

**Purpose:** Identity provider — handles user registration, login, JWT issuance, token rotation, and logout.

**Key Design Decisions:**
- `@Order(1)` on `SecurityConfig` overrides Spring Boot's auto-configured chain in test slices
- `@EnableJpaAuditing` in `JpaConfig` (not `SecurityConfig`) prevents `@WebMvcTest` loading JPA context unnecessarily
- Stateless session — no `HttpSession` created; all state lives in JWT
- Token revocation table (`token`) prevents reuse of logged-out tokens without waiting for expiry

**Database:** `auth_db` (PostgreSQL, port 5435)
- Tables: `app_user`, `token`
- Flyway manages schema migrations
- `spring.jpa.hibernate.ddl-auto: validate` — schema must match entity classes exactly

---

### Cart Service (:8091)

**Purpose:** Ephemeral shopping cart backed by Redis. Cart data is intentionally transient — it expires after 24 hours of inactivity.

**Key Design Decisions:**
- Redis Hash data structure (`@RedisHash`) — entire cart stored as a single hash, enabling O(1) reads
- Key format: `cart:{tenantId}:{customerId}` — tenant isolation at the key level (not filter-based)
- TTL sliding window: 24 hours from last modification; adding an item resets the timer
- Redis Sentinel (3 sentinels) — automatic failover if primary Redis dies. Sentinel promotes a replica in < 30 seconds

**Cart Checkout Snapshot:**
```
GET /api/v1/carts/{customerId}/checkout
→ Returns a point-in-time cart snapshot used by order-service when creating an order
→ After checkout, cart is cleared: DELETE /api/v1/carts/{customerId}
```

---

### Product Service (:8082)

**Purpose:** Product catalog and inventory management. Consumes order events to reserve stock.

**Key Design Decisions:**
- `availableQuantity` is decremented atomically in a database transaction
- Kafka consumer group `inventory-reservation-group` — guarantees that each `order.requested` event is processed by exactly one consumer instance
- DLQ strategy: 3 retries with 1-second backoff → if still failing, event goes to `order.requested.DLQ` for manual inspection
- Concurrency: 3 consumer threads (3 Kafka partition readers per instance)

**Inventory Reservation Logic:**
```java
// Optimistic check — if quantity insufficient, publish inventory.insufficient
if (product.getAvailableQuantity() < requestedQty) {
    kafkaProducer.publish("inventory.insufficient", event);
} else {
    product.setAvailableQuantity(product.getAvailableQuantity() - requestedQty);
    productRepository.save(product);
    kafkaProducer.publish("inventory.reserved", event);
}
```

---

### Order Service (:8083)

**Purpose:** Order lifecycle management and saga orchestration hub.

**Key Design Decisions:**
- Returns `202 Accepted` immediately — order processing is asynchronous via Kafka
- `correlationId` is the client's handle to track async saga progress
- `OrderSagaConsumer` subscribes to outcome events and drives order status transitions
- Outbox pattern ensures events survive service restarts
- Feign clients call customer, payment, product services **through the gateway** (not direct service calls) — this ensures gateway's circuit breakers and rate limiters apply to inter-service traffic as well

---

### Payment Service (:8086)

**Purpose:** Payment authorization. Publishes payment outcomes to drive the saga.

**Key Design Decisions:**
- Idempotency key (unique constraint on `orderId + idempotencyKey`) prevents double charges if the service is called twice for the same order
- Payment circuit breaker has the strictest thresholds (30% failure → 60s open) — payment failures are expensive
- **No retry** on payment routes in the gateway — retrying a payment request can lead to double charges

---

### Customer Service (:8090)

**Purpose:** Customer profile management. Uses MongoDB for flexible document storage.

**Key Design Decisions:**
- MongoDB instead of PostgreSQL — customer profiles are schema-flexible (different tenants may store different address fields)
- Redis caching layer: customer profile reads are cached with a 10-minute TTL. Cache evicted on profile updates
- No Hibernate @Filter (MongoDB doesn't support it) — tenant isolation enforced at the service layer with explicit `tenantId` query parameters

---

### Notification Service (:8040)

**Purpose:** Email delivery triggered by Kafka events. Consumes payment and order events, sends emails via SMTP.

**Key Design Decisions:**
- Idempotency via `ProcessedEvent` (MongoDB): before sending any email, service checks if this `eventId` was already processed. Prevents duplicate emails from at-least-once Kafka delivery
- Dead Letter Queue consumer: reads from `payment-topic.DLQ` and `order-topic.DLQ`, persists to MongoDB for operations team review
- SMTP transport: MailHog in development (port 1025), real SMTP provider in production (SendGrid / SES)

---

### Gateway Service (:8222)

See [Section 5](#5-api-gateway--the-front-door) for full detail.

---

### Config Service (:8888)

**Purpose:** Centralized configuration management for all services.

**How It Works:**
- Spring Cloud Config Server with `native` backend (config files on classpath)
- Config files: `config-service/src/main/resources/configurations/{service-name}.yml`
- Services fetch config at startup: `GET http://config-service:8888/{service-name}/default`
- Vault integration: sensitive values (JWT secret, DB passwords) fetched from Vault at config-service startup, then served to downstream services

---

### Discovery Service (:8761)

**Purpose:** Eureka service registry — services announce themselves here; gateway queries it for load balancing.

**Client Credentials:** `eureka:eureka-secret-2024` (basic auth)

**Registration Lifecycle:**
1. Service starts → `@EnableDiscoveryClient` → registers with Eureka (IP, port, health URL)
2. Eureka health check every 30s → removes instance if unhealthy for 90s
3. Gateway queries Eureka every 5s for fresh instance list

---

## 8. Event-Driven Architecture (Kafka)

### Kafka Cluster

3-broker cluster with SASL/PLAIN authentication:
- `kafka1:9092` (external: 29092)
- `kafka2:9094`
- `kafka3:9096`

In production, each broker runs on a separate node with dedicated storage.

### Topic Inventory

| Topic | Partitions | Replication | Producer | Consumer(s) |
|---|---|---|---|---|
| `order.requested` | 10 | 1 (3 in prod) | order-service | product-service |
| `inventory.reserved` | 10 | 1 | product-service | payment-service, order-service |
| `inventory.insufficient` | 10 | 1 | product-service | order-service |
| `inventory.released` | 10 | 1 | order-service | product-service |
| `payment.authorized` | 10 | 1 | payment-service | order-service, notification-service |
| `payment.failed` | 10 | 1 | payment-service | order-service, notification-service |
| `*.DLQ` | 3 | 1 | error handlers | notification-service (DlqConsumer) |

### Consumer Groups

| Group ID | Service | Topics | Purpose |
|---|---|---|---|
| `inventory-reservation-group` | product-service | `order.requested` | Reserve stock for new orders |
| `payment-saga-group` | payment-service | `inventory.reserved` | Authorize payment after stock reservation |
| `order-saga-group` | order-service | `payment.authorized`, `payment.failed`, `inventory.insufficient` | Update order status based on saga outcome |
| `notification-group` | notification-service | `payment.authorized`, `payment.failed` | Trigger emails |
| `dlq-consumer-group` | notification-service | `*.DLQ` | Capture failed events for ops review |

### Dead Letter Queue Strategy

Every consumer is configured with a `DeadLetterPublishingRecoverer`:

```
Message arrives → Consumer processes it
  ├── SUCCESS → ack.acknowledge() → offset committed
  └── FAILURE → Retry 3 times (1s fixed backoff)
                   ├── Still failing → Publish to {topic}.DLQ
                   └── ack.acknowledge() → offset committed
                        │
                        ▼
              DlqConsumer (notification-service)
                   └── Persist to MongoDB (dlq_events collection)
                        └── Ops team reviews failed events in Grafana/admin UI
```

### Event Schema

Events are serialized as JSON. Example `OrderRequestedEvent`:

```json
{
  "correlationId": "550e8400-e29b-41d4-a716-446655440000",
  "tenantId": "abc123",
  "customerId": "cust-456",
  "orderReference": "ORD-2024-001",
  "totalAmount": 99.99,
  "paymentMethod": "CREDIT_CARD",
  "products": [
    { "productId": "prod-789", "quantity": 2, "unitPrice": 49.99 }
  ],
  "timestamp": "2024-04-13T10:00:00Z"
}
```

---

## 9. Data Layer

### Database-per-Service Pattern

Each service owns its database — no service can directly query another service's database. Cross-service data access goes through APIs.

| Service | Database | Technology | Port |
|---|---|---|---|
| authentication-service | auth_db | PostgreSQL 15 | 5435 |
| order-service | order_service_db | PostgreSQL 15 | 5432 |
| product-service | product_service_db | PostgreSQL 15 | 5432 |
| payment-service | payment_db | PostgreSQL 15 | 5432 |
| customer-service | customer_service_db | MongoDB | 27017 |
| notification-service | notification_db | MongoDB | 27017 |
| cart-service | (no persistent DB) | Redis | 6379 |

### Schema Management

All PostgreSQL services use **Flyway** for migration management:
- Migrations in `src/main/resources/db/migration/V{version}__{description}.sql`
- `spring.jpa.hibernate.ddl-auto: validate` — Hibernate validates schema on startup, never auto-alters
- Forward-only migrations — no rollbacks via Flyway (use blue-green deployment strategy)

### Multi-Tenant Data Isolation (PostgreSQL)

Every table in a multi-tenant service includes `tenant_id VARCHAR NOT NULL` with a Hibernate filter:

```sql
-- Example: order table
CREATE TABLE customer_order (
    order_id    VARCHAR(255) PRIMARY KEY,
    tenant_id   VARCHAR(255) NOT NULL,
    correlation_id VARCHAR(255) UNIQUE NOT NULL,
    -- ... other columns
    CONSTRAINT fk_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id)
);
CREATE INDEX idx_order_tenant ON customer_order(tenant_id);
```

The Hibernate filter (`WHERE tenant_id = :tenantId`) is activated per-request by `TenantContextFilter`. No data from another tenant can be read, even if a bug allows an attacker to guess another tenant's order ID — the filter prevents the query from returning any rows.

### Redis Architecture

Redis runs in **Sentinel mode** (high availability):
- 1 primary node handles writes
- 2 replica nodes handle reads
- 3 Sentinel nodes monitor primary + replicas, trigger automatic failover if primary dies

Services connect via Sentinel: `spring.data.redis.sentinel.master: mymaster`

**Usage by service:**
- `cart-service` → Primary store (cart data as Redis Hash, TTL 24h)
- `gateway-api-service` → Rate limiter token buckets (per-tenant, per-route)
- `customer-service` → Profile cache (TTL 10 min, cache-aside pattern)
- `authentication-service` → Token revocation (optional: revoked tokens cached for TTL duration)

---

## 10. Frontend Architecture

The frontend is a **React 19 single-page application** with TypeScript, served by Nginx in production. All API calls are proxied through Nginx to the API gateway.

### Routing Structure

```
Public (no auth required):
  /                        → HomePage (product catalog landing)
  /catalog                 → CatalogPage (browsable product grid)
  /products/:id            → ProductPage (product detail + add to cart)
  /login                   → LoginPage
  /register                → RegisterPage

Customer (JWT required, role: USER):
  /dashboard               → DashboardPage (order summary, welcome)
  /orders                  → OrdersPage (order history)
  /orders/:id              → OrderDetailPage (status + saga timeline)
  /profile                 → ProfilePage (edit personal info)
  /checkout                → CheckoutPage (cart → order creation)

Seller (JWT required, role: SELLER):
  /seller/dashboard        → SellerDashboard
  /seller/products         → ProductManagement (CRUD)
  /seller/products/new     → ProductForm
  /seller/orders           → OrderManagement
  /seller/inventory        → InventoryPage

Admin (JWT required, role: ADMIN):
  /admin/dashboard         → AdminDashboard
  /admin/tenants           → TenantsPage
  /admin/tenants/:id       → TenantDetailPage
  /admin/users             → UsersPage
  /admin/payments          → PaymentsPage
  /admin/analytics         → AnalyticsPage
```

All routes are lazy-loaded with `React.lazy()` + `Suspense` — initial bundle is minimal, each page chunk loaded on navigation.

### State Management

| Store | Library | State |
|---|---|---|
| `auth.store.ts` | Zustand | `accessToken`, `refreshToken`, `tenantId`, `user` (email, role) |
| `cart.store.ts` | Zustand | `items`, `total`, `itemCount`, `isOpen` (drawer) |
| `ui.store.ts` | Zustand | `darkMode`, `sidebarOpen`, `notifications` |

Zustand stores are persisted to `localStorage` for auth state (survives page refreshes), with cart synced to backend on login.

### API Client

All HTTP calls go through `src/api/client.ts` — a configured Axios instance:

```typescript
// Request interceptor: inject auth headers automatically
axiosInstance.interceptors.request.use((config) => {
  const { accessToken, tenantId } = useAuthStore.getState();
  if (accessToken) config.headers.Authorization = `Bearer ${accessToken}`;
  if (tenantId)    config.headers['X-Tenant-Id'] = tenantId;
  return config;
});

// Response interceptor: silent token refresh on 401
axiosInstance.interceptors.response.use(null, async (error) => {
  if (error.response?.status === 401 && !error.config._retry) {
    error.config._retry = true;
    const newToken = await refreshAccessToken();
    error.config.headers.Authorization = `Bearer ${newToken}`;
    return axiosInstance(error.config);  // Retry original request
  }
  return Promise.reject(error);
});
```

Multiple simultaneous 401s are queued — only one refresh call is made, and all queued requests receive the new token.

### Nginx Production Configuration

In production, the frontend container runs Nginx which:
1. Serves the React SPA static files
2. Proxies `/api/*` requests to the API gateway
3. Handles SPA routing (`try_files $uri /index.html`)
4. Sets security headers (CSP, HSTS, X-Frame-Options)

```nginx
server {
  listen 80;
  root /usr/share/nginx/html;

  location /api/ {
    proxy_pass http://gateway-api-service:8222;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
  }

  location / {
    try_files $uri $uri/ /index.html;  # SPA fallback
  }
}
```

---

## 11. Observability Stack

### Metrics — Prometheus + Grafana

Every Spring Boot service exposes `/actuator/prometheus`. Prometheus scrapes all services every 15 seconds.

**Key Metrics Tracked:**
- JVM: heap usage, GC pause duration, thread count
- HTTP: request rate, latency (p50, p95, p99), error rate per route
- Kafka: consumer lag, message throughput, partition assignment
- Custom: order creation rate, payment success/failure ratio, inventory reservation rate

**Grafana Dashboards (port 3000):**
- JVM Overview — heap, GC, threads per service
- Kafka Lag Monitor — consumer group lag per topic/partition
- API Latency Heatmap — p50/p95/p99 per endpoint
- Business Metrics — orders/hour, revenue, top products

### Logs — Loki + Promtail

Promtail runs as a sidecar/agent that:
1. Reads Docker container logs (via `docker.sock` mount)
2. Parses log format (JSON structured logs from Spring Boot)
3. Ships to Loki with labels: `service`, `tenant`, `level`, `traceId`

Logs are queryable in Grafana using LogQL:
```
{service="order-service"} |= "FAILED" | json | level="ERROR"
```

### Distributed Tracing — Zipkin

Services emit traces via Micrometer (Zipkin exporter) at 10% sampling rate. Traces show the full request path across services, including Kafka publish latency and database query time.

**Trace Propagation:** `traceparent` W3C header propagated by Feign clients and Kafka producer/consumer headers.

### Alerting — AlertManager

Alerts defined in Prometheus alert rules. AlertManager routes alerts to:
- Slack (critical: `#oncall`, warning: `#alerts`)
- Email (on-call engineer)
- PagerDuty (critical only)

**Key Alerts:**
- `OrderSagaStuck` — order in non-terminal state > 10 minutes
- `PaymentCircuitOpen` — payment-cb in open state > 5 minutes
- `KafkaConsumerLagHigh` — consumer lag > 10,000 messages
- `ServiceDown` — service not scraped for > 2 minutes

---

## 12. Secrets Management (Vault)

### Development Mode

HashiCorp Vault runs in `dev` mode (docker-compose):
- Auto-unseal on startup
- Root token: `root-token` (never use in production)
- UI: `http://localhost:8200`
- All data is in-memory (lost on restart)

### Production Mode

Vault must run in **HA cluster mode** (3 nodes, Raft storage, TLS):
1. `vault operator init` → generates unseal keys + root token
2. Store unseal keys in separate HSM or split across trusted operators
3. `vault operator unseal` with 3-of-5 keys to unseal after restart

### Secret Structure

```
secret/
  ecommerce/
    shared/
      jwt_secret: <256-bit key>
      kafka_username: <user>
      kafka_password: <pass>
      redis_password: <pass>
    authentication-service/
      db_password: <pass>
    order-service/
      db_password: <pass>
    payment-service/
      db_password: <pass>
    product-service/
      db_password: <pass>
```

### How Services Access Vault

Services use Spring Cloud Vault. On startup, they authenticate to Vault using their service token (injected via environment variable) and fetch their secrets. The config-service acts as a secrets aggregator — it fetches all service secrets and serves them via Spring Cloud Config endpoints.

---

## 13. Infrastructure & Networking

### Network Segmentation (Docker Compose)

Traffic is segregated into three overlay networks to enforce the principle of least privilege:

```
infra-net (INTERNAL — no external access):
  - Kafka, Zookeeper
  - PostgreSQL x4, MongoDB
  - Redis + Sentinel nodes
  - Vault
  Services connect here to access databases/messaging.
  No direct external traffic reaches these nodes.

services-net (APPLICATION):
  - All Spring Boot services
  - Config-service, Discovery-service
  - Gateway-api-service
  - MailHog
  Services communicate with each other here.

monitoring-net (OBSERVABILITY):
  - Prometheus, Grafana, Loki, Promtail
  - Zipkin, AlertManager
  Monitoring stack can scrape all services but is isolated from infra.
```

### Volume Persistence

All stateful services use named Docker volumes:

```
vault-data           → Vault secrets (dev: in-memory, prod: Raft snapshots)
postgres-auth-data   → auth_db data files
postgres-order-data  → order_service_db data files
postgres-product-data→ product_service_db data files
postgres-payment-data→ payment_db data files
mongodb-data         → MongoDB data directory
redis-data           → Redis AOF + RDB persistence files
prometheus-data      → Prometheus TSDB (15-day retention)
grafana-data         → Grafana dashboards, datasources, users
loki-data            → Log chunks
zipkin-data          → Trace data
alertmanager-data    → Silences, notification state
```

---

## 14. Running in Production

### Prerequisites

- Docker 24+ and Docker Compose V2
- 16 GB RAM minimum (32 GB recommended for full stack)
- 50 GB disk for volumes
- A `.env` file with all secrets (never commit to git)

### Environment Variables

Create a `.env` file in the project root:

```env
# JWT
JWT_SECRET=<base64-encoded-256-bit-key>

# Kafka SASL
KAFKA_USERNAME=kafka-user
KAFKA_PASSWORD=<strong-password>

# Redis
REDIS_PASSWORD=<strong-password>
REDIS_SENTINEL_PASSWORD=<strong-password>

# PostgreSQL (one per DB)
POSTGRES_AUTH_PASSWORD=<pass>
POSTGRES_ORDER_PASSWORD=<pass>
POSTGRES_PRODUCT_PASSWORD=<pass>
POSTGRES_PAYMENT_PASSWORD=<pass>

# MongoDB
MONGODB_USERNAME=vanilson
MONGODB_PASSWORD=<pass>

# Vault
VAULT_ENABLED=true
VAULT_HOST=vault
VAULT_TOKEN=<production-vault-token>

# Eureka
EUREKA_USERNAME=eureka
EUREKA_PASSWORD=eureka-secret-2024

# Grafana
GRAFANA_ADMIN_PASSWORD=<strong-password>

# Mail (production SMTP)
MAIL_HOST=smtp.sendgrid.net
MAIL_PORT=587
MAIL_USERNAME=apikey
MAIL_PASSWORD=<sendgrid-api-key>
```

### Startup Sequence

```bash
# 1. Start infrastructure (databases, Kafka, Redis, Vault)
docker-compose up -d zookeeper kafka1 kafka2 kafka3
docker-compose up -d postgres-auth postgres-order postgres-product postgres-payment
docker-compose up -d mongodb
docker-compose up -d redis redis-sentinel-1 redis-sentinel-2 redis-sentinel-3
docker-compose up -d vault

# 2. Wait for infrastructure to be healthy
docker-compose ps   # All infra should show "healthy" within 60 seconds

# 3. Start Spring Cloud infrastructure
docker-compose up -d config-service
sleep 30   # Wait for config-service to register Vault secrets
docker-compose up -d discovery-service
sleep 20   # Wait for Eureka to be ready

# 4. Start application services (parallel)
docker-compose up -d authentication-service tenant-service customer-service \
                       product-service order-service payment-service \
                       cart-service notification-service

# 5. Start gateway (after services register with Eureka)
sleep 30
docker-compose up -d gateway-api-service

# 6. Start frontend
docker-compose up -d frontend

# 7. Start observability stack
docker-compose up -d prometheus grafana loki promtail zipkin alertmanager

# OR: Start everything at once (Docker Compose handles dependency order via depends_on)
docker-compose up -d
```

### Health Verification

```bash
# Check all services are healthy
docker-compose ps

# Test gateway is routing correctly
curl -f http://localhost:8222/actuator/health

# Test auth service
curl -X POST http://localhost:8222/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: <tenant-uuid>" \
  -d '{"firstname":"Test","lastname":"User","email":"test@example.com","password":"Test1234!"}'

# Verify Eureka registrations
open http://localhost:8761

# Access Grafana dashboards
open http://localhost:3000   # admin / <GRAFANA_ADMIN_PASSWORD>

# Access application frontend
open http://localhost:80
```

### Production Docker Compose (HA mode)

Use `docker-compose.prod.yml` for production overrides:

```bash
docker-compose -f docker-compose.yml -f docker-compose.prod.yml up -d
```

The prod overlay sets:
- Kafka replication factor: 3 (instead of 1)
- Resource limits on all containers (CPU + memory)
- No exposed database ports (only internal network access)
- Log rotation policies
- Restart policy: `always`

### Graceful Shutdown

All Spring Boot services are configured for graceful shutdown:

```yaml
server:
  shutdown: graceful
spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s
```

On `docker-compose stop`, services finish in-flight requests (up to 30s) before stopping. Kafka consumers commit offsets cleanly. Database connections are properly closed.

---

## 15. Kubernetes Deployment

### Cluster Requirements

- Kubernetes 1.28+
- NGINX Ingress Controller
- cert-manager (TLS certificate automation)
- Horizontal Pod Autoscaler (metrics-server required)
- Persistent Volume provisioner (EBS, GCE PD, or local-path)

### Namespace & Resource Quotas

```bash
kubectl apply -f k8s/namespace.yml
# Creates: ecommerce namespace
# ResourceQuota: 40 CPU cores, 40 GB RAM, 100 pods max
```

### Deployment

```bash
# Apply all manifests
kubectl apply -f k8s/network-policies/
kubectl apply -f k8s/services/all-services.yml
kubectl apply -f k8s/deployments/all-services-deployments.yml
kubectl apply -f k8s/hpa/all-hpa.yml
kubectl apply -f k8s/pdb/all-pdb.yml
kubectl apply -f k8s/ingress.yml

# Verify rollout
kubectl rollout status deployment/gateway-api-service -n ecommerce
kubectl get pods -n ecommerce
```

### Using Helm (Recommended)

```bash
# Install with custom values
helm install ecommerce ./helm \
  --namespace ecommerce \
  --create-namespace \
  --set global.jwtSecret=${JWT_SECRET} \
  --set global.kafka.bootstrapServers="kafka1:9092,kafka2:9094,kafka3:9096" \
  --set orderService.replicas=3 \
  --set paymentService.replicas=2 \
  --values helm/values.production.yaml

# Upgrade (rolling update)
helm upgrade ecommerce ./helm -n ecommerce --values helm/values.production.yaml
```

### Auto-Scaling

HPA scales each service based on CPU and memory utilization:

```yaml
# Min 2 replicas (high availability), max 10 replicas
minReplicas: 2
maxReplicas: 10
targetCPUUtilizationPercentage: 80
targetMemoryUtilizationPercentage: 80
```

PodDisruptionBudget ensures at least 1 pod is always available during node maintenance or rolling updates.

### Ingress & TLS

```
https://ecommerce.vanilsonshop.io/api/*  →  gateway-api-service:8222
https://ecommerce.vanilsonshop.io/grafana/* → grafana:3000
```

TLS certificate issued automatically by cert-manager via Let's Encrypt. Certificate auto-renewed 30 days before expiry.

### Network Policies

By default, all pod-to-pod traffic is denied. Explicit allow rules are defined for:
- Gateway → all services (ingress rule on each service)
- Services → Kafka brokers (port 9092/9094/9096)
- Services → PostgreSQL (port 5432)
- Services → MongoDB (port 27017)
- Services → Redis (port 6379)
- Prometheus → all services (port for `/actuator/prometheus`)

### Istio (Optional Service Mesh)

If Istio is installed, additional features are available:
- **mTLS** between all pods (zero-trust networking)
- **Traffic splitting** for canary deployments (10% traffic to new version)
- **Retry logic** at mesh level (complements application-level retry)
- **Authorization policies** for fine-grained RBAC at network level

---

## 16. CI/CD Pipeline

### Pipeline Overview (GitHub Actions)

```
Push to main/develop, or PR to main
              │
              ▼
1. detect-changes
   └── Path filter: which services changed?
       └── Output: ["order-service", "frontend"]

2. Parallel jobs (only changed services):
   ├── frontend
   │   ├── npm run lint
   │   ├── npm run build (TypeScript + Vite)
   │   ├── npm test (Vitest unit)
   │   └── npm run test:e2e (Playwright)
   │
   └── build (per service)
       ├── mvn clean package -pl {service} -am
       └── Upload Surefire test reports

3. integration-test (per service, Docker required)
   └── mvn failsafe:integration-test (Testcontainers)

4. docker-build (main branch only)
   ├── docker build → ghcr.io/org/ecommerce/{service}:{sha}
   └── docker push → GitHub Container Registry

5. deploy-staging (main branch only)
   ├── helm upgrade ecommerce ./helm --set image.tag={sha}
   └── Slack notification: success/failure
```

### Image Tagging Strategy

| Branch | Tag | Purpose |
|---|---|---|
| `main` | `{git-sha}`, `latest` | Production candidates |
| `develop` | `dev-{git-sha}` | Staging deployments |
| PR | `pr-{number}` | Preview environments |

---

## 17. Security Considerations

### Defense in Depth

Security is layered — no single failure can expose tenant data:

1. **Network layer:** Private subnets (infra-net), Kubernetes NetworkPolicies, Istio mTLS
2. **API layer:** Rate limiting (per-tenant), load shedding (max concurrent requests)
3. **Authentication layer:** JWT validation at gateway, token revocation at service
4. **Authorization layer:** RBAC (Spring Security), role claims in JWT
5. **Data layer:** Tenant filter (automatic WHERE clause), service-layer ownership checks
6. **Secrets layer:** Vault-managed secrets, zero plaintext secrets in config files

### Password Security

- Algorithm: BCrypt with cost factor 12 (~100ms per hash)
- This makes brute-force attacks computationally expensive (100ms × 10^9 = 3+ years for 1 billion attempts)
- Random per-password salts prevent rainbow table attacks
- Passwords are never logged, never returned in API responses

### JWT Security

- Secret rotated regularly (Vault policies can enforce rotation)
- Tokens are revocable via the `token` table (critical for logout + account lockdown)
- Short access token TTL (24h) limits blast radius of leaked tokens
- Refresh tokens (7d) require database lookup — immediately invalidatable

### Cross-Tenant Attack Prevention

An authenticated user cannot access another tenant's data even if they know the exact resource IDs:

```
User A (tenant: "tenant-A") requests: GET /api/v1/orders/order-from-tenant-B
  → JWT decoded: tenantId = "tenant-A"
  → TenantContextFilter activates Hibernate filter: tenant_id = "tenant-A"
  → SQL: SELECT * FROM customer_order WHERE order_id = ? AND tenant_id = "tenant-A"
  → Returns 0 rows (order belongs to tenant-B, not tenant-A)
  → Service returns 404 Not Found
```

### Idempotency

Payment service uses an idempotency key (unique constraint) to prevent double charges from network retries:
- Client sends `Idempotency-Key` header on payment creation
- If a payment with the same key already exists, the existing result is returned (no new charge)
- Database unique constraint on `(orderId, idempotencyKey)` — enforced at DB level even under concurrency

### Dependency Security

- GitHub Actions workflows include OWASP dependency-check on every PR
- Dependabot configured for automatic security patch PRs
- Container images scanned with Trivy for known CVEs

---

## 18. Developer Onboarding

### Prerequisites

```bash
# Required tools
java --version     # Java 17+ required
mvn --version      # Maven 3.8+
docker --version   # Docker 24+
node --version     # Node 20+ (frontend only)
```

### Quick Start (Local Development)

```bash
# Clone repository
git clone https://github.com/vanilson/ecommerce-microservice.git
cd ecommerce-microservice

# Start infrastructure only (no Spring services)
docker-compose up -d zookeeper kafka1 postgres-auth postgres-order \
                    postgres-product postgres-payment mongodb redis vault \
                    config-service discovery-service

# Build all modules
mvn clean package -DskipTests

# Start a specific service (IDE or terminal)
mvn spring-boot:run -pl order-service

# OR start everything
docker-compose up -d
```

### Running Tests

```bash
# Unit + BDD tests for all modules
mvn test

# Unit tests for a specific service
mvn test -pl authentication-service

# Run a specific test class
mvn test -pl customer-service -Dtest=CustomerServiceTest

# Run a specific test method
mvn test -pl customer-service -Dtest="CustomerServiceTest#Create"

# Integration tests (requires Docker for Testcontainers)
mvn verify -pl order-service

# Frontend tests
cd frontend
npm test          # Vitest unit tests
npm run test:e2e  # Playwright E2E (requires running backend)
```

### Service Ports Quick Reference

| Service | Port | URL |
|---|---|---|
| Frontend | 80 | http://localhost |
| API Gateway | 8222 | http://localhost:8222 |
| Auth Service | 8085 | http://localhost:8085/swagger-ui.html |
| Customer Service | 8090 | http://localhost:8090/swagger-ui.html |
| Product Service | 8082 | http://localhost:8082/swagger-ui.html |
| Order Service | 8083 | http://localhost:8083/swagger-ui.html |
| Payment Service | 8086 | http://localhost:8086/swagger-ui.html |
| Cart Service | 8091 | http://localhost:8091/swagger-ui.html |
| Notification Service | 8040 | http://localhost:8040 |
| Config Service | 8888 | http://localhost:8888 |
| Eureka | 8761 | http://localhost:8761 |
| Vault UI | 8200 | http://localhost:8200 |
| Grafana | 3000 | http://localhost:3000 |
| Prometheus | 9090 | http://localhost:9090 |
| Zipkin | 9411 | http://localhost:9411 |
| MailHog | 8025 | http://localhost:8025 |

### Package Structure Convention

Every service follows the same layered structure:

```
code.with.vanilson.{servicename}/
  ├── presentation/   → REST controllers (HTTP in/out)
  ├── application/    → Service classes, DTOs, Mappers
  ├── domain/         → JPA/MongoDB entities, enums
  ├── infrastructure/ → Repositories, Kafka consumers/producers
  ├── config/         → Spring @Configuration classes
  ├── exception/      → Custom exceptions + GlobalExceptionHandler
  └── kafka/          → Kafka event POJOs + producers/consumers
```

### Adding a New Service

1. Create Maven module in `pom.xml` reactor
2. Add `spring.config.import: optional:configserver:http://config-service:8888` to `bootstrap.yml`
3. Add `@EnableDiscoveryClient` to main class
4. Add `{service-name}.yml` to `config-service/src/main/resources/configurations/`
5. Add route to `gateway-service.yml`
6. Add Docker Compose service with `infra-net` + `services-net` networks
7. Add Kubernetes manifests (Deployment, Service, HPA, PDB)

---

*This document reflects the state of the codebase as of April 2026. For individual service API specifications, see the Swagger UI at each service's `/swagger-ui.html` endpoint or the files in `docs/api/`.*
