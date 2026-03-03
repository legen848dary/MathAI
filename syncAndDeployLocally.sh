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
  export $(grep -v '^#' .env | xargs)
  info "Loaded .env"
fi

# ── Require API key ───────────────────────────────────────────────────────────
if [[ -z "$GEMINI_API_KEY" ]]; then
  error "GEMINI_API_KEY is not set. Add it to .env or export it before running."
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
docker compose down --remove-orphans 2>/dev/null || true
echo ""

info "Building & starting containers (this may take a few minutes on first run)…"
echo ""

export LOG_DIR="$SCRIPT_DIR/logs"
export GEMINI_API_KEY="$GEMINI_API_KEY"
export GEMINI_MODEL="${GEMINI_MODEL:-gemini-2.5-flash}"

docker compose build --no-cache
docker compose up -d

echo ""
info "Waiting for backend to be healthy…"
for i in $(seq 1 30); do
  if docker compose ps backend | grep -q "healthy"; then
    echo ""
    success "Backend is healthy."
    break
  fi
  printf "."
  sleep 5
done
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
echo "    ./syncAndDeploy.sh \"your commit message\""
echo "══════════════════════════════════════════════════"
echo ""

