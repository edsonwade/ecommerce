# Development History & Incident Log

**Audience:** senior engineers joining the project, technical stakeholders, auditors/investors.
**Scope:** what was built, in what order, every non-trivial production incident hit along the way, and the exact technical fix applied — with file references so a reader can verify each claim against the code.

This document is deliberately narrative and incident-driven. For the current-state architecture reference, see [../api/API.md](../api/API.md), [../adr/](../adr/) and the root [README.md](../../README.md). For a critique of the resulting architecture, see [02-architecture-tradeoffs.md](02-architecture-tradeoffs.md).

---

## Part 1 — Build Phases

The system was not designed top-down and built once. It was built incrementally, service by service, then hardened through a long sequence of QA passes that each surfaced a new class of distributed-systems bug. That sequence is itself part of the system's design record — most of the resilience patterns in place today exist *because* an earlier, simpler version of the service failed in production-like conditions first.

### Phase 0 — Scaffolding (one service at a time)
`config-service` → `discovery-service` → `customer-service` (MongoDB) → `product-service` (PostgreSQL) → `order-service` → `payment-service` + `order-line` → `notification-service` + `gateway-api-service`. Each service was merged individually through its own PR, with its own Dockerfile, before the next was started. This is why the reactor build order in the root `pom.xml` still reads top-to-bottom as a dependency chain: discovery → config → domain services → gateway.

### Phase 1 — Stabilization
First full-stack pass: fixed cross-service Docker networking (services attempting to reach `localhost:9411` for Zipkin instead of the `zipkin` container hostname — classic container-vs-host DNS confusion), consolidated `.gitignore`/build artifacts, and got CI green across all modules.

### Phase 2 — BDD Test Coverage
Cucumber suites added for every service (`src/test/resources/features/`), Gherkin scenarios wired to Spring-context step definitions. This phase established the four-test-layer convention still enforced today: unit → controller slice → integration (Testcontainers) → BDD.

### Phase 3 — Advanced Resilience Patterns
Circuit breakers, retry, and the choreography saga skeleton (`OrderSagaConsumer`, outbox pattern) were introduced. See [ADR-001](../adr/ADR-001-choreography-saga.md) and [ADR-002](../adr/ADR-002-transactional-outbox.md) for the reasoning.

