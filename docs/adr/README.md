# Architecture Decision Records

This directory contains Architecture Decision Records (ADRs) for the e-commerce microservices platform. Each ADR documents a significant architectural choice: the context that motivated it, the decision made, the alternatives considered, and the resulting trade-offs.

ADRs are immutable once accepted. If a decision is reversed or superseded, a new ADR is created with a reference to the original.

## Index

| ADR | Title | Status |
|---|---|---|
| [ADR-001](ADR-001-choreography-saga.md) | Choreography Saga over Orchestration for Order Processing | Accepted |
| [ADR-002](ADR-002-transactional-outbox.md) | Transactional Outbox Pattern for Reliable Event Publishing | Accepted |
| [ADR-003](ADR-003-polyglot-persistence.md) | Polyglot Persistence Strategy | Accepted |
| [ADR-004](ADR-004-multitenant-threadlocal-hibernate.md) | Multi-Tenancy via ThreadLocal + Hibernate Filters | Accepted |
