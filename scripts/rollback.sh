#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────────────────────────
#  rollback.sh  —  Roll back MathAI to the last working build + DB state
#
#  Usage:
#    ./scripts/rollback.sh
#
#  What it does:
#    1. Stops all containers
#    2. Restores the latest pre-upgrade DB backup
#    3. Swaps Docker images back to the previous build
#    4. Restarts containers (no rebuild — uses rollback images)
#
#  Prerequisites:
#    - A previous deploy must have been made (which saved rollback images)
#    - At least one pre-upgrade backup must exist
# ──────────────────────────────────────────────────────────────────────────────
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

# ── Colours ──────────────────────────────────────────────────────────────────
GREEN="\033[0;32m"; YELLOW="\033[1;33m"; RED="\033[0;31m"; CYAN="\033[0;36m"; NC="\033[0m"
info()    { echo "${CYAN}▶ $*${NC}"; }
success() { echo "${GREEN}✔ $*${NC}"; }
warn()    { echo "${YELLOW}⚠ $*${NC}"; }
error()   { echo "${RED}✖ $*${NC}"; exit 1; }

cd "$PROJECT_DIR"

echo ""
echo "══════════════════════════════════════════════════"
echo "   MathAI — Rollback to Last Working Build"
echo "══════════════════════════════════════════════════"
echo ""

# ── Confirm ──────────────────────────────────────────────────────────────────
echo "⚠️  This will:"
echo "   1. Stop all running containers"
echo "   2. Restore the database from the latest pre-upgrade backup"
echo "   3. Roll back Docker images to the previous build"
echo "   4. Restart all services"
echo ""
read -rp "  Type YES to confirm: " CONFIRM
if [[ "$CONFIRM" != "YES" ]]; then
  echo "Cancelled."
  exit 0
fi
echo ""

# ── Load .env if present ─────────────────────────────────────────────────────
if [[ -f .env ]]; then
  while IFS= read -r line || [[ -n "$line" ]]; do
    [[ -z "$line" || "$line" == \#* ]] && continue
    export "$line"
  done < .env
  info "Loaded .env"
fi

# ── Step 1: Stop containers ───────────────────────────────────────────────────
info "Step 1/4 — Stopping containers..."
export LOG_DIR="${LOG_DIR:-$PROJECT_DIR/logs}"
docker compose down --remove-orphans 2>&1 || true
success "Containers stopped."
echo ""

# ── Step 2: Restore DB from latest pre-upgrade backup ────────────────────────
info "Step 2/4 — Restoring database from latest pre-upgrade backup..."

# Find latest pre-upgrade backup, fall back to any backup
LATEST_BACKUP=$(ls -1t "$PROJECT_DIR/backups/"*-pre-upgrade.sql.gz 2>/dev/null | head -1)
if [[ -z "$LATEST_BACKUP" ]]; then
  LATEST_BACKUP=$(ls -1t "$PROJECT_DIR/backups/"*.sql.gz 2>/dev/null | head -1)
fi

if [[ -z "$LATEST_BACKUP" ]]; then
  warn "No backup found in backups/ — skipping DB restore."
  SKIP_DB=1
else
  echo "  Using: $(basename "$LATEST_BACKUP")"
  
  # Start just the DB container for restore
  info "  Starting DB container..."
  docker compose up -d db 2>&1
  echo "  Waiting for DB to be ready..."
  sleep 5
  
  # Check DB is ready
  for i in $(seq 1 10); do
    if docker compose exec -T db pg_isready -U mathai -d mathai > /dev/null 2>&1; then
      break
    fi
    printf "."
    sleep 2
  done
  echo ""
  
  # Restore
  DB_PASSWORD="${DB_PASSWORD:-mathai_secret}"
  if gunzip -c "$LATEST_BACKUP" | docker compose exec -T -e PGPASSWORD="$DB_PASSWORD" db \
    psql -U mathai -d mathai > /dev/null 2>&1; then
    success "Database restored from: $(basename "$LATEST_BACKUP")"
  else
    warn "DB restore failed — continuing without restore."
    SKIP_DB=1
  fi
  
  # Re-run Flyway by restarting DB (Flyway triggers on backend startup)
  docker compose stop db 2>&1 || true
fi
echo ""

# ── Step 3: Swap images ──────────────────────────────────────────────────────
info "Step 3/4 — Swapping to previous images..."

ROLLBACK_OK=0
if docker image inspect mathai-backend:rollback > /dev/null 2>&1 && \
   docker image inspect mathai-frontend:rollback > /dev/null 2>&1; then
  docker tag mathai-backend:rollback mathai-backend:current 2>&1
  docker tag mathai-frontend:rollback mathai-frontend:current 2>&1
  ROLLBACK_OK=1
  success "Rollback images found and tagged as current."
elif docker image inspect mathai-backend:rollback > /dev/null 2>&1; then
  docker tag mathai-backend:rollback mathai-backend:current 2>&1
  ROLLBACK_OK=1
  warn "Only backend rollback image found. Frontend will rebuild."
else
  warn "No rollback images found (mathai-backend:rollback / mathai-frontend:rollback)."
  warn "Images may have been pruned or this is the first deploy."
  warn "Will attempt to rebuild from current source code."
fi
echo ""

# ── Step 4: Restart ──────────────────────────────────────────────────────────
info "Step 4/4 — Restarting services..."

if [[ "$ROLLBACK_OK" -eq 1 ]]; then
  # Use rollback images without rebuilding
  docker compose up -d 2>&1
else
  # Rebuild from source
  docker compose up -d --build 2>&1
fi

echo ""
info "Waiting for backend to be healthy..."
for i in $(seq 1 24); do
  if curl -sf http://localhost:8080/actuator/health > /dev/null 2>&1; then
    success "Backend is healthy."
    HEALTHY=1
    break
  fi
  printf "."
  sleep 5
done
if [[ -z "$HEALTHY" ]]; then
  warn "Backend did not become healthy within 120s."
  echo "  Check logs: docker compose logs backend --tail 50"
fi

echo ""
echo "══════════════════════════════════════════════════"
if [[ "$ROLLBACK_OK" -eq 1 ]]; then
  success "Rollback complete! Previous build restored."
else
  warn "Rollback attempted with rebuild (no rollback images)."
fi
echo ""
echo "  🌐  http://localhost:8081   ← Frontend"
echo "  ⚙️   http://localhost:8080   ← Backend API"
echo "  🔐  http://localhost:8081/portal/ops-login  ← Admin"
echo ""
echo "  Verify everything works. If issues persist:"
echo "    docker compose logs --tail 100"
echo "══════════════════════════════════════════════════"
