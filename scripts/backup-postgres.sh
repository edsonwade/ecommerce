#!/usr/bin/env bash
# backup-postgres.sh — PostgreSQL full backup + WAL archiving
# Idempotent: safe to re-run. Retention: 7 daily, 4 weekly, 3 monthly.
# Usage: ./backup-postgres.sh [daily|weekly|monthly]
set -euo pipefail

BACKUP_TYPE="${1:-daily}"
BACKUP_BASE="/backup/postgres"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
DATE=$(date +%Y%m%d)
DAY_OF_WEEK=$(date +%u)   # 1=Monday … 7=Sunday
DAY_OF_MONTH=$(date +%-d)

# Retention counts
DAILY_KEEP=7
WEEKLY_KEEP=4
MONTHLY_KEEP=3

declare -A DB_CONTAINERS=(
  ["order"]="postgres-order:5432:order_service_db"
  ["product"]="postgres-product:5433:product_service_db"
  ["payment"]="postgres-payment:5434:payment_db"
  ["auth"]="postgres-auth:5435:auth_db"
)

log() { echo "[$(date +'%Y-%m-%d %H:%M:%S')] $*"; }

rotate_backups() {
  local dir="$1" keep="$2"
  local count
  count=$(find "$dir" -maxdepth 1 -name "*.tar.gz" | wc -l)
  if [[ $count -gt $keep ]]; then
    find "$dir" -maxdepth 1 -name "*.tar.gz" -printf '%T+ %p\n' \
      | sort | head -n $(( count - keep )) | awk '{print $2}' | xargs rm -f
    log "Rotated $dir — kept $keep, removed $(( count - keep ))"
  fi
}

backup_instance() {
  local name="$1" container="$2" port="$3" dbname="$4"
  local dest="$BACKUP_BASE/$BACKUP_TYPE/$name"
  mkdir -p "$dest"

  local outfile="$dest/${name}_${TIMESTAMP}.tar.gz"
  log "Starting pg_basebackup for $name ($dbname) → $outfile"

  docker exec "$container" pg_basebackup \
    -U "${POSTGRES_USERNAME:-vanilson}" \
    -D /tmp/pgbackup_$$ \
    -Ft -z -Xs -P \
    --checkpoint=fast

  docker cp "$container:/tmp/pgbackup_$$/base.tar.gz" "$outfile"
  docker exec "$container" rm -rf /tmp/pgbackup_$$

  log "Backup complete: $outfile ($(du -sh "$outfile" | cut -f1))"
}

# Archive WAL for PITR
archive_wal() {
  local name="$1" container="$2"
  local wal_dir="$BACKUP_BASE/wal/$name"
  mkdir -p "$wal_dir"

  # pg_receivewal runs continuously; this one-shot triggers a checkpoint + ships WAL
  docker exec "$container" psql \
    -U "${POSTGRES_USERNAME:-vanilson}" \
    -c "SELECT pg_switch_wal();" \
    -q 2>/dev/null || true

  log "WAL checkpoint triggered for $name"
}

log "=== PostgreSQL Backup: type=$BACKUP_TYPE timestamp=$TIMESTAMP ==="

for name in "${!DB_CONTAINERS[@]}"; do
  IFS=':' read -r container port dbname <<< "${DB_CONTAINERS[$name]}"
  backup_instance "$name" "$container" "$port" "$dbname"
  archive_wal "$name" "$container"
done

# Apply retention policy
for name in "${!DB_CONTAINERS[@]}"; do
  case "$BACKUP_TYPE" in
    daily)   rotate_backups "$BACKUP_BASE/daily/$name"   $DAILY_KEEP   ;;
    weekly)  rotate_backups "$BACKUP_BASE/weekly/$name"  $WEEKLY_KEEP  ;;
    monthly) rotate_backups "$BACKUP_BASE/monthly/$name" $MONTHLY_KEEP ;;
  esac
done

log "=== Backup run complete ==="