### Phase 4 — SaaS Multi-Tenancy
`tenant-context` (shared library) and `tenant-service` were introduced as the *first* modules in the reactor build order — every other service depends on `tenant-context` for its `TenantFilter`/`TenantValidationFilter` machinery. This phase is why the current multi-tenancy story is architecturally sound but **operationally inert** (see [Tradeoffs §Multi-Tenancy](02-architecture-tradeoffs.md#multi-tenancy-built-but-not-actually-multi-tenant)) — all UI-created users share `tenantId = default`, so the filter never actually partitions anyone's data today.

### Phase 5 — RBAC & Security Hardening
`AuditLog` entity, admin bootstrap runner, `Product` ownership fields, RBAC design spec (`docs/*rbac*`). This is where `Product.createdBy` was introduced — originally as an audit column, later repurposed as the *de facto* tenant boundary (see below).

### Phase 6 — Frontend Build-Out
React 19 + Vite SPA built against the gateway. Iterated through navigation bugs, dashboard overhaul, and role-based layout issues (documented per-incident below).

### Phase 7 — Production QA Marathon (the largest phase by commit count)
A sustained "run it like a real user, fix what breaks" pass. This is where most of the incidents in Part 2 were found and fixed: saga gaps (missing inventory compensation, missing confirmation notifications), seller/tenant data leaks, cart/order/payment race conditions, login latency, spurious permission errors, product image and catalog search bugs. Each fix shipped with its own unit + controller + integration + BDD tests per the project's [test-quality](../../.claude/skills/test-quality) convention.

### Phase 8 — HA / Zero-Downtime Deploy
`docker-compose.scale.yml` overlay + `nginx-edge` introduced: second instances of `gateway-api-service` and `frontend`, `ip_hash` sticky sessions, rolling-restart scripts. Root-caused and fixed a Zipkin-span-export-blocking-the-event-loop bug that caused 27% request failure during rolling restarts before tracing was made overlay-conditional.

### Phase 9 — Observability Hardening & Documentation
Prometheus `/actuator/prometheus` scrape 401/404 fixed across 8 services (see below), full README/API.md documentation pass, operational runbooks published.

---

## Part 2 — Per-Service Technical Deep Dive

For each service: what it owns, and every non-trivial problem found in it with the actual fix.

### `tenant-context` (shared library, no Spring Boot app)

**Responsibility:** `TenantContext` (ThreadLocal tenantId holder), `TenantFeignInterceptor` (propagates `X-Tenant-Id` on outbound Feign calls), `TenantHibernateFilterActivator` (activates a Hibernate `@Filter` for row-level tenant isolation), `TenantValidationFilter` (gateway-side rejection of requests with a missing/invalid `X-Tenant-Id`).

**Problem — auto-configuration silently no-op'd in JPA-less contexts.**
`TenantContextAutoConfiguration.HibernateTenantFilterConfig` originally keyed its `@ConditionalOnClass` off `jakarta.persistence.EntityManager`. `customer-service` (MongoDB-only) carried a stray `persistence-api` dependency on its classpath, so the condition matched even though there was no Hibernate `Session` to filter — the auto-configuration bean tried to activate a Hibernate filter with no session factory behind it.
**Fix:** condition changed to `@ConditionalOnClass(name = "org.hibernate.Session")` — [`tenant-context/.../TenantContextAutoConfiguration.java:74`](../../tenant-context/src/main/java/code/with/vanilson/tenantcontext/TenantContextAutoConfiguration.java). This is the correct signal because only services with an actual Hibernate `SessionFactory` (the PostgreSQL-backed ones) should ever try to activate a session-level filter.

---

### `authentication-service`

**Responsibility:** JWT issuance/validation, RBAC (`USER`/`SELLER`/`ADMIN`), refresh-token rotation, registration, password reset, account self-service (GET/PATCH/DELETE `/account`).

**Problem — login latency ≈1.5s per request, root-caused to BCrypt.**
Every login paid one BCrypt hash at cost factor 12 on a resource-constrained Docker host. Gateway, tenant validation, and Kafka were all ruled out first via distributed tracing (Zipkin) before the actual bottleneck was found in application logs.
**Fix:** cost factor made configurable (`security.bcrypt.strength`, default **10** — Spring Security's own default, still OWASP-acceptable) — [`SecurityConfig.java:87`](../../authentication-service/src/main/java/code/with/vanilson/authentication/config/SecurityConfig.java). Because lowering the cost breaks verification of *existing* cost-12 hashes, `AuthService` transparently re-hashes a user's password at login once it detects the stored hash's embedded cost differs from the configured strength — [`AuthService.java:171-195`](../../authentication-service/src/main/java/code/with/vanilson/authentication/application/AuthService.java). Spring's own `upgradeEncoding` only migrates *upward*, so this downward-migration path is bespoke.

**Problem — customer-profile provisioning was a hidden blocking call on the hot path.**
Every register/login was doing a synchronous side-effect against `customer-service` to ensure a customer profile existed, adding a network hop (with its own failure modes) to what should be the fastest path in the system.
**Fix:** `CustomerProvisioning.ensureCustomerProfile()` now runs `@Async` on a bounded `authSideEffectsExecutor` (`AsyncConfig`) with a `DiscardPolicy` — it *cannot* fall back onto the request thread under load. It is fail-open (a dropped provisioning call doesn't fail the login) and idempotent (customer-service's `/internal` endpoint no-ops if the profile already exists). A previous design used a `user.registered` Kafka event for this; it was deliberately removed — introducing a broker dependency into the single fastest, most security-sensitive path in the system was judged not worth the decoupling benefit.

**Problem — Prometheus scraping `/actuator/prometheus` returned 401.**
Spring Security's default chain required authentication for all actuator endpoints, including the one Prometheus itself scrapes every 15s.
**Fix:** `/actuator/prometheus` (and only that path — never a wildcard `/actuator/**`) added to the `permitAll` allowlist in every service's `SecurityConfig`. The allowlist is an explicit array literal, verified by a dedicated `SecurityConfigTest` per service so a future refactor can't silently widen it back to a wildcard.

---

### `customer-service`

**Responsibility:** customer profile CRUD, MongoDB-backed, cached via Redis.

**Problem — Redis serializer stored/retrieved objects as raw Java serialization, not JSON.**
Bare default `RedisTemplate` configuration used JDK serialization, which is fragile across class changes and unreadable for ops debugging.
**Fix:** explicit `Jackson2JsonRedisSerializer` wiring in `CustomerServiceConfig.java`.

**Problem — BDD runner's `@SpringBootTest` context never loaded.**
Missing JWT test secret + an unmocked live Redis dependency meant the Cucumber runner failed at Spring context bootstrap, before a single scenario ran — the failure looked like "0 scenarios" rather than a clear error.
**Fix:** test bootstrap only — added the missing JWT secret property and mocked Redis in the test slice. No production code changed. **Lesson recorded:** always check context-load prerequisites first when a module's BDD runner reports zero scenarios; it's rarely the feature files.

**Problem — account soft-delete and profile sync with auth-service.**
`AccountSettingsPage` (frontend) drove `DELETE /account` (auth) which needed to also tombstone/sync the customer profile.
**Fix:** async sync from auth → customer-service's `/internal` `PUT`/`DELETE` endpoints, same fail-open/idempotent pattern as provisioning.

---

### `product-service`

**Responsibility:** catalog, pessimistic-locked inventory reservation, seller-scoped ownership.

**Problem — Flyway migration failures left permanent `failed` markers requiring manual DB surgery.**
A bad migration would leave `flyway_schema_history` in a failed state; the only recovery path was a manual `psql DELETE` against the history table or a full volume wipe.
**Fix:** self-healing Flyway strategy — `flyway.repair()` runs before every `flyway.migrate()` on startup, clearing failed-migration markers automatically — [`ProductServiceConfig.java:65-77`](../../product-service/src/main/java/code/with/vanilson/productservice/config/ProductServiceConfig.java). Recovery from a bad migration is now: fix the `.sql`, rebuild, `up -d`. No manual DB intervention.

**Problem — seller isolation didn't exist; `Product.createdBy` was audit-only.**
Because all UI tenants share `tenantId = default`, the tenant Hibernate filter provides zero isolation between sellers. A seller's catalog page could show every seller's products.
**Fix:** `Product.createdBy` (added in `V5__add_product_ownership_fields.sql`) repurposed as the actual ownership boundary. `GET /products/mine` filters on it; `ProductSecurityConfig`/`ProductService` enforce it on mutations. **This is documented explicitly as a workaround, not a design choice** — see [Tradeoffs](02-architecture-tradeoffs.md#multi-tenancy-built-but-not-actually-multi-tenant).

**Problem — `GET /orders/seller` 500 (cross-service symptom, root cause here).**
Hibernate compiled a `SELECT DISTINCT` with an `ORDER BY` on a foreign-key column not present in the select list; PostgreSQL legally rejects this. Fixed by selecting the full `Order` entity instead of a projection.

**Problem — circuit breaker + timeout mistuned in early passes.**
`product-cb.slow-call-duration-threshold` was left at 2s when cold calls routinely took 3-5s under Docker-Desktop CPU pressure, tripping the breaker on legitimate (if slow) requests. Root cause was compounded by a separate, more severe bug at the gateway (see gateway section).

---

### `order-service`

**Responsibility:** saga initiator, outbox event publisher, order/order-line persistence, per-order shipping address, seller-scoped order-line visibility.

**Problem — no atomic "write DB + publish event" guarantee.**
A naive implementation would write the order row, then call Kafka directly — a crash between the two steps loses the event forever, or publishes an event for a transaction that later rolls back.
**Fix:** [ADR-002](../adr/ADR-002-transactional-outbox.md) — `OutboxEvent` written in the *same* local transaction as the `Order` row; a separate `OutboxEventPublisher` polls for `PENDING` rows and publishes them, marking them `SENT` only after a successful Kafka ack.

**Problem — saga was missing its compensation and completion paths.**
Two gaps existed simultaneously: (1) no inventory compensation (stock was never released back) when `payment.failed` arrived after `inventory.reserved` had already decremented it, and (2) no notification was ever sent when an order actually reached `CONFIRMED` — only failure paths notified anyone.
**Fix:** `OrderSagaConsumer` extended to trigger a stock-release event on `payment.failed`/`inventory.insufficient`, and to fire the confirmation notification path on `payment.authorized`. Manual Kafka acknowledgement (offset committed only after the DB write) makes both paths safe to redeliver.

**Problem — saga events dropped critical correlation fields under certain payloads.**
Payment failures were happening for *every* order because saga events were missing `orderReference`/`tenantId`/`orderId` in some code paths, tripping `NOT NULL` constraints in `payment_db`. A second, unrelated bug compounded this: the gateway's HTTP client pool held stale connections after idle periods, producing spurious 503s that looked related but weren't.
**Fix:** event DTOs corrected to always populate the three fields; gateway HTTP client pool given proper eviction/keep-alive tuning.

**Problem — non-idempotent `POST /orders` under client retry.**
A client that legitimately retried after a false-503 (see gateway TimeLimiter issue below) created duplicate orders.
**Fix:** `Idempotency-Key` header support in `OrderController`/`OrderService` — a duplicate key within the dedup window returns the original order's 202 response instead of creating a new one.

**Problem — seller order-detail leaked other sellers' order lines and totals.**
A seller viewing an order detail page saw every line item (including other sellers' and system-owned lines) and the full order total, not their own slice.
**Fix:** `findAllByOrderId` scoped to `seller_id` (`ADMIN`/owning-buyer still see everything); frontend derives the seller's own summary/total purely from the lines it's actually allowed to see.

**Problem — `GET /auth/sellers/{id}` 500 for seed data.**
Seed products used the string `"system"` as `createdBy`. The frontend passed that straight into a `@PathVariable Long id` seller-profile lookup, producing an unhandled type-mismatch 500 instead of a clean 4xx.
**Fix:** frontend filters to numeric owners before calling the endpoint; the auth-service handler additionally returns a proper 400 for non-numeric input rather than 500.

**Problem — checkout address was silently discarded.**
The address entered at checkout was never persisted; the order's shipping address was read only from the customer's profile snapshot, so it showed "not provided" whenever the profile had no address on file — even though the user typed one in at checkout.
**Fix:** shipping address persisted per-order (`V1_13` Flyway migration), threaded through `OrderRequest` → `Order` → mapper, and sent by the frontend.

---

### `payment-service`

**Responsibility:** idempotent payment authorization keyed to `orderReference`.

**Problem — duplicate Kafka deliveries could double-charge.**
At-least-once delivery semantics mean the same `inventory.reserved` event can arrive twice.
**Fix:** idempotency key derived from `orderReference`; a duplicate is detected and the original result returned rather than re-processed.

**Problem — checkout default payment method was silently `CREDIT_CARD`.**
The frontend defaulted the payment-method selector to `CREDIT_CARD` even when the user hadn't chosen anything, so unintentional charges could be attributed to the wrong method.
**Fix:** default changed to empty, with the checkout "Review" button gated until a method is explicitly selected.

---

### `cart-service`

**Responsibility:** Redis-backed, 24h-TTL shopping cart.

**Problem — Redis client timeouts too aggressive for Dockerized Redis.**
A 2-second connect/command timeout intermittently tripped `RedisReactiveHealthIndicator` at container startup, before Redis had fully initialized inside Docker's overlay network.
**Fix:** standardized on 10s connect-timeout / 10s command-timeout for all Redis clients in this stack — recorded as a durable rule, not a one-off tweak, because the failure mode recurs any time a new service adds a Redis client with framework defaults.

**Problem — cart fetched for non-cart roles caused a freeze.**
`PublicLayout` (which also hosts the login/register pages) fetched the cart for *any* authenticated user, including `SELLER`/`ADMIN`, who have no cart. A 503/404 from that fetch, combined with a separate MUI Drawer bug (below), froze the seller UI entirely.
**Fix:** cart fetch gated to `role === 'USER'`; 404/503 responses swallowed with no retry for non-cart roles.

**Problem — cart not cleared after successful payment; GET on an empty cart returned 404 instead of an empty cart.**
**Fix:** cart clear wired into the post-payment success path; `GET` on a nonexistent cart returns an empty cart object rather than 404 (empty cart is a valid state, not an error).

---

### `notification-service`

**Responsibility:** Kafka consumer for order/payment topics, email dispatch, DLQ persistence, idempotent event processing.

**Problem — poison-record hot loop pinned a CPU core at ~50-122%.**
An undeserializable Kafka record (a payload that didn't match the configured Avro/JSON type) caused the consumer to throw, Kafka to redeliver the same offset, and the consumer to throw again — forever, at full CPU, without ever reaching the DLQ.
**Fix:** `ErrorHandlingDeserializer` wrapping the real deserializer + `VALUE_DEFAULT_TYPE` configured + a dedicated `DeserializationErrorHandler` ([`notification-service/.../kafka/DeserializationErrorHandler.java`](../../notification-service/src/main/java/code/with/vanilson/notification/kafka/DeserializationErrorHandler.java)) that routes the poison record to the DLQ instead of retrying it in place. Producers were also given explicit type headers so the consumer-side deserializer has an unambiguous target type. This is the single highest-severity self-inflicted incident in the project's history — a logic bug that manifested as an infrastructure/CPU problem, which is why it's called out here in detail.

**Problem — DLQ existed as a topic but nothing consumed it.**
Failed events vanished into `payment-topic.DLQ`/`order-topic.DLQ` with no operational visibility.
**Fix:** `DlqConsumer` persists DLQ events to MongoDB via `DlqEventRepository` for ops review — a queryable audit trail instead of a black hole.

**Problem — duplicate at-least-once Kafka deliveries could send duplicate emails.**
**Fix:** `ProcessedEvent` + `ProcessedEventRepository` (MongoDB) dedupe by event ID before the email send, not after — the idempotency guard was originally placed *after* the send and had to be moved *before* it, since a crash between "email sent" and "guard recorded" would otherwise resend on redelivery.

**Problem — a health-check indicator locked out the platform's real email provider.**
`SmtpHealthIndicator` ([`notification-service/.../health/SmtpHealthIndicator.java`](../../notification-service/src/main/java/code/with/vanilson/notification/health/SmtpHealthIndicator.java)) performed a *full SMTP AUTH handshake* against the shared Mailtrap sandbox inbox roughly every 18 seconds as part of its health poll. Mailtrap's brute-force protection locked the inbox after repeated auth attempts (`535 Too many failed login attempts`), and because the mail-send path is fail-open, real password-reset emails silently stopped arriving with no visible error to the user or the logs.
**Fix:** the indicator is now gated off by default (mail/SMTP health disabled), and the lock — which does **not** expire on its own — required a Mailtrap credential reset to clear. **Lesson recorded:** a health check that authenticates against a real, rate-limited third party is itself a production risk; health checks against external providers should probe connectivity, not perform the exact operation the provider rate-limits.

---

### `gateway-api-service`

**Responsibility:** single ingress, `TenantValidationFilter` → `JwtAuthenticationFilter` → `LoadSheddingFilter` → `RequestIdFilter` chain, per-route circuit breaking, rate limiting.

**Problem — persistent, unexplained 503s (`Retry-After: 30`) across many sessions.**
Every earlier attempt tuned circuit-breaker thresholds, connection pools, and slow-call windows — all of which turned out to be irrelevant. The actual cause: Spring Cloud CircuitBreaker wraps every Resilience4j breaker in a `TimeLimiter` whose **default timeout is 1 second**. Cold calls to product/cart/order genuinely take 1.5–5s on this Docker host, so the limiter cancelled the call at 1s — *before it ever reached the downstream service*, which is why product-service's own logs showed nothing wrong.
**Fix:** explicit `resilience4j.timelimiter` config with `timeout-duration: 20s` — [`config-service/.../gateway-service.yml:306-326`](../../config-service/src/main/resources/configurations/gateway-service.yml). The in-code comment at that location is deliberately blunt about the root cause, because this bug survived multiple unrelated tuning passes before being found.

**Problem — stale pooled HTTP connections produced 503s after idle periods.**
Compounded the saga-propagation bug above; the connection pool wasn't evicting idle connections aggressively enough for a low-traffic dev/test environment.
**Fix:** pool eviction/keep-alive tuning in the gateway HTTP client config.

**Problem — Zipkin span export blocked the gateway's event loop during rolling restarts.**
During HA rolling-restart testing, 27% of requests timed out. Root cause: Zipkin trace export is a blocking call inside a reactive (WebFlux) gateway; under the added load of an in-flight rolling restart, span export backpressure stalled the entire event loop.
**Fix:** tracing disabled specifically in the HA overlay's environment (base compose still traces normally) — a deliberate scope-limited fix rather than removing tracing altogether, since tracing is valuable outside of rolling-restart windows.

---

### Frontend (`frontend/`)

**Responsibility:** React 19 + Vite SPA for customer/seller/admin roles.

**Problem — a "freeze fix" for one bug silently broke seller/admin navigation.**
An earlier fix for a scroll-lock/backdrop freeze (below) hid the seller/admin sidebar entirely, blocking the "who bought my product" page.
**Fix:** sidebar visibility restored via an `md`-breakpoint + hamburger-menu rule; cart UI removed from `SELLER`/`ADMIN` roles (they don't have carts).

**Problem — MUI temporary `Drawer` auto-opened below the `lg` breakpoint, then closed itself via a `useEffect`, hanging the backdrop/scroll-lock.**
**Fix:** `Drawer` open state changed to `open={sidebarOpen && !isMobile}` instead of an effect-driven toggle.

**Problem — `Ctrl+R` on a seller tab silently became a `USER` session.**
Auth state lived in `localStorage`, which is shared across *all* browser tabs for the same origin — a customer tab and a seller tab logged in as different roles would stomp on each other's session on any refresh.
**Fix:** `auth.store` moved from `localStorage` to `sessionStorage`, isolating auth state per tab. This required a corresponding fix to the Playwright E2E specs, which had seeded auth via `localStorage` and consequently failed 100% of the time (every guarded route redirected to `/login`) until updated to seed `sessionStorage` instead.

**Problem — seller order-detail "Back" button navigated to the customer order-history page.**
**Fix:** added a seller-specific route (`/seller/orders/:id`) and made the back-navigation context-aware instead of hardcoded to the customer path.

**Problem — seller profile save didn't confirm or navigate anywhere.**
**Fix:** save now resets the form and navigates to `/seller` dashboard on success.

---

### Infrastructure & Observability

**Problem — `promtail` pinned at ~122% CPU.**
Root cause traced through three layers: (1) 7 services returned 401 on `/actuator/prometheus` (see authentication-service section) → (2) Prometheus's `ServiceDown` alert fired falsely for all of them → (3) the Alertmanager Slack receiver was a placeholder webhook that, on failure, logged the *entire* outgoing HTML error page on every retry, every service, every evaluation interval → a single log file grew to 189MB, which promtail then had to continuously tail and ship.
**Fix:** the actual fix was the 401 fix itself (see above); `alertmanager.yml` and the compose file were additionally hardened so a placeholder receiver failure can't spiral into unbounded logging again.

**Problem — HA rolling restarts needed live proof, not a "should work" claim.**
**Fix:** `scripts/rolling-restart-proof.ps1` drives real traffic against the `nginx-edge` entrypoint through both gateway instances restarting in sequence; the last verified run showed 348/348 requests (100%) succeeded across both restarts.

---

## Cross-Cutting Lessons

These recur often enough across the incidents above that they're worth stating once, generically, rather than per-service — they are expanded into a checklist in [03-saas-engineering-playbook.md](03-saas-engineering-playbook.md):

1. **A blocking call hidden inside a "fast path" will eventually be found the hard way** (BCrypt cost 12 on login, synchronous customer-provisioning on register, Zipkin span export inside the gateway event loop).
2. **A default timeout you didn't set is still a timeout** — the gateway's invisible 1s `TimeLimiter` cost multiple debugging sessions before anyone looked for a value that was never explicitly configured.
3. **A health check against a real external dependency can itself become an outage** (Mailtrap SMTP AUTH flood).
4. **Poison messages need a designed failure path before they need a retry policy** — without `ErrorHandlingDeserializer` + DLQ, "at-least-once" delivery becomes "infinite, at full CPU."
5. **Anything keyed by browser storage shared across tabs will eventually corrupt a multi-role dev/QA session** (localStorage auth state).
6. **A fix for bug A can silently reintroduce bug B** if the two features share a UI surface (the freeze-fix that hid the seller sidebar).
