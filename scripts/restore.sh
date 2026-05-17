#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────────────────────────
#  restore.sh  —  Restore the MathAI PostgreSQL database from a backup file
#
#  Usage:
#    ./scripts/restore.sh backups/mathai-2025-06-01-120000.sql.gz
#
#  WARNING: This will DROP existing data and replace it with the backup.
#           Make sure the DB container is running.
# ──────────────────────────────────────────────────────────────────────────────
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

if [[ -z "$1" ]]; then
  echo "Usage: $0 <backup-file.sql.gz>"
  echo ""
  echo "Available backups:"
  ls -1 "$PROJECT_DIR/backups/"*.sql.gz 2>/dev/null || echo "  (none)"
  exit 1
fi

BACKUP_FILE="$1"
if [[ ! -f "$BACKUP_FILE" ]]; then
  echo "Error: backup file not found: $BACKUP_FILE"
  exit 1
fi

# ── Colours ──────────────────────────────────────────────────────────────────
GREEN="\033[0;32m"; RED="\033[0;31m"; CYAN="\033[0;36m"; NC="\033[0m"
info()    { echo "${CYAN}▶ $*${NC}"; }
success() { echo "${GREEN}✔ $*${NC}"; }
warn()    { echo "${RED}✖ $*${NC}"; }

cd "$PROJECT_DIR"

CONTAINER_ID=$(docker compose ps -q db 2>/dev/null || true)
if [[ -z "$CONTAINER_ID" ]]; then
  warn "DB container is not running. Start it first: docker compose up -d db"
  exit 1
fi

DB_PASSWORD="${DB_PASSWORD:-mathai_secret}"
if [[ -f "$PROJECT_DIR/.env" ]]; then
  source <(grep DB_PASSWORD "$PROJECT_DIR/.env" 2>/dev/null || true)
fi

# Confirm
echo ""
echo "⚠️  This will DESTROY the current database and replace with:"
echo "   $BACKUP_FILE"
echo ""
read -rp "  Type YES to confirm: " CONFIRM
if [[ "$CONFIRM" != "YES" ]]; then
  echo "Cancelled."
  exit 0
fi

info "Restoring database from backup..."
gunzip -c "$BACKUP_FILE" | docker exec -i -e PGPASSWORD="$DB_PASSWORD" "$CONTAINER_ID" \
  psql -U mathai -d mathai

success "Restore complete. Restart backend to re-run Flyway if needed:"
echo "  docker compose restart backend"
