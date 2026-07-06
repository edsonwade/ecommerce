# Monitoring & Alerting — Runbook

> **Scope:** operating the observability stack (Prometheus, Grafana, AlertManager, Loki/Promtail, Zipkin) and troubleshooting scrape/alert incidents.
> **Status:** scrape security model live-verified 2026-07-06 — all 11 Prometheus targets `UP`, zero firing alerts.

---

## Stack components

| Component | Port | Purpose |
|---|---|---|
| Prometheus | 9090 | Metrics scraping (15s interval, 15-day retention) |
| Grafana | 3000 | Pre-provisioned dashboards and datasources |
| AlertManager | 9093 | Severity-based alert routing (Slack + email) |
| Loki | 3100 | Centralized log aggregation (7-day retention) |
| Promtail | — | Container log shipper to Loki |
| Zipkin | 9411 | Distributed tracing (disabled on the gateway in the HA scale overlay — the blocking exporter stalled the reactive event loop) |

## Scrape security model

Every scraped service exposes exactly **three** public actuator endpoints, declared in a unit-tested `PUBLIC_ACTUATOR_ENDPOINTS` array in its `SecurityConfig`:

```
/actuator/health
/actuator/health/**
/actuator/prometheus
```

Rules:

- **Never open `/actuator/**`** — `env`, `heapdump`, `beans`, etc. must stay authenticated.
- `/actuator/prometheus` must be public: Prometheus sends no credentials, so an authenticated endpoint returns 401 on every scrape and the target reports `down` forever.
- **config-service and discovery-service** need the `micrometer-registry-prometheus` dependency explicitly (plus `management.endpoints.web.exposure.include: ...,prometheus`) — without it the endpoint does not exist at all (404, not 401).
- Any **new service** must ship the same allowlist + dependency, its four test layers (unit array test, `@WebMvcTest` slice, Testcontainers integration, Cucumber BDD `@monitoring` feature), and a scrape job in `prometheus.yml`.

## Verification procedure

1. **Targets:** `http://localhost:9090/targets` — every target must be `UP` with an empty error column. Via API:
   ```powershell
   Invoke-RestMethod "http://localhost:9090/api/v1/targets?state=active" |
     % { $_.data.activeTargets } |
     % { "{0} {1} {2}" -f $_.labels.job, $_.health, $_.lastError }
   ```
2. **Alerts:** `http://localhost:9090/api/v1/alerts` must return zero firing alerts on a healthy stack (`ServiceDown` in particular).
3. **Single scrape by hand:** `curl http://localhost:<port>/actuator/prometheus` → HTTP 200 with metric text. A 401 means the service's security allowlist regressed; a 404 means the micrometer registry dependency/exposure is missing.
4. **Grafana:** dashboards populate within one scrape interval (15s) of targets going `UP`.

## Alert rules (preconfigured)

- Service availability: `ServiceDown`, `HighRestartRate`
- HTTP errors: >5% server error rate, >20% client errors
- Latency: P99 >2s warning, >5s critical
- JVM memory: >85% warning, >95% critical
- DB connection pool: >75% warning, >90% critical
- Kafka consumer lag: >1,000 warning, >10,000 critical
- Disk space: <15% remaining

## Incident case studies (real, resolved)

### 1. All targets down + permanent ServiceDown alerts (2026-07-05/06)

**Symptom chain:** 7 services returned **401** on `/actuator/prometheus` (auth required), discovery returned **404** (no micrometer registry) → Prometheus marked them `down` → `ServiceDown` fired permanently for every service → AlertManager spammed the Slack webhook.

**Fix:** the allowlist + dependency model described above, applied to all 8 services, with all four test layers per service. Verify with the procedure above after any security-config change.

### 2. Promtail at 122% CPU / 189MB log file (2026-07-05)

**Root cause was NOT promtail.** The false `ServiceDown` alerts (case 1) were routed to a **placeholder Slack webhook URL** in `alertmanager.yml`; each failed webhook call logged an entire HTML error page, the log grew to 189MB, and promtail burned CPU tailing it.

**Lesson:** a hot log shipper usually means a hot log *producer* — find who is writing, not who is reading. Never leave placeholder webhook/receiver URLs in `alertmanager.yml`; leave the receiver unconfigured instead.

### 3. Notification-service Kafka hot loop at ~50% CPU (2026-07-05)

**Symptom:** a single undeserializable ("poison") Kafka record made the consumer spin forever: deserialization fails → offset never commits → same record fetched again, at CPU speed.

**Fix (in place):** `ErrorHandlingDeserializer` wrapping the value deserializer + `VALUE_DEFAULT_TYPE` + a `DeserializationErrorHandler` that logs and skips the poison record; producers send type headers.

**Detection:** sustained CPU on a consumer service with a repeating deserialization stack trace in its log and a consumer-group offset that never advances.

### 4. SMTP health indicator locked the shared mail inbox (2026-07-04)

**Symptom:** password-reset emails silently stopped being delivered (send code fail-open, so no error surfaced).

**Root cause:** notification-service's `SmtpHealthIndicator` performed a **full SMTP AUTH on every ~18s health poll** against the shared Mailtrap inbox → provider rate-limited with `535 Too many failed login attempts` → inbox permanently locked (the lock does not expire on its own; credentials had to be reset).

**Fix (in place):** SMTP health indicator gated off by default (`management.health.mail/smtp` disabled). **Never** put an authenticating side-effect in a health indicator — health endpoints are polled continuously by Docker, Prometheus, and load balancers.

## Operational notes

- Docker container logs are **UTC** (local time −1h) — align timestamps before correlating with Prometheus/Grafana.
- Prometheus, Grafana, AlertManager, Loki and Promtail live on `monitoring-net`; scraped services are reached through their service names on the compose network.
- After changing `alertmanager.yml` or `prometheus.yml`, only that container needs `docker compose up -d <name>` — do not bounce the whole stack.
