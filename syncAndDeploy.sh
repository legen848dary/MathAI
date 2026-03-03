#!/usr/bin/env zsh
# ──────────────────────────────────────────────────────────────────────────────
#  syncAndDeploy.sh  —  Commit, push, sync and deploy MathAI to DigitalOcean
#
#  Usage:
#    ./syncAndDeploy.sh                        # auto commit message
#    ./syncAndDeploy.sh "my commit message"    # custom commit message
# ──────────────────────────────────────────────────────────────────────────────

set -e

DROPLET_IP="129.212.238.124"
DROPLET_USER="root"
REMOTE_DIR="/opt/mathai"
SCRIPT_DIR="$(cd "$(dirname "${0:A}")" && pwd)"   # project root (absolute, symlink-safe)

# ── Colours ──────────────────────────────────────────────────────────────────
GREEN="\033[0;32m"; YELLOW="\033[1;33m"; RED="\033[0;31m"; CYAN="\033[0;36m"; NC="\033[0m"
info()    { echo "${CYAN}▶ $*${NC}"; }
success() { echo "${GREEN}✔ $*${NC}"; }
warn()    { echo "${YELLOW}⚠ $*${NC}"; }
error()   { echo "${RED}✖ $*${NC}"; exit 1; }

echo ""
echo "══════════════════════════════════════════════════"
echo "   MathAI — Sync & Deploy to ${DROPLET_IP}"
echo "══════════════════════════════════════════════════"
echo ""

cd "$SCRIPT_DIR"

# ─────────────────────────────────────────────────────────────────────────────
# STEP 1 — Git commit & push (skip if nothing to commit)
# ─────────────────────────────────────────────────────────────────────────────
info "STEP 1/3 — Git commit & push"

# Stage all changes
git add -A

# Check if there is anything to commit
if git diff --cached --quiet; then
  warn "  Nothing new to commit — working tree is clean."
else
  COMMIT_MSG="${1:-"Deploy: $(date '+%Y-%m-%d %H:%M:%S')"}"
  git commit -m "$COMMIT_MSG"
  success "  Committed: $COMMIT_MSG"
fi

# Push
echo "  Pushing to origin/main..."
if git push origin main 2>&1; then
  success "  Pushed to GitHub."
else
  warn "  Git push failed — check SSH key: ssh -T git@github.com"
  warn "  Continuing with rsync of local files..."
fi
success "  Git step done."
echo ""

# ─────────────────────────────────────────────────────────────────────────────
# STEP 2 — rsync local code to droplet
# ─────────────────────────────────────────────────────────────────────────────
info "STEP 2/3 — rsync to ${DROPLET_USER}@${DROPLET_IP}:${REMOTE_DIR}"

rsync -avz --progress \
  --exclude '.git' \
  --exclude '.gradle' \
  --exclude '.idea' \
  --exclude 'build' \
  --exclude 'frontend/node_modules' \
  --exclude 'frontend/dist' \
  --exclude 'logs' \
  --exclude '.env' \
  --exclude '*.log' \
  --exclude 'gitsetup.py' \
  --exclude 'write_scripts.py' \
  -e "ssh -o StrictHostKeyChecking=no" \
  "${SCRIPT_DIR}/" \
  "${DROPLET_USER}@${DROPLET_IP}:${REMOTE_DIR}/"

success "  rsync done."
echo ""

# ─────────────────────────────────────────────────────────────────────────────
# STEP 3 — SSH → docker compose build & restart
# ─────────────────────────────────────────────────────────────────────────────
info "STEP 3/3 — Building & restarting containers on droplet..."
echo "  (This takes ~2 min on code changes, ~5 min if base images changed)"
echo ""

ssh -o StrictHostKeyChecking=no "${DROPLET_USER}@${DROPLET_IP}" bash << 'REMOTE'
set -e
cd /opt/mathai

# Ensure log directory exists on the host
mkdir -p /root/logs

echo "  >>> docker compose down --remove-orphans"
docker compose down --remove-orphans

echo "  >>> docker compose up -d --build"
docker compose up -d --build

echo ""
echo "  >>> Waiting for backend to be healthy..."
for i in $(seq 1 24); do
  if docker compose ps backend | grep -q "healthy"; then
    echo "  >>> Backend is healthy."
    break
  fi
  printf "."
  sleep 5
done
echo ""

echo "  >>> Container status:"
docker compose ps
REMOTE

echo ""
echo "══════════════════════════════════════════════════"
success "  Deployment complete!"
echo ""
echo "  🌐  https://mathai.insoftu.com"
echo ""
echo "  Useful commands on the droplet:"
echo "    ssh ${DROPLET_USER}@${DROPLET_IP}"
echo "    bash ${REMOTE_DIR}/scripts/droplet/logs.sh"
echo "    bash ${REMOTE_DIR}/scripts/droplet/restart.sh"
echo "══════════════════════════════════════════════════"
echo ""

