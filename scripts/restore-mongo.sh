#!/usr/bin/env bash
# restore-mongo.sh — MongoDB restore from mongodump archive
# Usage: ./restore-mongo.sh <backup-file> [--drop]
#   backup-file: path to .archive.gz from backup-mongo.sh
#   --drop:      drop existing collections before restore (default: merge)
set -euo pipefail

BACKUP_FILE="${1:?Usage: $0 <backup-file> [--drop]}"
DROP_FLAG="${2:-}"

MONGO_CONTAINER="${MONGO_CONTAINER:-mongo1}"
MONGO_USER="${MONGO_USERNAME:-vanilson}"
MONGO_PASS="${MONGO_PASSWORD:-vanilson}"
MONGO_RS_URI="mongodb://${MONGO_USER}:${MONGO_PASS}@mongo1:27017,mongo2:27017,mongo3:27017/?replicaSet=rs0&authSource=admin"

log() { echo "[$(date +'%Y-%m-%d %H:%M:%S')] $*"; }
die() { log "ERROR: $*"; exit 1; }

[[ -f "$BACKUP_FILE" ]] || die "Backup file not found: $BACKUP_FILE"

log "=== MongoDB Restore ==="
log "Backup    : $BACKUP_FILE"
[[ "$DROP_FLAG" == "--drop" ]] && log "Mode      : DROP existing collections before restore" || log "Mode      : Merge (no drop)"

read -rp "This will restore MongoDB data. Type 'CONFIRM' to proceed: " confirm
[[ "$confirm" == "CONFIRM" ]] || die "Aborted by user"

log "Step 1: Copying backup archive to container"
REMOTE_FILE="/tmp/mongodb_restore_$$.archive.gz"
docker cp "$BACKUP_FILE" "$MONGO_CONTAINER:$REMOTE_FILE"

log "Step 2: Running mongorestore"
EXTRA_ARGS=""
[[ "$DROP_FLAG" == "--drop" ]] && EXTRA_ARGS="--drop"

docker exec "$MONGO_CONTAINER" mongorestore \
  --uri="$MONGO_RS_URI" \
  --oplogReplay \
  --gzip \
  --archive="$REMOTE_FILE" \
  $EXTRA_ARGS

docker exec "$MONGO_CONTAINER" rm -f "$REMOTE_FILE"

log "Step 3: Verifying restore"
docker exec "$MONGO_CONTAINER" mongosh \
  --uri="$MONGO_RS_URI" \
  --eval 'db.adminCommand({ping:1})' \
  --quiet

log "=== MongoDB restore complete ==="
