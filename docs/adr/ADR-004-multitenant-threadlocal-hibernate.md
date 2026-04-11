# ADR-004: Multi-Tenancy via ThreadLocal + Hibernate Filters

**Date:** 2026-04-11
**Status:** Accepted
**Author:** Vanilson

---

## Context

The platform is a SaaS product where multiple tenants share the same deployed infrastructure. Every piece of business data — orders, products, customers, payments — belongs to exactly one tenant and must never be visible to another tenant, even in the event of a bug or missing filter clause.

The three classical approaches to multi-tenancy are:

1. **Database-per-tenant** — each tenant gets an isolated database instance.
2. **Schema-per-tenant** — each tenant gets an isolated schema within a shared database.
3. **Row-level isolation (discriminator column)** — all tenants share tables; a `tenant_id` column discriminates rows.

Beyond the isolation model, the implementation question was: *where* does the tenant context live, and *how* do individual services access it without coupling their business logic to tenancy concerns?

---

## Decision

We adopted **row-level isolation** with `tenant_id` discriminator columns on all multi-tenant entities, combined with two infrastructure mechanisms:

1. **`InheritableThreadLocal` in `TenantContext`** — holds the current tenant ID for the lifetime of an HTTP request thread. `InheritableThreadLocal` (rather than plain `ThreadLocal`) is used so that child threads spawned by `@Async` methods inherit the tenant context without explicit propagation.

2. **Hibernate `@Filter` / `@FilterDef`** — a named filter (`tenantFilter`) is declared on each tenant-scoped entity. `TenantHibernateFilterActivator` enables this filter on the JPA session with the current tenant ID as a parameter, causing Hibernate to automatically append `WHERE tenant_id = :tenantId` to all queries against those entities.

The complete request lifecycle is:

```
HTTP Request arrives at service
         │
         ▼
TenantInterceptor.preHandle()
  → extracts X-Tenant-ID from header
  → TenantContext.setCurrentTenantId(tenantId)
  → TenantHibernateFilterActivator.activateFilter()
         │
         ▼
Business logic executes
  (all JPA queries filtered automatically)
         │
         ▼
TenantInterceptor.afterCompletion()
  → TenantContext.clear()          ← prevents memory leak in pooled threads
  → TenantHibernateFilterActivator.deactivateFilter()
```

The entire mechanism is packaged as a **Spring Boot auto-configuration library (`tenant-context`)**, published as a Maven module. Any service that adds `tenant-context` as a dependency and annotates its main class with `@EnableMultiTenancy` receives full tenant isolation with zero changes to its business logic or repository layer.

At the gateway layer, `TenantValidationFilter` validates the `X-Tenant-ID` header on every inbound request against `tenant-service` before the request reaches any downstream service. Invalid or missing tenant IDs are rejected at the perimeter with a `403 Forbidden`.

For Feign-based inter-service calls, `TenantFeignInterceptor` propagates the `X-Tenant-ID` header automatically, so the tenant context is never lost as a request crosses service boundaries.

`TenantHibernateFilterActivator` is registered conditionally — only when JPA is on the classpath. Services that use MongoDB or Redis (e.g., `cart-service`, `notification-service`) skip this bean entirely without any configuration.

Certain cross-tenant operations — such as `OutboxEventPublisher` polling all pending outbox events — intentionally bypass the filter by accessing data outside the normal request thread. This is by design: the scheduler has legitimate need for cross-tenant visibility and is documented explicitly in `OutboxEvent`'s Javadoc.

---

## Alternatives Considered

### Database-per-tenant

Each tenant gets a dedicated PostgreSQL instance or at least a dedicated connection pool.

**Why rejected:**
- With potentially hundreds or thousands of tenants, provisioning a database per tenant is operationally untenable. PostgreSQL connection counts, storage allocation, and backup procedures scale linearly with tenant count.
- Dynamic tenant onboarding would require runtime database provisioning, adding latency to the signup flow.
- Migrations must be applied to every tenant database independently, complicating the deployment pipeline.

### Schema-per-tenant

Each tenant gets a dedicated schema (`tenant_abc.orders`, `tenant_abc.products`, etc.) within a shared PostgreSQL cluster.

**Why rejected:**
- Hibernate does not support runtime schema switching cleanly without custom connection pooling (e.g., a per-tenant `DataSource` or schema-switching `ConnectionProvider`). This adds significant framework-level complexity.
- Liquibase/Flyway migration management across hundreds of schemas is error-prone.
- The number of schemas scales with tenant count, eventually hitting PostgreSQL schema namespace limits.
- Offers stronger isolation than row-level but at a maintenance cost not justified for this platform's threat model.

### Application-level filtering (manual WHERE clauses)

Add `tenantId` parameters to every repository method and every service call explicitly.

**Why rejected:**
- Requires every developer to remember to add `AND tenant_id = ?` to every query. A single omission creates a data leak.
- The tenant concern pollutes every method signature in the domain and application layers.
- Non-trivial to audit: there is no single place to verify that all queries are correctly filtered.

### Spring Security ACL

Use Spring Security's Access Control List framework to enforce per-object access rules based on the authenticated principal's tenant.

**Why rejected:**
- ACL operates at the object level (post-fetch), not at the query level. It does not prevent queries from fetching cross-tenant records in the first place — it only filters after the fact.
- ACL adds a significant persistence model of its own (`acl_*` tables) that must be maintained in sync with the business data.

---

## Consequences

**Positive:**
- Tenant isolation is enforced transparently at the persistence layer. Business logic in service classes and JPA repositories does not contain any tenancy-related code.
- Adding a new service to the platform requires only adding the `tenant-context` dependency and `@EnableMultiTenancy` — no boilerplate to write.
- `InheritableThreadLocal` ensures `@Async` tasks correctly inherit the tenant context without extra propagation code.
- The filter is opt-in per entity — entities like `Tenant` itself, which require cross-tenant visibility for admin operations, simply omit the `@Filter` annotation.

**Negative / Trade-offs:**
- `ThreadLocal` state is invisible to the IDE and static analysis tools. A developer who spawns a thread manually (not via `@Async`) will silently lose the tenant context. This must be documented in the contributing guide.
- Row-level isolation is weaker than physical isolation. A Hibernate bug, a native query that bypasses the filter, or a direct JDBC call could inadvertently return cross-tenant data. Mitigation: integration tests cover cross-tenant isolation; native queries are reviewed carefully.
- The Hibernate filter must be activated before any JPA access. If a service method accesses the repository before `TenantHibernateFilterActivator.activateFilter()` is called (e.g., from a background scheduler), it will retrieve unfiltered data. These cases must be handled explicitly.
- Scheduled jobs that require cross-tenant access (e.g., `OutboxEventPublisher`) must explicitly avoid calling `TenantContext.requireCurrentTenantId()` and must not enable the Hibernate filter. This is a footgun that requires developer awareness.

---

## Implementation Reference

| Component | Location |
|---|---|
| Tenant ID holder | `tenant-context/.../TenantContext.java` |
| HTTP interceptor (extract + filter activation) | `tenant-context/.../TenantInterceptor.java` |
| Hibernate filter activator | `tenant-context/.../TenantHibernateFilterActivator.java` |
| Feign propagation | `tenant-context/.../TenantFeignInterceptor.java` |
| Auto-configuration | `tenant-context/.../TenantContextAutoConfiguration.java` |
| Activation annotation | `tenant-context/.../EnableMultiTenancy.java` |
| Filter constants | `tenant-context/.../TenantFilterConstants.java` |
| Gateway validation filter | `gateway-api-service/.../TenantValidationFilter.java` |
