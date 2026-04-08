#!/usr/bin/env bash
# backup-mongo.sh — MongoDB consistent backup using mongodump --oplog
# Idempotent: safe to re-run. Retention: 7 daily, 4 weekly, 3 monthly.
# Usage: ./backup-mongo.sh [daily|weekly|monthly]
set -euo pipefail

BACKUP_TYPE="${1:-daily}"
BACKUP_BASE="/backup/mongodb"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)

DAILY_KEEP=7
WEEKLY_KEEP=4
MONTHLY_KEEP=3

MONGO_CONTAINER="${MONGO_CONTAINER:-mongo1}"
MONGO_USER="${MONGO_USERNAME:-vanilson}"
MONGO_PASS="${MONGO_PASSWORD:-vanilson}"
MONGO_RS_URI="mongodb://${MONGO_USER}:${MONGO_PASS}@mongo1:27017,mongo2:27017,mongo3:27017/?replicaSet=rs0&authSource=admin"

log() { echo "[$(date +'%Y-%m-%d %H:%M:%S')] $*"; }

rotate_backups() {
  local dir="$1" keep="$2"
  local count
  count=$(find "$dir" -maxdepth 1 -name "*.archive.gz" 2>/dev/null | wc -l)
  if [[ $count -gt $keep ]]; then
    find "$dir" -maxdepth 1 -name "*.archive.gz" -printf '%T+ %p\n' \
      | sort | head -n $(( count - keep )) | awk '{print $2}' | xargs rm -f
    log "Rotated $dir — kept $keep backups"
  fi
}

dest="$BACKUP_BASE/$BACKUP_TYPE"
mkdir -p "$dest"
outfile="$dest/mongodb_${TIMESTAMP}.archive.gz"

log "=== MongoDB Backup: type=$BACKUP_TYPE timestamp=$TIMESTAMP ==="
log "Destination: $outfile"

# mongodump with --oplog for consistent point-in-time snapshot across all databases
docker exec "$MONGO_CONTAINER" mongodump \
  --uri="$MONGO_RS_URI" \
  --oplog \
  --gzip \
  --archive="/tmp/mongodb_backup_$$.archive.gz"

docker cp "$MONGO_CONTAINER:/tmp/mongodb_backup_$$.archive.gz" "$outfile"
docker exec "$MONGO_CONTAINER" rm -f "/tmp/mongodb_backup_$$.archive.gz"

SIZE=$(du -sh "$outfile" | cut -f1)
log "Backup complete: $outfile ($SIZE)"

case "$BACKUP_TYPE" in
  daily)   rotate_backups "$BACKUP_BASE/daily"   $DAILY_KEEP   ;;
  weekly)  rotate_backups "$BACKUP_BASE/weekly"  $WEEKLY_KEEP  ;;
  monthly) rotate_backups "$BACKUP_BASE/monthly" $MONTHLY_KEEP ;;
esac

log "=== MongoDB backup run complete ==="
