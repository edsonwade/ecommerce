#!/bin/bash
# ==============================================================
# backup.sh — Automated backup for all databases
# Usage: ./scripts/backup.sh
# Cron: 0 2 * * * /app/scripts/backup.sh  (daily at 2am)
# ==============================================================
set -euo pipefail

BACKUP_DIR="${BACKUP_DIR:-./backups}"
DATE=$(date +%Y%m%d_%H%M%S)
RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-7}"

mkdir -p "$BACKUP_DIR"/{postgres,mongodb}

echo "[backup] Starting backup — $DATE"

# -------------------------------------------------------
# PostgreSQL backups
# -------------------------------------------------------
for DB in "postgres-order:order_service_db" "postgres-product:product_service_db" "postgres-payment:payment_db" "postgres-auth:auth_db"; do
  HOST="${DB%%:*}"
  DBNAME="${DB##*:}"
  echo "[backup] Dumping $DBNAME from $HOST..."
  docker exec "$HOST" pg_dump \
    -U "${POSTGRES_USERNAME:-vanilson}" \
    -d "$DBNAME" \
    --format=custom \
    --compress=9 \
    > "$BACKUP_DIR/postgres/${DBNAME}_${DATE}.dump" 2>/dev/null
  echo "[backup] $DBNAME → $BACKUP_DIR/postgres/${DBNAME}_${DATE}.dump"
done

# -------------------------------------------------------
# MongoDB backup
# -------------------------------------------------------
echo "[backup] Dumping MongoDB..."
docker exec mongodb mongodump \
  --username "${MONGO_USERNAME:-vanilson}" \
  --password "${MONGO_PASSWORD:-vanilson}" \
  --authenticationDatabase admin \
  --gzip \
  --archive > "$BACKUP_DIR/mongodb/mongodb_${DATE}.archive"
echo "[backup] MongoDB → $BACKUP_DIR/mongodb/mongodb_${DATE}.archive"

# -------------------------------------------------------
# Cleanup old backups
# -------------------------------------------------------
echo "[backup] Removing backups older than $RETENTION_DAYS days..."
find "$BACKUP_DIR" -type f -mtime +$RETENTION_DAYS -delete

echo "[backup] Backup complete — $DATE"
