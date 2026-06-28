
<!--
  ╔══════════════════════════════════════════════════════════════════════════════╗
  ║           E-Commerce Microservices Platform — Release Notes                 ║
  ║                        backend-v2.0.0                                       ║
  ╚══════════════════════════════════════════════════════════════════════════════╝
-->

<div align="center">

# 🛒 E-Commerce Microservices Platform

## `backend-v2.0.0` — Full-Stack Production Release

> **Released:** June 24, 2026  &nbsp;·&nbsp;  **Previous tag:** `backend-v1.0.0` (April 8, 2026)  &nbsp;·&nbsp;  **86 commits** across 12 services + React SPA

[![Build](https://img.shields.io/badge/build-passing-brightgreen?style=flat-square)](.)
[![Tests](https://img.shields.io/badge/tests-115%2F115-brightgreen?style=flat-square)](.)
[![E2E](https://img.shields.io/badge/e2e-75%2F75-brightgreen?style=flat-square)](.)
[![Services](https://img.shields.io/badge/services-12-blue?style=flat-square)](.)
[![Stack](https://img.shields.io/badge/stack-Spring%20Boot%203.2%20%2B%20React%2019-orange?style=flat-square)](.)

</div>

---

## 🎯 Release Overview

`v2.0.0` delivers every capability listed as **"Next Steps"** in the initial release — and then some.

This is the first version to ship as a **complete, full-stack product**: a production-grade Spring Boot backend with event-driven Kafka orchestration, SaaS multi-tenancy, three-tier RBAC, a full React 19 SPA, distributed observability, and an automated CI/CD pipeline with staging deployment on Kubernetes.

---

## 🚀 What's New

### 1. 📨 Event-Driven Architecture — Apache Kafka

> _Was listed as "Kafka event streaming — planned" in v1.0.0. It is now live._

The entire order lifecycle is now driven by a **Choreography Saga** over Kafka, replacing the synchronous chain of REST calls.

| Topic | Producer | Consumer |
|---|---|---|
| `order-requested` | order-service | product-service |
| `inventory.reserved` | product-service | order-service |
| `inventory.insufficient` | product-service | order-service |
| `payment.authorized` | payment-service | order-service |
| `payment.failed` | payment-service | order-service |
| `order-topic.DLQ` | Kafka (auto) | notification-service |
| `payment-topic.DLQ` | Kafka (auto) | notification-service |

**Saga guarantees:**
- **At-least-once delivery** — manual offset commit only after the DB write succeeds
- **Idempotency** — `ProcessedEvent` in MongoDB deduplicates replayed messages in notification-service
- **Compensation** — inventory is automatically released on `payment.failed`
- **Outbox pattern** — `OutboxEvent` + `OutboxEventPublisher` in order-service ensure no event is lost even if Kafka is momentarily unavailable
- **Dead Letter Queue** — `DlqConsumer` in notification-service persists all failed events to MongoDB for ops review via `DlqEventRepository`

---

### 2. 🔐 Three-Tier RBAC (Admin / Seller / User)

> _Security was JWT-only in v1.0.0. v2.0.0 adds fine-grained role enforcement across all services._

| Role | Capabilities |
|---|---|
| **USER** | Browse catalog, manage cart, place orders, track payments, view history |
| **SELLER** | Publish/manage own products, view own order lines, track earnings |
| **ADMIN** | Full platform access, user/role management, audit log review |

**Implementation highlights:**
- `AdminBootstrapRunner` seeds a guaranteed admin account on first startup
- `AuditLog` entity in auth-service records all role mutations with actor/timestamp
- Role management REST endpoint (`/api/v1/auth/admin/roles`) under ADMIN guard
- `ProductSecurityConfig` enforces `SELLER` ownership: `GET /products/mine` returns only the caller's listings; write operations validate `createdBy`
- `GET /orders` is now **ADMIN-only**; sellers use `GET /orders/seller` (scoped to `order_line.seller_id`)
- RBAC design specification committed under `docs/` (ADR format)

---

### 3. 🏢 SaaS Multi-Tenancy

> _Was listed as "future Phase 4" in v1.0.0. The foundation is now live._

- **`tenant-context` shared library** — `TenantContext` holds `tenantId` in a `ThreadLocal`; `@EnableMultiTenancy` activates auto-configuration on any service
- **`TenantValidationFilter`** in the gateway rejects requests with a missing or unrecognized `X-Tenant-Id` before they reach downstream services
- **`TenantFeignInterceptor`** propagates `X-Tenant-Id` on all Feign service-to-service calls
- **`TenantHibernateFilterActivator`** activates Hibernate tenant filters for data-row isolation
- **Conditional auto-configuration** — `@ConditionalOnClass(name = "org.hibernate.Session")` prevents activation on services that don't use Hibernate (eliminates the startup `ClassNotFoundException` that appeared in v1.0.0)
- **Seller isolation** — all current UI users share `tenantId = default`; seller product/order scoping keys off `Product.createdBy` and `order_line.seller_id`, not the shared tenant, preventing cross-seller data leaks

---

### 4. 📊 Full Observability Stack

> _Was listed as "not yet integrated" in v1.0.0._

| Tool | Purpose | Access |
|---|---|---|
| **Zipkin** | Distributed tracing across all 12 services | `http://localhost:9411` |
| **Prometheus** | Metrics scraping (Actuator endpoints) | `http://localhost:9090` |
| **Grafana** | Dashboards + alerting | `http://localhost:3000` |

**Fixes shipped:**
- All services now resolve Zipkin by container hostname (`zipkin:9411`) instead of `localhost:9411`, eliminating the connection failures that appeared when running under Docker Compose
- Alertmanager configuration added (`alertmanager/`)
- Redis health indicator timeout raised to 10 s (`connect-timeout:10s` + `timeout:10s`) — the 2 s default caused false `RedisReactiveHealthIndicator` startup failures under Docker

---

### 5. 🖥️ React 19 Frontend SPA — Full Feature Parity

A production-quality single-page application built with **React 19, MUI v9, TanStack Query, Zustand, React Hook Form + Zod, Tailwind v4, Recharts, and Axios**.

**Role-based UI:**

| Feature | USER | SELLER | ADMIN |
|---|---|---|---|
| Product catalog + search | ✅ | ✅ | ✅ |
| Shopping cart | ✅ | — | — |
| Checkout + payment | ✅ | — | — |
| Order history + detail | ✅ | — | — |
| Seller dashboard | — | ✅ | — |
| Seller product management | — | ✅ | — |
| Seller order lines | — | ✅ | — |
| Admin dashboard | — | — | ✅ |
| Analytics & charts | — | ✅ | ✅ |

**Analytics dashboards (Recharts):**
- Revenue / GMV area chart (real order data)
- Order status breakdown donut chart
- Payment method distribution donut chart
- Daily payments bar chart

**Navigation & UX:**
- Responsive layout with `md`-breakpoint hamburger drawer
- Context-aware breadcrumbs and back-navigation in seller order detail
- Per-order shipping address captured at checkout (no longer read from profile snapshot)
- Payment method selection with no default; Review button gated until method chosen
- Cart restricted to `USER` role — `SELLER`/`ADMIN` layouts never trigger cart API calls

**Auth stability:**
- Auth store migrated to `sessionStorage` — eliminates the cross-tab role leak where `Ctrl+R` in a seller tab would flip the role to `USER`
- Login and register flows updated; E2E test fixtures updated to match

---

### 6. ⚡ Auth Hot-Path Decoupling

Login and registration are now strictly **fast paths** — the response time is bounded by BCrypt + one DB round-trip only.

- **`CustomerProvisioning.ensureCustomerProfile()`** runs `@Async` on a dedicated bounded thread pool (`authSideEffectsExecutor` in `AsyncConfig`, `DiscardPolicy` so it never falls back to the request thread)
- The provisioning call hits customer-service's `/internal` endpoint — idempotent, no-ops if the profile already exists
- **No Kafka in the auth path** — the `user.registered` Kafka producer was removed; do not reintroduce a broker dependency into login/register
- **BCrypt cost optimized** — cost factor reduced from 12 → 10 (configurable); rehash-on-login transparently upgrades legacy hashes. Expected login time on this hardware: ~375 ms vs ~1 500 ms at cost 12

---

### 7. 🛡️ Gateway Resilience

- **`resilience4j.timelimiter`** added to `gateway-service.yml` — the missing configuration was the root cause of persistent 503s (Spring Cloud Circuit Breaker defaulted to 1 s, cancelling cold ~1.5–5 s downstream calls before they could respond)
- Gateway HTTP client pool tuned to handle idle-connection resets after long periods of inactivity
- Circuit breaker slow-call thresholds raised to 3–4 s for product-service to account for cold Redis cache

---

### 8. 📦 Order Service Improvements

| Improvement | Detail |
|---|---|
| **Per-order shipping address** | Flyway `V1_13` adds `shipping_*` columns; address captured from checkout form, stored per order — never inferred from profile |
| **Seller order lines** | Flyway `V1_10` adds `order_line.seller_id`; `GET /orders/seller` returns only the caller's lines (DISTINCT + ORDER BY fix for Postgres) |
| **Order invoice detail** | Full invoice view: customer block, line totals, IVA/tax breakdown (23% inclusive), seller business profile sourced from auth-service |
| **Idempotency** | Orders accept `Idempotency-Key` header; duplicate submissions within the TTL window return the original response without creating a second order |
| **Saga consumer** | `OrderSagaConsumer` handles `payment.authorized`, `payment.failed`, `inventory.insufficient` with manual Kafka acknowledgement |

---

### 9. 🔍 Catalog Search

Full-text product search implemented across the catalog (`P2-implement-catalog-search`):
- Backend: parameterized JPQL with `LOWER(LIKE)` on name + description; exposed on product-service
- Gateway: route + circuit breaker wired
- Frontend: search bar in `ProductCatalog` with debounce; empty-state and loading feedback

---

### 10. 🏗️ CI/CD Pipeline — Full Automation

```
detect-changes
  └── frontend (build + lint)
        └── build (Maven multi-module)
              └── integration-tests (Testcontainers)
                    └── security-scan
                          └── build-push (Docker Hub)
                                └── deploy-staging (Kind + Helm)
```

- **Change detection** — jobs skip if their module has no diff; reduces pipeline wall time on partial changes
- **Staging deployment** — Kind (Kubernetes in Docker) cluster provisioned via `helm/kind-action@v1.10.0`; Helm chart deploys all services to `staging` namespace
- **KUBECONFIG handling** — explicit export prevents "cluster unreachable" failures in multi-step Helm jobs
- **Maven retries** — `config-service/Dockerfile` retries Maven Central downloads up to 5× to survive transient 403s
- **Playwright E2E** — 75 end-to-end scenarios run against the built frontend as a CI gate

---

### 11. 🧪 Test Coverage

| Service | Unit + BDD tests | Notes |
|---|---|---|
| authentication-service | 115 / 115 ✅ | BCrypt cost + rehash tested |
| order-service | 92 / 92 ✅ | Saga, idempotency, shipping |
| product-service | 98 / 98 ✅ | RBAC, catalog search, images |
| All services (aggregate) | **Green** | Surefire + Failsafe + Cucumber |
| Frontend (Playwright E2E) | 75 / 75 ✅ | Auth fixed to sessionStorage |

---

### 12. 📄 Technical Documentation

- `docs/adr/` — Architecture Decision Records (RBAC model, saga approach, tenant-context design)
- `docs/api/` — OpenAPI annotations per service; Swagger UI at `http://localhost:<port>/swagger-ui.html`
- `docs/architecture/` — Updated Mermaid topology diagrams, Kafka topic/consumer-group tables, gateway filter chain order, Helm/Kind layout, runbooks
- `docs/technical/` — Deep-dive on multi-tenancy, saga compensation, BCrypt tuning, Redis serializer design

---

## 🐛 Critical Bug Fixes

| Bug | Impact | Fix |
|---|---|---|
| Gateway 503 on cold start | Every cold checkout failed with 503 + Retry-After 30 | Added `resilience4j.timelimiter` to gateway config |
| Saga lost `tenantId` / `orderReference` | Every payment hit NOT NULL violation → all orders stuck `PENDING` | Saga event POJOs now carry all required fields |
| Cart 404 for logged-in users | GET /cart returned 404 instead of empty cart | Cart endpoint returns `{}` (HTTP 200) when empty; frontend shows empty state |
| Order duplication on retry | Double-click / false 503 retry created duplicate orders | `Idempotency-Key` header deduplicates within TTL |
| Order lines 403 for owners | Customers got 403 fetching their own order lines | Authorization fixed: owners always allowed |
| Seller page drawer freeze | `/seller` frozen — MUI temporary Drawer's `useEffect` close locked backdrop/scroll | Fixed layout to `open={sidebarOpen && !isMobile}` |
| Cart fetch for Seller at login | Seller login triggered cart 503 (no cart for SELLER role) | Cart fetch gated on `role === 'USER'` in both layouts |
| Cross-tab role leak | Refreshing seller tab flipped role to USER (localStorage shared) | Auth store migrated to `sessionStorage` |
| Checkout address not saved | Shipping address discarded at submit; order showed "not provided" | Address posted in `OrderRequest`; persisted via Flyway V1_13 |
| BCrypt cost 12 login latency | Login took ~1.5 s on this CPU (Docker overhead + cost 12) | Default cost lowered to 10; rehash-on-login for legacy accounts |
| Zipkin `localhost` in Docker | Tracing spans silently dropped inside Docker Compose network | All services now reference `zipkin` container hostname |
| Redis serializer mismatch | CustomerService Redis cache threw `ClassCastException` on read | `PageRequestMixin` + correct `ObjectMapper` bean wired |
| Tenant filter `ClassNotFoundException` | `TenantHibernateFilterActivator` crashed on non-JPA services | Guard changed to `@ConditionalOnClass(name = "org.hibernate.Session")` |
| Product image upload | Images were silently dropped; product detail showed placeholder | Multipart upload pipeline fixed end-to-end |
| Spurious 403 on public routes | Unauthenticated catalog browse returned 403 | `ProductSecurityConfig` `permitAll` paths corrected |

---

## ⚠️ Breaking Changes

| Area | Change |
|---|---|
| **Auth store** | Migrated from `localStorage` to `sessionStorage`. Any E2E test or automation that seeds auth via `localStorage` will break — update to use `sessionStorage.setItem('auth', ...)` |
| **Order creation** | `POST /api/v1/orders` now requires `Idempotency-Key` header for safe retries. Missing header still works but the idempotency guarantee is lost |
| **GET /orders** | Now **ADMIN-only**. Sellers must use `GET /orders/seller`; customers use `GET /orders/customer` |
| **BCrypt rehash** | Accounts created under v1.0.0 (cost 12) are silently rehashed to cost 10 on next login. No action needed — transparent to users |
| **Kafka dependency** | Order, payment, product, and notification services now require Kafka. A `docker-compose up -d` without the Kafka/Zookeeper containers will cause these services to fail on startup |

---

## 🛣️ Upgrade Guide

```bash
# 1. Pull latest code
git fetch --all
git checkout main

# 2. Rebuild all backend modules
mvn clean package -DskipTests

# 3. Run DB migrations (new Flyway versions: V1_10, V1_13)
# Flyway runs automatically on service startup — nothing manual needed.

# 4. Rebuild and restart the full stack
docker-compose down
docker-compose up -d --build

# 5. Rebuild frontend
cd frontend
npm install
npm run build

# 6. Verify
curl -s http://localhost:8222/actuator/health | jq .
```

> **Note on Kafka:** Kafka and Zookeeper must be running before order-service, payment-service, product-service, and notification-service start. `docker-compose up -d` starts them in the correct order via `depends_on`.

---

## 📊 Platform Statistics

| Metric | Value |
|---|---|
| Backend services | 12 Spring Boot modules |
| Frontend | React 19 SPA (`frontend/`) |
| Total commits since v1.0.0 | 86 |
| Kafka topics | 7 (+ 2 DLQ) |
| Flyway migrations | 13 (V1_1 → V1_13) |
| PostgreSQL databases | 4 (auth, order, payment, product) |
| MongoDB collections | customer, product, notification, dlq, processedEvents |
| Redis usage | Cart + customer cache |
| Service ports | 8040, 8082, 8083, 8085, 8086, 8090, 8091, 8095, 8222, 8761, 8888 |

---

## 🔭 What's Next — v2.1.0 Candidates

- [ ] Kubernetes production deployment (currently staging-only via Kind)
- [ ] Elasticsearch full-text search (replace JPQL `LIKE`)
- [ ] Real SaaS multi-tenancy (multiple `tenantId` values, tenant onboarding flow)
- [ ] gRPC service-to-service communication for hot paths
- [ ] Rate limiting per tenant / per user in the gateway
- [ ] WebSocket order status push (replace polling)

---

## 🙌 Final Note

`backend-v2.0.0` completes the architectural vision set out in v1.0.0. The platform now ships as a **cohesive, end-to-end system**: events flow through Kafka, tenants are isolated, roles are enforced, every service is observable, the frontend is fully functional for all three user roles, and every change is tested and deployed through an automated pipeline.

---

<div align="center">

**Built with Spring Boot 3.2 · React 19 · Apache Kafka · Kubernetes · Helm**

*Semantic Versioning — future releases will maintain backward compatibility*

</div>
