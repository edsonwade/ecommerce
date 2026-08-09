#!/bin/sh
# ============================================================
# Continuous warm-up (fail-open).
# Issues the real hot-path requests after the gateway is healthy
# so the FIRST user never pays JIT/pool cold-start.
# Runs once, or loops every WARMUP_INTERVAL_SECONDS when WARMUP_LOOP=true.
# NEVER exits non-zero on request failure (fail-open) — the stack
# being briefly cold must not crash-loop this helper.
# ============================================================

GATEWAY_URL="${GATEWAY_URL:-http://gateway-api-service:8222}"
TENANT="${WARMUP_TENANT_ID:-default}"
EMAIL="${WARMUP_ADMIN_EMAIL:-admin@obsidian.com}"
PASSWORD="${WARMUP_ADMIN_PASSWORD:-Admin@123!}"
LOOP="${WARMUP_LOOP:-false}"
INTERVAL="${WARMUP_INTERVAL_SECONDS:-300}"

log() { echo "[warmup] $(date -u +%Y-%m-%dT%H:%M:%SZ) $*"; }

# A single curl that never breaks the script. Prints the HTTP status.
hit() {
  method="$1"; path="$2"; body="$3"
  if [ -n "$body" ]; then
    code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 30 \
      -X "$method" "${GATEWAY_URL}${path}" \
      -H "Content-Type: application/json" \
      -H "X-Tenant-Id: ${TENANT}" \
      -d "$body" 2>/dev/null) || code="ERR"
  else
    code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 30 \
      -X "$method" "${GATEWAY_URL}${path}" \
      -H "X-Tenant-Id: ${TENANT}" 2>/dev/null) || code="ERR"
  fi
  log "$method $path -> $code"
}

warm_once() {
  # Auth hot path (BCrypt + JWT issue) — the slowest first-hit.
  hit POST /api/v1/auth/login "{\"email\":\"${EMAIL}\",\"password\":\"${PASSWORD}\"}"
  # Public catalog reads — product-service + category JVMs.
  hit GET /api/v1/products ""
  hit GET /api/v1/categories ""
  # Gateway itself.
  hit GET /actuator/health ""
}

log "starting (loop=${LOOP}, interval=${INTERVAL}s, gateway=${GATEWAY_URL})"

if [ "$LOOP" = "true" ]; then
  while true; do
    warm_once
    log "cycle done; sleeping ${INTERVAL}s"
    sleep "$INTERVAL"
  done
else
  warm_once
  log "one-shot done"
fi

exit 0
