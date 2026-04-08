#!/usr/bin/env bash
# restore-postgres.sh — PostgreSQL PITR restore to specific timestamp
# Usage: ./restore-postgres.sh <db-name> <backup-file> [target-time]
#   db-name:     order | product | payment | auth
#   backup-file: path to .tar.gz produced by backup-postgres.sh
#   target-time: ISO-8601 timestamp for PITR, e.g. "2024-01-15 14:30:00+00"
#                Omit to restore to end of backup (no PITR)
set -euo pipefail

DB_NAME="${1:?Usage: $0 <db-name> <backup-file> [target-time]}"
BACKUP_FILE="${2:?Usage: $0 <db-name> <backup-file> [target-time]}"
TARGET_TIME="${3:-}"

declare -A DB_CONTAINERS=(
  ["order"]="postgres-order:order_service_db"
  ["product"]="postgres-product:product_service_db"
  ["payment"]="postgres-payment:payment_db"
  ["auth"]="postgres-auth:auth_db"
)

log() { echo "[$(date +'%Y-%m-%d %H:%M:%S')] $*"; }
die() { log "ERROR: $*"; exit 1; }

[[ -v "DB_CONTAINERS[$DB_NAME]" ]] || die "Unknown db-name '$DB_NAME'. Valid: ${!DB_CONTAINERS[*]}"
[[ -f "$BACKUP_FILE" ]] || die "Backup file not found: $BACKUP_FILE"

IFS=':' read -r CONTAINER DBNAME <<< "${DB_CONTAINERS[$DB_NAME]}"
PGDATA="/var/lib/postgresql/data"
WAL_ARCHIVE="/backup/postgres/wal/$DB_NAME"
RESTORE_WORK="/tmp/pg_restore_$$"

log "=== PostgreSQL PITR Restore ==="
log "Database  : $DB_NAME ($DBNAME)"
log "Container : $CONTAINER"
log "Backup    : $BACKUP_FILE"
[[ -n "$TARGET_TIME" ]] && log "Target    : $TARGET_TIME (PITR)" || log "Target    : end of backup"

# Safety check — require explicit confirmation for payment (zero data loss requirement)
if [[ "$DB_NAME" == "payment" ]]; then
  read -rp "WARNING: Restoring PAYMENT database. Type 'CONFIRM' to proceed: " confirm
  [[ "$confirm" == "CONFIRM" ]] || die "Aborted by user"
fi

log "Step 1: Stopping container $CONTAINER"
docker stop "$CONTAINER"

log "Step 2: Extracting backup to temp directory"
mkdir -p "$RESTORE_WORK"
tar -xzf "$BACKUP_FILE" -C "$RESTORE_WORK"

log "Step 3: Clearing existing data directory"
docker run --rm \
  -v "${CONTAINER}_data:/var/lib/postgresql/data" \
  postgres:15-alpine \
  sh -c "rm -rf $PGDATA/* && chown -R postgres:postgres $PGDATA"

log "Step 4: Copying restored data"
docker run --rm \
  -v "$RESTORE_WORK:/restore:ro" \
  -v "${CONTAINER}_data:/var/lib/postgresql/data" \
  postgres:15-alpine \
  sh -c "cp -a /restore/. $PGDATA/ && chown -R postgres:postgres $PGDATA"

# Write recovery config for PITR
if [[ -n "$TARGET_TIME" ]]; then
  log "Step 5: Writing recovery.conf for PITR target: $TARGET_TIME"
  docker run --rm \
    -v "${CONTAINER}_data:/var/lib/postgresql/data" \
    -v "$WAL_ARCHIVE:/wal_archive:ro" \
    postgres:15-alpine \
    sh -c "cat > $PGDATA/recovery.signal << 'EOF'
EOF
cat >> $PGDATA/postgresql.auto.conf << EOF
restore_command = 'cp /wal_archive/%f %p'
recovery_target_time = '$TARGET_TIME'
recovery_target_action = 'promote'
EOF
chown postgres:postgres $PGDATA/recovery.signal $PGDATA/postgresql.auto.conf"
fi

log "Step 6: Starting container $CONTAINER"
docker start "$CONTAINER"

log "Step 7: Waiting for PostgreSQL to be ready..."
for i in {1..30}; do
  if docker exec "$CONTAINER" pg_isready -U "${POSTGRES_USERNAME:-vanilson}" -q 2>/dev/null; then
    log "PostgreSQL is ready."
    break
  fi
  sleep 2
done

log "Step 8: Verifying restore"
docker exec "$CONTAINER" psql \
  -U "${POSTGRES_USERNAME:-vanilson}" \
  -d "$DBNAME" \
  -c "SELECT pg_is_in_recovery(), now();"

rm -rf "$RESTORE_WORK"
log "=== Restore complete for $DB_NAME ==="
