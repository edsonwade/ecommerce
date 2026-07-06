# Architecture Trade-offs & Honest Critique

This document exists to answer three questions plainly, without marketing language: **why is the system built this way, what is genuinely not ideal about it, and what would change if this were being re-architected for a real production SaaS launch instead of a portfolio/learning project.**

It should be read alongside the [ADRs](../adr/) (which record *what* was decided and the alternatives considered at the time) and [01-development-history.md](01-development-history.md) (which records *what broke* and *how it was fixed*). This document adds the layer neither of those cover: **what's still wrong, on purpose or otherwise, even after every fix.**

---

## 1. Why This Architecture

- **Choreography saga over Kafka** ([ADR-001](../adr/ADR-001-choreography-saga.md)) — chosen because the order flow spans four independently-owned databases with no shared transaction boundary, and an orchestrator would have reintroduced a single point of coupling the whole design is trying to avoid.
- **Transactional outbox** ([ADR-002](../adr/ADR-002-transactional-outbox.md)) — chosen because "write to DB, then publish to Kafka" is not atomic, and a crash between the two steps either loses an event or publishes one for a transaction that never committed.
- **Polyglot persistence** ([ADR-003](../adr/ADR-003-polyglot-persistence.md)) — PostgreSQL where ACID and relational integrity matter (auth, order, product, payment), MongoDB where schema flexibility and document-shaped reads matter (customer, notification), Redis where TTL-bound, extreme-throughput ephemeral data matters (cart).
- **Multi-tenant ThreadLocal + Hibernate filter** ([ADR-004](../adr/ADR-004-multitenant-threadlocal-hibernate.md)) — chosen as a low-invasion way to add row-level tenant isolation without schema-per-tenant or database-per-tenant operational overhead.
- **Database-per-service** — each domain service owns its data outright; no service reaches into another's schema. This is the one architectural rule that was never violated or worked around anywhere in the codebase, and it's the reason every other resilience pattern here (saga, outbox, event-carried state transfer) exists — it's the tax paid for keeping services genuinely independent.

These are all reasonable, defensible decisions for the stated goals. The rest of this document is about where the *execution* of those decisions still falls short of "ready for a real tenant to pay you for this."

---

## 2. Multi-Tenancy: Built But Not Actually Multi-Tenant

This is the single largest piece of architectural debt in the system, so it gets its own section instead of a bullet point.

**What exists:** a complete, generically-reusable tenancy substrate — `TenantContext` (ThreadLocal), `TenantFeignInterceptor` (propagates `X-Tenant-Id` on every outbound call), `TenantHibernateFilterActivator` (row-level Hibernate `@Filter`), `TenantValidationFilter` at the gateway, and a full `tenant-service` with plans, feature flags, and usage metering endpoints.

**What actually happens today:** every user created through the UI is provisioned with `tenantId = "default"`. The tenant filter is real code, correctly wired, and does precisely nothing, because there is currently only one tenant value in existence. Seller-to-seller data isolation — which is the isolation boundary that actually matters in this system today — is instead enforced by comparing `Product.createdBy` / `order_line.seller_id` against the calling user's ID. That column started life as an audit field, not a security boundary, and its repurposing shows: the `GET /auth/sellers/{id}` 500 (a seed product's `createdBy = "system"` string landing in a `@PathVariable Long id`) is a direct symptom of using a loosely-typed convenience field as a security boundary instead of a properly-typed, constraint-enforced tenant column.

**Why this matters:** the two isolation mechanisms are not equivalent, and treating "seller isolation via `createdBy`" as a stand-in for "tenant isolation via `tenantId`" hides a real gap — nothing currently stops one *tenant* from seeing another tenant's data, only one *seller* from seeing another seller's products within the same tenant. If a second real tenant were onboarded tomorrow via `tenant-service`'s own API, none of the domain services' queries would automatically scope to it beyond what the (currently inert) Hibernate filter does.

**What would need to change for this to be real:**
1. Actually issue distinct `tenantId`s at registration/tenant-onboarding time and thread them through JWT claims, not just accept a single hardcoded default.
2. Add integration tests that create **two** tenants and assert cross-tenant queries return zero rows — every test today runs under a single implicit tenant, so the filter's absence of effect is untested by definition.
3. Promote `createdBy`/`seller_id` to a properly-typed, non-nullable, foreign-key-backed column with a real seller identity, not a convention-typed string/long field that seed data can violate.
4. Wire `tenant-service`'s subscription plans and feature flags to actual request-time enforcement (rate limits, feature gates) at the gateway — right now they are CRUD resources with no consumer.

