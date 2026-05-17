#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────────────────────────
#  backup.sh  —  Dump the MathAI PostgreSQL database to a timestamped file
#
#  Usage:
#    ./scripts/backup.sh                  # auto filename in backups/
#    ./scripts/backup.sh pre-upgrade      # label as pre-upgrade
#    ./scripts/backup.sh my-label         # custom label
#
#  Output:  backups/mathai-YYYY-MM-DD-HHMMSS[-label].sql
# ──────────────────────────────────────────────────────────────────────────────
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
BACKUP_DIR="$PROJECT_DIR/backups"
mkdir -p "$BACKUP_DIR"

LABEL="${1:-}"
TIMESTAMP=$(date '+%Y-%m-%d-%H%M%S')
if [[ -n "$LABEL" ]]; then
  BACKUP_FILE="$BACKUP_DIR/mathai-${TIMESTAMP}-${LABEL}.sql"
else
  BACKUP_FILE="$BACKUP_DIR/mathai-${TIMESTAMP}.sql"
fi

# ── Colours ──────────────────────────────────────────────────────────────────
GREEN="\033[0;32m"; RED="\033[0;31m"; CYAN="\033[0;36m"; NC="\033[0m"
info()    { echo "${CYAN}▶ $*${NC}"; }
success() { echo "${GREEN}✔ $*${NC}"; }
warn()    { echo "${RED}✖ $*${NC}"; }

cd "$PROJECT_DIR"

# ── Check container is running ───────────────────────────────────────────────
CONTAINER_ID=$(docker compose ps -q db 2>/dev/null || true)
if [[ -z "$CONTAINER_ID" ]]; then
  warn "DB container is not running. Start it first: docker compose up -d db"
  exit 1
fi

# Load DB password from .env or docker-compose.yml
DB_PASSWORD="${DB_PASSWORD:-mathai_secret}"
if [[ -f "$PROJECT_DIR/.env" ]]; then
  source <(grep DB_PASSWORD "$PROJECT_DIR/.env" 2>/dev/null || true)
fi

info "Dumping database to: $BACKUP_FILE"
docker exec -e PGPASSWORD="$DB_PASSWORD" "$CONTAINER_ID" \
  pg_dump -U mathai -d mathai --clean --if-exists --no-owner > "$BACKUP_FILE"

# Compress
gzip -f "$BACKUP_FILE"
BACKUP_FILE="${BACKUP_FILE}.gz"

success "Backup complete — $(du -h "$BACKUP_FILE" | cut -f1)"
echo "  $BACKUP_FILE"
