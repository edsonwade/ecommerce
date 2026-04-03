# Plan: Create Comprehensive  Production-Readiness Improvements

## Context

The e-commerce microservice project (10 Spring Boot services + infrastructure) has a well-architected foundation but lacks production-readiness in security, observability, reliability, and operations. The existing CLAUDE.md is essentially empty. The user wants a complete replacement that serves as both a project guide AND a detailed production-readiness improvement plan.

## What We're Doing

Replace `CLAUDE.md` with a comprehensive document that:
1. Documents the current architecture (build commands, service ports, dependencies, patterns)
2. Provides a phased production-readiness improvement plan covering all areas
3. Serves as actionable guidance for implementing each improvement

## CLAUDE.md Structure

### Section 1: Build & Run Commands
- Maven build commands (all modules, single service, skip tests)
- Docker-compose commands (full stack, infra only)
- Test commands (all, single module, single class/method)

### Section 2: Architecture Overview
- Multi-module Maven project (Java 17, Spring Boot 3.2.5, Spring Cloud 2023.0.1)
- Service startup order (config → discovery → apps)
- Service ports table (10 services)
- Database strategy (PostgreSQL x4, MongoDB, Redis)
- Inter-service communication (Feign sync, Kafka async)
- Order saga (transactional outbox pattern)
- Gateway filter chain (JWT + tenant validation)
- Multi-tenancy (tenant-context shared library)

### Section 3: Current Infrastructure State
- Summary of what's deployed (Kafka, Redis, PostgreSQL, MongoDB, Zipkin, Prometheus, Grafana, MailHog)
- Known limitations (single broker Kafka, no replication, hardcoded credentials)

### Section 4: Production-Readiness Improvement Plan

#### Phase 1: Security Hardening (Highest Priority)
1. **Secrets Management**
- Integrate HashiCorp Vault or Spring Cloud Vault
- Move all credentials from `.env`/`docker-compose.yml` to Vault
- Implement credential rotation for PostgreSQL, MongoDB, Redis
- Encrypt JWT secret and store in Vault
- Add `bootstrap.yml` configs for Vault in each service

2. **Service Authentication**
- Add authentication to Eureka dashboard
- Secure Prometheus/Grafana with proper credentials (not admin/admin)
- Add Redis AUTH password
- Configure Kafka SASL/SCRAM authentication
- Secure actuator endpoints (expose only health/prometheus, restrict others)

3. **TLS/Encryption**
- Enable TLS for inter-service communication
- Configure SSL for PostgreSQL connections
- Enable Redis TLS
- Configure Kafka SSL listeners
- Add mTLS between services (optional, service mesh)

4. **Network Security**
- Define Docker network policies (restrict service-to-service access)
- Remove exposed ports for internal-only services
- Add rate limiting per tenant on gateway (already partial)

#### Phase 2: Observability (High Priority)
5. **Structured Logging**
- Add `logback-spring.xml` to all 10 services
- Configure JSON format output (logstash-logback-encoder)
- Include traceId, spanId, tenantId in MDC
- Set appropriate log levels per environment (dev/staging/prod)
- Add log rotation and max file size limits

6. **Log Aggregation**
- Add ELK stack to docker-compose (Elasticsearch, Logstash, Kibana)
- OR add Loki + Promtail (lighter alternative)
- Configure Logstash/Promtail to collect from all services
- Create Kibana/Grafana dashboards for log search

7. **Prometheus Alerting**
- Create alerting rules file: `prometheus/alert.rules.yml`
- Rules: service down, high error rate (>5%), high latency (p99 > 2s), disk/memory pressure, Kafka consumer lag, database connection pool exhaustion
- Configure Alertmanager in docker-compose
- Add Slack/email notification channels

8. **Grafana Dashboards**
- Provision dashboards via JSON files in docker-compose
- Dashboards: JVM metrics, HTTP request rates, database connections, Kafka throughput, Redis hit rates, order saga flow, per-tenant metrics
- Configure Grafana datasources automatically

9. **Distributed Tracing Improvements**
- Reduce sampling from 100% to 10-20% for production
- Add persistent storage for Zipkin (Elasticsearch backend)
- Configure trace retention policy

#### Phase 3: Reliability & Resilience (Medium Priority)
10. **Database Reliability**
- Add PostgreSQL replication (primary-replica) for read scaling
- Configure MongoDB replica set (minimum 3 nodes)
- Document backup/restore procedures
- Add automated backup cron job to docker-compose

11. **Kafka Reliability**
- Scale to 3 brokers with replication factor 3
- Configure proper topic partitioning
- Set `min.insync.replicas=2`
- Replace Zookeeper with KRaft (Kafka 3.x+)

12. **Graceful Shutdown**
- Add `SIGTERM` handling in all Dockerfiles (`STOPSIGNAL SIGTERM`)
- Configure Spring Boot graceful shutdown (`server.shutdown=graceful`)
- Set shutdown timeout (30s default)
- Ensure Kafka consumers commit offsets on shutdown

13. **Dockerfile Fixes**
- Fix port mismatch (EXPOSE should match actual service port)
- Add `STOPSIGNAL SIGTERM`
- Document required environment variables

#### Phase 4: Operational Excellence (Lower Priority)
14. **Configuration Management**
- Encrypt sensitive config values in Config Server (Spring Cloud Config encryption)
- Add config file versioning (git-backed config server)
- Document all environment variables per service

15. **API Documentation**
- Validate OpenAPI schemas in CI
- Add API versioning strategy (URL path: /api/v1/, /api/v2/)
- Generate API client SDKs from OpenAPI specs

16. **CI/CD Pipeline**
- Add GitHub Actions workflow (build, test, lint, security scan)
- Add SAST scanning (SpotBugs, OWASP dependency check)
- Add container image scanning (Trivy)
- Enable tests in Docker builds (remove `-DskipTests`)

17. **Performance & Scaling**
- Add resource limits to docker-compose (CPU/memory)
- Document horizontal scaling strategy
- Add load testing config (k6 or Gatling)
- Configure Kafka partitioning for parallelism

### Section 5: Error Messages & Conventions
- MessageSource pattern for externalized messages
- Testing strategy (unit, integration with Testcontainers, BDD with Cucumber)

### Section 6: Flyway Migrations
- Migration naming conventions
- Which services have migrations (auth, order, payment, product, tenant)

## Files to Modify

| File | Action |
|------|--------|
| `CLAUDE.md` | Replace entirely with comprehensive content |

## Verification

- Read the generated CLAUDE.md and verify all sections are present
- Verify accuracy against current docker-compose.yml, pom.xml, and service configs
- Ensure build commands are correct by dry-running `mvn clean package -DskipTests`