---

## 3. Secrets & Configuration: Fine for Local Dev, Not Fine for Production

`docker-compose.yml` hardcodes the *same* fallback `JWT_SECRET` default (`${JWT_SECRET:-bXlTdXBlclNlY3VyZVNlY3JldEtleUZvckpXVEF1dGgxMjM0NTY3ODk=}`) across all eight services that need it. This is a reasonable convenience for a one-command local bring-up, but it means:

- Every service trusts tokens signed by every other service with an identical shared secret — there is no cryptographic boundary between services at the JWT layer.
- The fallback value is committed to source control in plaintext. Anyone who has ever cloned this repo has the "production" default.
- Vault is deployed in **dev mode** (`vault server -dev`, root token `root-token`, also defaulted and committed) — dev mode is explicitly unsealed, non-persistent, and not intended to hold real secrets even transiently.

**What would need to change:** real deployments must inject `JWT_SECRET`/`VAULT_TOKEN`/`REDIS_PASSWORD`/DB credentials via a secrets manager (the existing Vault integration is the right mechanism — it just needs to run in `-config=/vault/config/vault.hcl` server mode, unsealed via a real unseal process, not `-dev`), and the compose defaults should fail loudly (or not exist) rather than silently falling back to a well-known value. `docker-compose.prod.yml` already strips exposed ports and adds resource limits — it does not yet strip these credential defaults, which is the more important hardening step.

---

## 4. Saga Observability: Correct, But Opaque

[ADR-001](../adr/ADR-001-choreography-saga.md) is upfront about this trade-off, but it's worth restating because it's the kind of gap that only hurts on the day you need it most: **there is no single place to see the current state of an in-flight order.** A support engineer investigating "why is order X stuck" must correlate `correlationId` across `order-service`, `product-service`, `payment-service`, and `notification-service` logs, or lean on Zipkin traces if tracing happens to be enabled for that path.

There is also no event schema registry or contract testing between saga participants — `order.requested`, `inventory.reserved`, `payment.authorized`, etc. are plain POJOs serialized without a shared, versioned schema. A producer changing a field name is a runtime break discovered by the consumer's `ErrorHandlingDeserializer`/DLQ, not a build-time contract violation. That DLQ safety net exists (see [01-development-history.md](01-development-history.md#notification-service) for exactly why it had to be added the hard way) but it is a last line of defense, not a substitute for a schema contract.

**What would need to change:** a saga-state read-model (a table or view that aggregates the last-known status per `correlationId` across all four services, or a proper saga-log projection) would turn "grep four services' logs" into "query one table." Schema registry (Avro/Protobuf with Confluent Schema Registry, or at minimum a shared versioned POJO module with consumer-driven contract tests) would move schema-compatibility failures from "discovered in production via the DLQ" to "caught in CI."

---

## 5. Infrastructure Duality: Docker Compose Service Discovery vs. Kubernetes

The repository contains a complete `k8s/` and `helm/ecommerce` layout (deployments, HPA, PDB, Istio network policies) *alongside* a Eureka-based service discovery layer (`discovery-service`) that Spring Cloud Gateway and every Feign client are wired against. These two infrastructure strategies solve the same problem — service discovery and load balancing — in incompatible ways: Kubernetes + Istio would normally provide DNS-based service discovery and mesh-level load balancing/mTLS, making Eureka redundant (and, if Istio's mutual TLS and Eureka's own client-side load balancing both try to own the same concern, a source of confusing double-indirection). As it stands today, the Kubernetes manifests are not the primary deployment target being exercised — the entire incident history in this document was found and fixed against the Docker Compose topology (`docker-compose.yml` + `.scale.yml` overlay) — which means the k8s/Helm/Istio path is largely unproven relative to the compose path.

**What would need to change:** pick one target for production and treat the other as either removed or explicitly marked experimental/future. If Kubernetes is the real target, Eureka should be retired in favor of native k8s Service discovery, and the HA story (currently `nginx-edge` + manual `docker-compose.scale.yml` replicas) should be replaced with a Horizontal Pod Autoscaler + Service — which the `k8s/hpa` directory already scaffolds but which has not been exercised the way the compose-based rolling-restart proof has.

---

## 6. Testing Strategy: Correct Rule, Real Cost

The project enforces a hard rule — **no H2, no embedded/in-memory database substitutes; integration tests must hit real PostgreSQL/MongoDB via Testcontainers, `@EmbeddedKafka` for messaging** — after being burned once by a mocked-database integration test that passed while the real migration it was meant to validate was broken. This is the right rule for correctness. It is not free:

