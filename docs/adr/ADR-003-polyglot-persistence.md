# ADR-003: Polyglot Persistence Strategy

**Date:** 2026-04-11
**Status:** Accepted
**Author:** Vanilson

---

## Context

The platform runs 10 application services, each with distinct data access patterns, consistency requirements, and read/write profiles. Using a single database technology across all services would mean accepting the worst-case trade-offs everywhere — either forcing document-oriented workloads into a rigid relational schema, or sacrificing ACID guarantees in financial workflows that require them.

The goal was to choose the right datastore for each service's domain model and access patterns, while keeping operational complexity manageable by limiting the total number of database technologies.

---

## Decision

We use three database technologies, each assigned to services based on their workload characteristics:

### PostgreSQL 15 — `authentication-service`, `order-service`, `product-service`, `payment-service`

PostgreSQL is used wherever the following requirements apply:

- **Strong consistency and ACID transactions** — payment processing and order state transitions must never result in partial writes or dirty reads.
- **Complex relational queries and joins** — products have categories, variants, and inventory rows that are best expressed as normalised relational tables.
- **Pessimistic or optimistic locking** — `product-service` uses pessimistic locking on inventory rows during reservation to prevent overselling under concurrent order load.
- **Auditing and referential integrity** — the financial record for a payment must be permanently linked to an order; foreign-key constraints enforce this at the database level.

Each service gets its own isolated PostgreSQL instance (`postgres-auth`, `postgres-order`, `postgres-product`, `postgres-payment`), ensuring that a slow query or schema migration in one service cannot affect the others.

### MongoDB 7.0 (Replica Set) — `customer-service`, `notification-service`

MongoDB is used where the following requirements apply:

- **Flexible or evolving schema** — customer profiles accumulate optional fields (address lines, preferences, social handles) that vary by tenant and change frequently. A rigid relational schema would require frequent `ALTER TABLE` migrations.
- **Document-centric access pattern** — a customer record is almost always read and written as a whole document, not as a set of joined rows. MongoDB's document model maps naturally to this.
- **No complex cross-collection joins** — neither customers nor notification records need to be joined to other collections at query time.
- **Append-heavy event log** — `notification-service` stores notification history, DLQ events, and idempotency records as immutable documents. MongoDB's write-optimised storage engine handles this efficiently.

The MongoDB replica set is shared across `customer-service` and `notification-service`, with each service using its own database (`customer_service_db`, `notification_service_db`) for isolation.

### Redis 7.2 (Sentinel) — `cart-service`

Redis is used for the shopping cart because:

- **Ephemeral, TTL-bound data** — cart sessions expire after 24 hours of inactivity. Redis's native TTL mechanism handles expiry automatically without a cleanup job.
- **Extreme read/write throughput** — the gateway applies a 200 req/s rate limit specifically to `cart-service`, the highest of any service. Redis's in-memory architecture handles this trivially; a relational database would require aggressive connection pooling and indexing to keep up.
- **Simple key-value access pattern** — carts are keyed by `customerId`. There are no joins, aggregations, or complex queries. The entire cart is serialised and stored as a single Redis value.
- **No durability requirement beyond session lifetime** — losing an uncommitted cart on a Redis restart is an acceptable UX degradation (the user simply re-adds items). This makes Redis's AOF/RDB persistence trade-offs acceptable.

Redis Sentinel provides high availability with automatic failover in the HA compose variant.

### Summary

| Service | Database | Reason |
|---|---|---|
| authentication-service | PostgreSQL | ACID, relational user/token model |
| order-service | PostgreSQL | ACID, saga state, outbox table |
| product-service | PostgreSQL | Pessimistic locking, relational inventory |
| payment-service | PostgreSQL | ACID, financial record integrity |
| customer-service | MongoDB | Flexible document schema, document-centric reads |
| notification-service | MongoDB | Append-heavy event log, DLQ persistence, idempotency |
| cart-service | Redis | Ephemeral TTL sessions, extreme throughput |

---

## Alternatives Considered

### Single PostgreSQL cluster for all services

A shared PostgreSQL cluster with per-service schemas.

**Why rejected:**
- `cart-service`'s 200 req/s workload and ephemeral data model are a poor fit for PostgreSQL. Redis handles this with an order of magnitude less resource consumption and without connection pool contention.
- Customer profile evolution would require frequent schema migrations coordinated across teams.
- A performance problem in one service's queries (e.g., a missing index in notification history) would affect all services sharing the cluster.

### Single MongoDB cluster for all services

Use MongoDB for all services, including financial ones.

**Why rejected:**
- MongoDB's multi-document transactions are available but add overhead and complexity. Payment records and order status transitions benefit from the simpler, more mature ACID guarantees of PostgreSQL.
- Pessimistic locking for inventory management is a first-class feature of relational databases and would require application-level workarounds in MongoDB.

### Redis as primary store beyond cart-service

Extend Redis usage to customer profiles or notification history.

**Why rejected:**
- Redis is an in-memory store. Persisting large document collections in Redis would be expensive and operationally fragile.
- Customer and notification data must survive Redis restarts without data loss. PostgreSQL and MongoDB both offer stronger durability guarantees.

---

## Consequences

**Positive:**
- Each service's persistence layer is tuned for its actual workload, not a generic compromise.
- Schema evolution is easier: MongoDB services can add fields without migrations; PostgreSQL services benefit from strict schema enforcement where it matters.
- Failure isolation is complete: a PostgreSQL outage does not affect `cart-service` or `customer-service`, and vice versa.

**Negative / Trade-offs:**
- Developers must be familiar with three different query languages and client libraries (JPA/JPQL, Spring Data MongoDB, Spring Data Redis).
- Operational surface area is larger: three database technologies to monitor, back up, and tune.
- Cross-service queries (e.g., "show me all orders for customers in city X") cannot be performed at the database level and must be composed at the application layer or via an analytics pipeline.
- Running four PostgreSQL instances, a MongoDB replica set, and a Redis Sentinel cluster locally requires Docker with at least 6 GB of RAM allocated.

---

## Implementation Reference

| Service | Config |
|---|---|
| cart-service | `cart-service/src/main/resources/application.yml` — `spring.data.redis.*` |
| customer-service | `customer-service/src/main/resources/application.yml` — `spring.data.mongodb.*` |
| notification-service | `notification-service/src/main/resources/application.yml` — `spring.data.mongodb.*` |
| order/payment/product/auth | respective `application.yml` — `spring.datasource.*` |
| Infrastructure | `docker-compose.yml` — `postgres-*`, `mongodb`, `redis` service definitions |
