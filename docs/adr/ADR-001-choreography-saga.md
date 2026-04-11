# ADR-001: Choreography Saga over Orchestration for Order Processing

**Date:** 2026-04-11
**Status:** Accepted
**Author:** Vanilson

---

## Context

The order fulfilment flow spans four independent services — `order-service`, `product-service` (inventory reservation), `payment-service`, and `notification-service`. Each service owns its own database, so the operation cannot be wrapped in a single ACID transaction.

The system required a distributed transaction strategy that could:

- Guarantee eventual consistency across service boundaries without a shared database.
- Compensate correctly on failure (insufficient stock, declined payment).
- Remain resilient to individual service unavailability.
- Scale independently — each service processes its slice of the workflow at its own pace.

Two patterns were evaluated: **orchestrated saga** (a central coordinator drives each step) and **choreography saga** (services react autonomously to domain events published on Kafka).

---

## Decision

We adopted the **choreography saga pattern** using Apache Kafka as the event bus. There is no central orchestrator. Each service listens for the domain events it cares about, performs its local transaction, and publishes the outcome event for the next participant.

The full event sequence is:

```
Client → OrderService  POST /api/v1/orders  (202 Accepted)
OrderService           publishes  order.requested          (via Outbox)
ProductService         consumes   order.requested
                       publishes  inventory.reserved  OR  inventory.insufficient
PaymentService         consumes   inventory.reserved
                       publishes  payment.authorized  OR  payment.failed
OrderService           consumes   payment.authorized  → status CONFIRMED
                       consumes   payment.failed       → status CANCELLED
                       consumes   inventory.insufficient → status CANCELLED
NotificationService    consumes   order-topic / payment-topic  → sends email
```

Compensation is implicit: on `payment.failed` or `inventory.insufficient`, `OrderSagaConsumer` sets the order status to `CANCELLED`. No explicit rollback messages are needed because inventory is only reserved (not decremented) until payment succeeds, and no money is captured until inventory is confirmed.

`OrderSagaConsumer` uses **manual Kafka acknowledgement** — the offset is committed only after the database write succeeds. This means a failed DB write causes Kafka to redeliver the event, and the handler is safe to re-run because status updates are idempotent (setting `CONFIRMED` on an already-`CONFIRMED` order has no effect).

---

## Alternatives Considered

### Orchestrated Saga (e.g., Spring State Machine or AWS Step Functions-style)

A central `OrderOrchestrator` service would have driven each step explicitly, waited for responses, and issued compensation commands on failure.

**Why rejected:**
- Introduces a single point of failure and a bottleneck — all order traffic flows through one component.
- Creates tight temporal coupling: the orchestrator must be available for every step.
- Harder to evolve: adding a new step (e.g., fraud check) requires modifying the orchestrator rather than deploying a new participant.
- Violates the principle that each service owns its domain fully.

### Two-Phase Commit (2PC / XA Transactions)

A distributed transaction manager coordinates a prepare-then-commit protocol across all databases.

**Why rejected:**
- None of the message brokers or datastores in this stack support XA in a practical, production-safe manner at scale.
- 2PC is a blocking protocol: a single slow or unavailable participant blocks the entire transaction.
- Fundamentally incompatible with the goal of independent service deployment and scaling.

---

## Consequences

**Positive:**
- Services are fully decoupled — `payment-service` has no compile-time or runtime dependency on `order-service`.
- Each service can be deployed, scaled, and restarted independently.
- The Kafka topic log provides a natural audit trail of every saga step.
- Adding a new participant (e.g., a fraud-detection service) requires only subscribing to an existing topic and publishing a new event — no changes to existing services.

**Negative / Trade-offs:**
- There is no single place to observe the full state of an in-flight saga. Saga state must be reconstructed by correlating events across multiple topics using `correlationId`.
- End-to-end latency is higher than a synchronous flow — an order moves through multiple async hops before reaching `CONFIRMED`.
- Debugging is more complex: a developer must trace `correlationId` across `order-service`, `product-service`, `payment-service`, and `notification-service` logs. Zipkin distributed tracing mitigates this.
- Compensation logic (the cancellation path) must be explicitly handled in every saga consumer that can receive a failure event.

---

## Implementation Reference

| Component | Location |
|---|---|
| Saga consumer | `order-service/.../kafka/OrderSagaConsumer.java` |
| Inventory reservation consumer | `product-service/.../kafka/InventoryReservationConsumer.java` |
| Payment saga consumer | `payment-service/.../kafka/PaymentSagaConsumer.java` |
| Notification consumer | `notification-service/.../kafka/NotificationsConsumer.java` |
| Kafka topic definitions | `docker-compose.yml` → `KAFKA_CREATE_TOPICS` |