- Every integration/BDD run now depends on a working Docker daemon. On this project's actual development host (Windows + Docker Desktop), that daemon has been observed to intermittently 500 on `/info`, requiring a restart — a flakiness source that a mocked-DB test suite would never have.
- Testcontainers startup (spinning up a real Postgres/Mongo/Kafka per test class) adds real wall-clock time to every CI run and every local `mvn verify`, compared to an in-memory substitute.
- DOM/component-level frontend tests (Vitest) are documented as unreliable on this same host (thread-pool hangs, fork-pool crashes on MUI/framer-motion imports) — meaning frontend logic below the DOM layer is tested, but component-level rendering tests are effectively unverified locally and rely on `tsc -b --noEmit` + ESLint as a proxy for correctness, which catches type errors but not rendering/interaction regressions.

**What would need to change:** this trade-off is arguably *correct as-is* for a small team that got burned once by mocking — the fix is environmental, not architectural: a CI runner with a stable Docker daemon (any standard Linux CI image) removes the flakiness that's specific to this Windows dev host, and a real headless-browser CI lane (Playwright, which the project already uses for E2E) can absorb the component-test coverage that Vitest can't currently provide here.

---

## 7. Alerting: A Monitoring Stack Is Not the Same as an Alerting Pipeline

Prometheus, Grafana, Zipkin, Loki, and Alertmanager are all deployed and, as of the most recent verification, scraping cleanly (11/11 targets up, zero errors). But for a long stretch of the project's history, Alertmanager's Slack receiver was a placeholder webhook — meaning every `ServiceDown` alert that fired was silently undeliverable, and worse, the failed-delivery retry loop is what caused the 189MB log file and 122% CPU `promtail` incident recorded in [01-development-history.md](01-development-history.md#infrastructure--observability). A fully-scraped, zero-alerts Prometheus dashboard looks identical whether or not anyone would actually be paged if something broke.

**What would need to change:** a real Slack/PagerDuty/Opsgenie receiver, and — just as important — a periodic synthetic alert test (fire a known alert on a schedule, confirm it's received) so "the alert pipeline is wired correctly" is a verified fact, not an assumption that holds until the first real incident.

---

## 8. Idempotency Is Applied Ad Hoc, Not as a Platform Guarantee

`order-service` supports an `Idempotency-Key` header (added specifically after duplicate orders were observed under client retry). `payment-service` derives its own idempotency key from `orderReference`. `notification-service` dedupes via `ProcessedEvent`. Each of these is a real, correct, independently-motivated fix — but each was added reactively, to its own service, after its own incident, rather than as a shared platform convention applied uniformly to every mutating endpoint from day one. `product-service`'s stock-reservation endpoint and `cart-service`'s item-mutation endpoints, for example, have no equivalent explicit idempotency guard documented.

**What would need to change:** a shared idempotency-key convention (and ideally a shared library, the way `tenant-context` is shared) applied to every mutating REST endpoint and every Kafka consumer at the point services are scaffolded, not bolted on service-by-service after each is caught by a duplicate-request bug in QA.

---

## 9. Summary Table

| Area | Current State | Production-Readiness Gap |
|---|---|---|
| Multi-tenancy | Substrate built, inert (single tenant); seller isolation via `createdBy` convention | Real tenant issuance + JWT claim + cross-tenant tests + typed ownership column |
| Secrets | Hardcoded, committed defaults for JWT/Vault/Redis/DB creds; Vault in dev mode | Real secret injection, Vault server mode, no committed fallback values |
| Saga observability | correlationId across 4 services' logs; no schema registry | Saga read-model/status projection + versioned event contracts |
| Infra topology | Compose (proven) + K8s/Helm/Istio (unproven, parallel) | Pick one target; retire Eureka if K8s is primary |
| Testing | Real DBs via Testcontainers (correct); Docker-daemon-dependent, host-flaky | Stable Linux CI runner; Playwright for component coverage gap |
| Alerting | Full metrics/trace/log stack; alert delivery previously inert | Real receiver + periodic synthetic alert test |
| Idempotency | Per-service, reactive, inconsistent coverage | Shared platform convention, applied proactively |

None of these are indictments of the engineering that's been done — every one of them was found, understood, and fixed *for the specific incident that surfaced it*. What's missing across the board is the generalization step: turning "we fixed this once, here" into "this class of bug cannot recur anywhere in the platform." That generalization step is exactly what [03-saas-engineering-playbook.md](03-saas-engineering-playbook.md) is about.
