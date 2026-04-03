#!/bin/bash
# ==============================================================
# restore.sh — Restore a database from backup
# Usage: ./scripts/restore.sh postgres order_service_db 20240101_020000
#        ./scripts/restore.sh mongodb "" 20240101_020000
# ==============================================================
set -euo pipefail

TYPE="${1:-postgres}"
DBNAME="${2:-}"
DATE_TAG="${3:-}"
BACKUP_DIR="${BACKUP_DIR:-./backups}"

if [[ -z "$DATE_TAG" ]]; then
  echo "Usage: $0 <postgres|mongodb> <dbname> <date_tag>"
  exit 1
fi

case "$TYPE" in
  postgres)
    BACKUP_FILE="$BACKUP_DIR/postgres/${DBNAME}_${DATE_TAG}.dump"
    [[ -f "$BACKUP_FILE" ]] || { echo "Backup not found: $BACKUP_FILE"; exit 1; }
    echo "[restore] Restoring $DBNAME from $BACKUP_FILE..."
    HOST="postgres-${DBNAME%%_*}"
    docker exec -i "$HOST" pg_restore \
      -U "${POSTGRES_USERNAME:-vanilson}" \
      -d "$DBNAME" \
      --clean --if-exists \
      < "$BACKUP_FILE"
    echo "[restore] PostgreSQL restore complete."
    ;;
  mongodb)
    BACKUP_FILE="$BACKUP_DIR/mongodb/mongodb_${DATE_TAG}.archive"
    [[ -f "$BACKUP_FILE" ]] || { echo "Backup not found: $BACKUP_FILE"; exit 1; }
    echo "[restore] Restoring MongoDB from $BACKUP_FILE..."
    docker exec -i mongodb mongorestore \
      --username "${MONGO_USERNAME:-vanilson}" \
      --password "${MONGO_PASSWORD:-vanilson}" \
      --authenticationDatabase admin \
      --gzip --archive \
      < "$BACKUP_FILE"
    echo "[restore] MongoDB restore complete."
    ;;
  *)
    echo "Unknown type: $TYPE. Use 'postgres' or 'mongodb'."
    exit 1
    ;;
esac
