# HA Zero-Downtime Deploy — Runbook (Docker Compose + nginx-edge)

> **Scope:** rolling updates of critical-path services (frontend, gateway, auth, product, cart, order, payment) on the HA compose stack (`docker-compose.yml` + `docker-compose.scale.yml`).
> **Principle:** there is never a moment with 0 healthy instances of a critical service.
> **Status:** validated live on 2026-07-05 — 348/348 requests (100%) succeeded across two consecutive gateway instance restarts (evidence: `docs/deploy-proof-f2.log`).

---

## When NOT to use this runbook

- **Normal dev stack** (1x of everything, no `scale` overlay) — there is no second instance; a downtime window is expected and accepted.
- **Services outside the x2 scope** (customer, notification, tenant, config, discovery) — normal deploy, accepted window.
- **nginx-edge config-only changes** — `docker exec nginx-edge nginx -s reload` is enough; do not recreate containers.

## Hard rules

1. **Never run `docker compose up -d --build <svc>` directly against the base service** on the HA stack — it recreates the only published instance and causes exactly the downtime this runbook exists to prevent.
2. **No step proceeds until the previous step's gate passes** (container healthcheck + Eureka registration).
3. **No zero-downtime claim without the proof log** — run the proof loop for the entire window and keep the log as evidence.
4. For a bounce **without any config/image change**, use `docker restart <container>` — never `--force-recreate` (container churn bloats the WSL2 vhdx).

---

## Pre-checks (before ANY rolling update)

| # | Check | Command | Gate |
|---|---|---|---|
| P1 | HA stack active (both instances exist) | `docker ps --format "{{.Names}}"` filtered by `<svc>` | `<svc>` AND `<svc>-2` present and `healthy` |
| P2 | Both registered in Eureka | `curl -u eureka:<pass> http://localhost:8761/eureka/apps/<SVC> -H "Accept: application/json"` | 2 instances with `"status":"UP"` |
| P3 | Flyway migration review (if the deploy includes new `.sql`) | review the migration diff | **Expand/contract** — must be compatible with N-1 code (new nullable column OK; rename/drop NOT OK — split into 2 releases) |
| P4 | Proof loop running (see Proof Protocol) | separate terminal | log accumulating 2xx |

> **Eureka naming gotcha:** the gateway registers as `GATEWAY-SERVICE` (not `GATEWAY-API-SERVICE`).

## Rolling update of one service (exact order)

```powershell
# Stack files: ALWAYS the same combination the stack was brought up with
$F = "-f docker-compose.yml -f docker-compose.scale.yml"   # (+ -f docker-compose.ha.yml if active)

# 1. Build the new image — recreates nothing
docker compose $F build <svc>

# 2. Recreate ONLY instance B
docker compose $F up -d --no-deps <svc>-2
```

- **Gate A:** `docker inspect --format "{{.State.Health.Status}}" <svc>-2` → `healthy`
- **Gate B:** Eureka shows `<svc>-2` UP (command P2)
- **Gate C:** wait ≥ 15s (load-balancer/Eureka cache TTLs with the overlay tuning)

```powershell
# 3. Recreate instance A (B is now serving)
docker compose $F up -d --no-deps <svc>
```

Repeat gates A + B + C for `<svc>`.

```powershell
# 4. ONLY for frontend/gateway (nginx-edge upstreams — container IPs may have changed):
docker exec nginx-edge nginx -s reload
```

**Multi-service order:** one service at a time, with proof between each. Never two rolling updates in parallel (overlapping Kafka rebalances + ambiguous gates).

## Proof Protocol (mandatory — without proof it is not done)

In a separate terminal, from BEFORE step 1 until after step 4:

```powershell
while ($true) { try { $r = Invoke-WebRequest http://localhost:8080/api/v1/products -UseBasicParsing -TimeoutSec 5; "$(Get-Date -f HH:mm:ss.fff) $($r.StatusCode)" } catch { "$(Get-Date -f HH:mm:ss.fff) FAIL $($_.Exception.Message)" }; Start-Sleep -m 1000 } | Tee-Object deploy-proof.log
```

**Pass criterion:** 0 `FAIL` and 0 non-2xx statuses during the entire window. For a frontend rolling update, run the loop against `http://localhost:8080/` as well. Keep the log as evidence.

## Troubleshooting

| Symptom | Probable cause | Action |
|---|---|---|
| Intermittent 502/503 for ~10-60s after recreating an instance | Eureka lag: the gateway still has the dead instance cached | Confirm the overlay tuning env vars (`SPRING_CLOUD_LOADBALANCER_CACHE_TTL=5s`, fetch/lease 5s/15s); respect Gate C |
| Constant 502 on `/` or `/api/` after a frontend/gateway rolling | nginx-edge holds a stale IP for the recreated container | `docker exec nginx-edge nginx -s reload` (step 4 was skipped) |
| 404 on `/assets/*.js` during a frontend rolling | New index.html mixed with old assets across instances | Confirm `ip_hash` on the `frontend_up` upstream; the window should be seconds |
| New instance `healthy` but missing from Eureka | `eureka.instance.instance-id` collision (the 2nd replaced the 1st in the registry) | Inject a unique instance-id per instance |
| New instance fails at startup on Flyway | Incompatible migration or a failed-migration marker | `flyway.repair()` already runs at startup (product-service); review the migration against the expand/contract rule |
| Duplicate events in notification-service during an order-service rolling | 2x OutboxEventPublisher (expected — at-least-once delivery) | Nothing — `ProcessedEvent` deduplicates. A user-visible effect means an idempotency bug in the consumer, not a rolling problem |
| Purchase fails during a payment-service rolling | Kafka rebalance mid-processing | Verify manual ack + retry; the event reprocesses — confirm in the log that the saga completed |
| False 503s while a new JVM boots (all instances healthy) | A booting JVM starving serving instances on a small host | The overlay caps every replica at 1.5 CPUs for exactly this reason — confirm the cap is active |

## Red flags — STOP immediately

- "I'll just `up -d --build` the base service, it's faster" → that is the downtime this runbook exists to prevent.
- "Healthcheck passed, I'll skip the Eureka check" → healthy ≠ registered; the gateway does not know the instance yet.
- "I'll recreate both instances at once to save time" → 0 instances during JVM startup = guaranteed downtime.
- "I don't need the proof loop, it went fine" → without `deploy-proof.log` the zero-downtime claim has no evidence.
- "The migration renames a column but the deploy is fast" → N-1 code breaks the instant the migration runs; expand/contract always.

## Validation history

| Date | Phase | Result |
|---|---|---|
| 2026-07-05 | F2 — gateway rolling restart (both instances, sequentially) | 348/348 requests 2xx (100%), 0 failures — `docs/deploy-proof-f2.log` |
| 2026-07-05 | Root cause of earlier 27% timeout rate | Zipkin span export blocking the gateway's reactive event loop → tracing disabled in the scale overlay (`MANAGEMENT_TRACING_ENABLED=false`); base compose still traces |
