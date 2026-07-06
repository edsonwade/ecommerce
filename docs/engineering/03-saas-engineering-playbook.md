# SaaS Engineering Playbook

This document generalizes the incidents in [01-development-history.md](01-development-history.md) and the gaps in [02-architecture-tradeoffs.md](02-architecture-tradeoffs.md) into reusable guidance. It is written for whoever starts the *next* multi-tenant SaaS platform — the goal is to let them buy back, for free, the lessons this project paid for in debugging hours.

---

## 1. Where to Start (Decision Order)

Order matters. Decisions made later are constrained by decisions made earlier, and some of the costliest rework in this project's history came from deciding infrastructure concerns before deciding the concerns they exist to serve.

1. **Decide your tenancy model before writing a single domain service.** Shared schema with a `tenant_id` column + row-level security/Hibernate filter? Schema-per-tenant? Database-per-tenant? This project chose shared-schema + ThreadLocal + Hibernate filter ([ADR-004](../adr/ADR-004-multitenant-threadlocal-hibernate.md)) — a reasonable choice — but never actually exercised it with more than one tenant, which is why it silently rotted into unenforced infrastructure (see [Tradeoffs §2](02-architecture-tradeoffs.md#2-multi-tenancy-built-but-not-actually-multi-tenant)). **Whatever you choose, provision at least two real tenants in your very first integration test**, and assert data from one is invisible to the other. If that test doesn't exist, you don't actually know your isolation works — you're trusting untested code.
2. **Decide your consistency boundary before choosing a saga pattern.** Choreography vs. orchestration ([ADR-001](../adr/ADR-001-choreography-saga.md)) is a real, defensible fork — but it's a decision about *how many services must agree on "what happened" without a shared database*, and that question only has a clean answer if you already know your service boundaries. Draw the bounded contexts first.
3. **Decide per-service persistence based on access pattern, not team preference.** [ADR-003](../adr/ADR-003-polyglot-persistence.md)'s reasoning — ACID/relational vs. document-flexible vs. ephemeral-TTL — is a template worth reusing directly: ask "does this data need joins and transactions, does its shape change often, does it need to survive a restart" before picking a datastore.
4. **Stand up observability (metrics, tracing, structured logs) before you have a second service, not after you have ten.** Every cross-service incident in this project's history (the gateway TimeLimiter 503s, the saga field-propagation bug, the Zipkin-blocks-event-loop bug) was found by correlating logs/traces across service boundaries. That correlation infrastructure is worthless if it's retrofitted after the services already exist and already have inconsistent logging conventions.
5. **Decide your secrets story (Vault/KMS/parameter store) before your first hardcoded `${VAR:-default}` shows up in a compose file.** It is far cheaper to start every service with "no default, fail to start without a real secret" than to retroactively hunt down and remove committed fallback values later (see [Tradeoffs §3](02-architecture-tradeoffs.md#3-secrets--configuration-fine-for-local-dev-not-fine-for-production)).
6. **Pick one deployment target and commit to proving it, not two half-proven ones.** This project has both a Docker Compose topology (exercised, incident-tested, proven) and a Kubernetes/Helm/Istio topology (scaffolded, not exercised) that solve service discovery differently. Maintaining both doubles the surface area for zero added confidence in either.

---

## 2. What to Always Keep in Mind

### Multi-tenancy is a data-access concern, not a login-screen concern
A tenant switcher in the UI and a `tenantId` column in the JWT are necessary but not sufficient. The actual guarantee has to live at the query layer (Hibernate filter, row-level security, or a repository wrapper that *cannot* be called without a tenant context). If it's possible to write a repository method that forgets to filter by tenant, someone eventually will, and it will not be caught until a real second tenant exists to leak into.

### Idempotency is a platform contract, not a per-incident patch
Every mutating endpoint and every at-least-once Kafka consumer needs an idempotency strategy decided at design time — key derivation, dedup window, and what to return on a repeat request. In this project, `order-service`'s `Idempotency-Key`, `payment-service`'s `orderReference`-derived key, and `notification-service`'s `ProcessedEvent` guard were each added *after* a duplicate-processing incident, service by service. That's a working fix, but it's not a design. Decide the convention once, put it in a shared library, and apply it when a service is scaffolded — not when QA finds the first duplicate order.

### Async delivery needs a designed failure path, not just a retry policy
"At-least-once" delivery is not a complete design until you've answered: what happens to a message that can *never* be processed successfully (a poison record, a permanently malformed payload)? Without an answer, the default behavior of most consumer frameworks is to retry forever — which, as this project learned directly, means an infinite loop at full CPU, not graceful degradation. `ErrorHandlingDeserializer` + a dead-letter queue + something that actually reads the DLQ (this project's `DlqConsumer` persisting to MongoDB for ops review) is the minimum viable version of "designed failure path." Build it alongside the happy path, not after the first production CPU spike.

### Every "fast path" hides its slow dependencies until load finds them
BCrypt at cost 12, a synchronous cross-service profile-provisioning call inside login, and Zipkin span export inside a reactive gateway's event loop were each, individually, reasonable-looking code that happened to sit on the one path where milliseconds are load-bearing. When you identify a hot path (auth, checkout, anything customer-facing and synchronous), audit every call it makes for blocking I/O, and default to `@Async`/fire-and-forget for anything that is a side effect rather than the actual response the caller is waiting for.

### A default you didn't set is still a setting
Spring Cloud's Resilience4j `TimeLimiter` defaults to a 1-second timeout that this project's gateway inherited silently and then spent multiple sessions debugging around, tuning circuit-breaker thresholds and connection pools that were never the actual problem. **Any time a framework wraps your code in resilience machinery (timeouts, retries, circuit breakers, connection pools), print or document every effective default, not just the ones you explicitly configured.** "It's not in my YAML" does not mean "it's not happening."

### A health check against a real external dependency is itself a dependency
`SmtpHealthIndicator` performing a full SMTP AUTH handshake against a rate-limited third-party sandbox (Mailtrap) on every health poll locked the platform's own outbound email out of its own provider. Health checks should verify reachability, not repeatedly perform the exact rate-limited operation the provider is protecting.

### Security parameters tuned for your dev laptop are not your production answer
BCrypt cost was lowered from 12 to 10 specifically because a local Docker host's CPU made every login unacceptably slow. That's a legitimate, honest trade-off *for local development* — but it should be revisited against real production hardware before launch, not carried forward by default. Any security/performance parameter (hash cost, token TTL, rate-limit thresholds) tuned to make local dev bearable needs an explicit "does this still hold on production infrastructure" check before go-live.

### Shared browser storage breaks multi-role, multi-tab testing
`localStorage` is shared across every tab of the same origin. Any app supporting multiple concurrent roles/sessions (customer + seller + admin, as here) needs session-scoped storage (`sessionStorage`) or explicit per-tab session partitioning from the start — otherwise QA sessions with multiple roles open will intermittently and confusingly stomp on each other.

---

## 3. What to Avoid

| Anti-pattern | Why it bites | What this project learned it the hard way |
|---|---|---|
| Mocking the database in integration tests | A mock can't diverge from a schema change; a real migration bug sails through green tests and breaks prod | Explicit project rule now: **no H2, Testcontainers only** for integration tests |
| Repurposing an audit/convenience field as a security boundary | Loosely-typed fields accept values (like `"system"`) that a strict security column would reject at the schema level | `Product.createdBy` string/seed-data mismatch caused an unhandled 500 instead of a clean 4xx |
| Building tenancy infrastructure without a second tenant to test it against | Untested isolation code is unverified isolation | Tenant filter has been inert since Phase 4 — never exercised with `tenantId != "default"` |
| Committing a working default secret "just for local dev" | Defaults leak into production if nobody remembers to override them, and they're visible to anyone with repo access regardless | Shared `JWT_SECRET` default across all 8 services, committed in `docker-compose.yml` |
| A monitoring stack without a tested alert-delivery path | Green dashboards create false confidence; nobody gets paged | Alertmanager's Slack receiver was a placeholder for a long stretch — alerts fired into the void |
| Letting a "fix" for one bug touch a shared UI/infra surface without checking adjacent features | Two features sharing a surface (sidebar visibility, drawer state) can regress each other silently | The freeze-fix for a scroll-lock bug hid the entire seller/admin sidebar |
| Adding resilience machinery (circuit breakers, timeouts) without auditing its *defaults* | You'll tune the settings you can see and miss the one you can't | Gateway 503s persisted across multiple tuning passes until the invisible 1s `TimeLimiter` default was found |
| Treating "at-least-once" as a complete design without a poison-message path | Retry-forever on an unprocessable message is not resilience, it's a CPU-bound infinite loop | notification-service pinned a core at ~50-122% CPU on a single bad record before `ErrorHandlingDeserializer` + DLQ existed |
| Health-checking a third party by performing its most expensive/rate-limited operation | You can trigger the exact failure mode you're trying to detect | SmtpHealthIndicator's real SMTP AUTH polling locked the shared Mailtrap inbox |
| Maintaining two parallel infrastructure strategies for the same concern (Eureka + Kubernetes/Istio) | Effort split across both means neither is as proven as a single committed choice | Compose topology is incident-tested; the k8s/Helm/Istio path is scaffolded but unexercised |
| Skipping the "why" when writing a fix | Without root cause, the same bug reappears in a new service with a new name | Redis 2s timeouts tripped health checks in more than one service before "always 10s connect+command timeout" became a standing rule |

---

## 4. Day-1 Checklist (before the first domain service is written)

- [ ] Tenancy model chosen, and a two-tenant isolation test exists before a second real service is added
- [ ] Structured logging + a trace ID propagated through every service, sync and async
- [ ] Secrets sourced from a vault/secrets-manager with no working default fallback
- [ ] Idempotency-key convention decided and available as a shared library/filter, not per-service
- [ ] Health checks defined for every external dependency, verified to probe *connectivity* rather than perform expensive/rate-limited operations
- [ ] Poison-message/DLQ handling designed alongside the first Kafka (or equivalent) consumer, not after the first stuck consumer
- [ ] One deployment target chosen (compose/k8s/other) and the other explicitly deferred, not built in parallel
- [ ] CI runs on infrastructure where the test suite's real dependencies (Docker daemon, etc.) are stable — don't inherit a host-specific flakiness workaround into "how the team writes tests"

## 5. Pre-Production Checklist

- [ ] Every hardcoded/default secret from local dev removed or replaced with a fail-fast (no default) requirement
- [ ] Vault (or equivalent) running in real server mode, not dev mode
- [ ] Alert delivery tested end-to-end with a synthetic alert, not just "the dashboard shows green"
- [ ] Security parameters (hash cost, token TTL, rate limits) re-validated against real production hardware, not carried over from whatever made local dev bearable
- [ ] At least one saga/workflow-state read-model exists so support can answer "what's the status of X" without grepping four services' logs
- [ ] Load test against the actual hot paths (login, checkout) with production-shaped concurrency, specifically looking for blocking calls that were fine at dev-scale traffic
- [ ] Tenant isolation re-verified with real multi-tenant data, not just the single default tenant every dev environment has used until now

---

## 6. The One-Sentence Version

Every incident in this project's history was a reasonable decision that was correct in isolation and incomplete in context — the fix, every time, was not "write better code" but "generalize what one incident taught into a rule the whole platform follows before the next service is written." That generalization discipline — captured here so it doesn't have to be relearned — is the actual deliverable of this playbook.
