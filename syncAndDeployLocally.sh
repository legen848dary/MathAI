#!/usr/bin/env zsh
# ──────────────────────────────────────────────────────────────────────────────
#  syncAndDeployLocally.sh  —  Build and run MathAI locally via Docker Compose
#
#  Usage:
#    ./syncAndDeployLocally.sh          # build & start (reads .env for API key)
#    ./syncAndDeployLocally.sh stop     # stop and remove local containers
#    ./syncAndDeployLocally.sh logs     # tail local container logs
#    ./syncAndDeployLocally.sh restart  # stop then start
# ──────────────────────────────────────────────────────────────────────────────

set -e

SCRIPT_DIR="$(cd "$(dirname "${0:A}")" && pwd)"
cd "$SCRIPT_DIR"

# ── Colours ──────────────────────────────────────────────────────────────────
GREEN="\033[0;32m"; YELLOW="\033[1;33m"; RED="\033[0;31m"; CYAN="\033[0;36m"; NC="\033[0m"
info()    { echo "${CYAN}▶ $*${NC}"; }
success() { echo "${GREEN}✔ $*${NC}"; }
warn()    { echo "${YELLOW}⚠ $*${NC}"; }
error()   { echo "${RED}✖ $*${NC}"; exit 1; }

# ── Load .env if present (for GEMINI_API_KEY etc.) ───────────────────────────
if [[ -f .env ]]; then
  while IFS= read -r line || [[ -n "$line" ]]; do
    [[ -z "$line" || "$line" == \#* ]] && continue
    export "$line"
  done < .env
  info "Loaded .env"
fi

# ── Require at least one AI API key ─────────────────────────────────────────────
if [[ -z "$GEMINI_API_KEY" ]] && [[ -z "$NOVITA_API_KEY" ]]; then
  error "At least one of GEMINI_API_KEY or NOVITA_API_KEY must be set. Add to .env or export."
fi

# ── Handle sub-commands ───────────────────────────────────────────────────────
case "${1:-start}" in

  stop)
    info "Stopping local containers…"
    export LOG_DIR="$SCRIPT_DIR/logs"
    docker compose down --remove-orphans
    success "Stopped."
    exit 0
    ;;

  logs)
    export LOG_DIR="$SCRIPT_DIR/logs"
    docker compose logs -f
    exit 0
    ;;

  restart)
    info "Restarting…"
    export LOG_DIR="$SCRIPT_DIR/logs"
    docker compose down --remove-orphans
    ;;

esac

# ── Default: build & start ────────────────────────────────────────────────────
echo ""
echo "══════════════════════════════════════════════════"
echo "   MathAI — Local Docker Build & Run"
echo "══════════════════════════════════════════════════"
echo ""

# Ensure local log dir exists (mirrors the volume mount in docker-compose.yml)
mkdir -p "$SCRIPT_DIR/logs"

info "Stopping any existing local containers…"
# ── Save current images for potential rollback ─────────────────────────────
echo ""
info "Tagging current images for rollback safety..."
CONTAINER_ID=$(docker compose ps -q backend 2>/dev/null)
if [ -n "$CONTAINER_ID" ]; then
  IMAGE_ID=$(docker container inspect "$CONTAINER_ID" --format='{{.Image}}' 2>/dev/null || true)
  [ -n "$IMAGE_ID" ] && docker tag "$IMAGE_ID" mathai-backend:rollback 2>/dev/null || true
fi
CONTAINER_ID=$(docker compose ps -q frontend 2>/dev/null)
if [ -n "$CONTAINER_ID" ]; then
  IMAGE_ID=$(docker container inspect "$CONTAINER_ID" --format='{{.Image}}' 2>/dev/null || true)
  [ -n "$IMAGE_ID" ] && docker tag "$IMAGE_ID" mathai-frontend:rollback 2>/dev/null || true
fi
success "Rollback tags saved."
# ── Pre-upgrade DB backup ──────────────────────────────────────────────────
if docker compose ps -q db > /dev/null 2>&1; then
  echo ""
  info "Backing up database before upgrade..."
  if bash "$SCRIPT_DIR/scripts/backup.sh" pre-upgrade 2>/dev/null; then
    success "Pre-upgrade backup complete."
  else
    warn "Backup failed — proceeding without backup."
  fi
fi
echo ""
docker compose down --remove-orphans 2>/dev/null || true
echo ""

info "Building & starting containers (this may take a few minutes on first run)…"
echo ""

export LOG_DIR="$SCRIPT_DIR/logs"
export GEMINI_API_KEY="${GEMINI_API_KEY:-}"
export GEMINI_MODEL="${GEMINI_MODEL:-gemini-2.5-flash}"
export NOVITA_API_KEY="${NOVITA_API_KEY:-}"
export DB_PASSWORD="${DB_PASSWORD:-mathai-db-password}"
export ADMIN_EMAIL="${ADMIN_EMAIL:-admin@mathai.local}"
export ADMIN_PASSWORD="${ADMIN_PASSWORD:-changeme}"
export JWT_SECRET="${JWT_SECRET:-mathai-jwt-secret-change-me}"

docker compose build --no-cache
docker compose up -d

echo ""
info "Waiting for backend to be healthy…"
for i in $(seq 1 30); do
  if curl -sf http://localhost:8080/actuator/health > /dev/null 2>&1; then
    echo ""
    success "Backend is healthy."
    HEALTHY=1
    break
  fi
  printf "."
  sleep 5
done
echo ""

if [[ -n "$HEALTHY" ]]; then
  info "Cleaning up old rollback tags..."
  docker rmi mathai-backend:rollback 2>/dev/null || true
  docker rmi mathai-frontend:rollback 2>/dev/null || true
  success "Rollback tags cleaned."
else
  warn "Rollback tags preserved — backend health check timed out."
  warn "If the deploy is broken, run: ./scripts/rollback.sh"
fi

# ── Housekeeping ────────────────────────────────────────────────────────────
echo ""
info "Housekeeping..."

# Rotate backups: keep last 10
BACKUP_COUNT=$(ls -1 "$SCRIPT_DIR/backups"/*.sql.gz 2>/dev/null | wc -l)
if [ "$BACKUP_COUNT" -gt 10 ]; then
  TO_DELETE=$((BACKUP_COUNT - 10))
  echo "  → Rotating backups: removing $TO_DELETE old file(s)..."
  ls -1t "$SCRIPT_DIR/backups"/*.sql.gz | tail -n "$TO_DELETE" | xargs rm -f
  echo "  ✔ Kept 10 most recent backups."
fi

echo "  → Pruning dangling Docker images..."
docker image prune -f 2>&1 | tail -1

echo "  → Pruning Docker build cache..."
docker builder prune -f --reserved-space=2GB 2>&1 || \
  docker builder prune -f --keep-storage=2GB 2>&1 || \
  docker builder prune -f 2>&1 | tail -1
echo ""

info "Container status:"
docker compose ps

echo ""
echo "══════════════════════════════════════════════════"
success "Local deployment ready!"
echo ""
echo "  🌐  http://localhost:8081   ← Frontend"
echo "  ⚙️   http://localhost:8080   ← Backend API"
echo ""
echo "  Useful commands:"
echo "    ./syncAndDeployLocally.sh logs     # tail all logs"
echo "    ./syncAndDeployLocally.sh stop     # stop everything"
echo "    ./syncAndDeployLocally.sh restart  # restart"
echo ""
echo "  When happy, deploy to production:"
echo "    ./syncAndDeploy.sh 'your commit message'"
echo "══════════════════════════════════════════════════"
echo ""

