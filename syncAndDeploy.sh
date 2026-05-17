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
DROPLET_USER="ubuntu"
REMOTE_DIR="/opt/mathai"
SCRIPT_DIR="$(cd "$(dirname "${0:A}")" && pwd)"   # project root (absolute, symlink-safe)

# ── Colours & timestamps ─────────────────────────────────────────────────────
GREEN="\033[0;32m"; YELLOW="\033[1;33m"; RED="\033[0;31m"; CYAN="\033[0;36m"; NC="\033[0m"
ts()     { TZ='Asia/Hong_Kong' date '+%H:%M:%S'; }
info()   { echo "$(ts)  ${CYAN}▶ $*${NC}"; }
success(){ echo "$(ts)  ${GREEN}✔ $*${NC}"; }
warn()   { echo "$(ts)  ${YELLOW}⚠ $*${NC}"; }
error()  { echo "$(ts)  ${RED}✖ $*${NC}"; exit 1; }

echo ""
echo "$(ts)  ══════════════════════════════════════════════════"
echo "$(ts)     MathAI — Sync & Deploy to ${DROPLET_IP}"
echo "$(ts)  ══════════════════════════════════════════════════"
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
  warn "  (Will still rsync + rebuild below)"
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
# STEP 3 — SSH → run update script on droplet (always rebuilds)
# ─────────────────────────────────────────────────────────────────────────────
info "STEP 3/3 — Running update script on droplet (always rebuilds)..."
echo "  (This takes ~2 min on code changes, ~5 min if base images changed)"
echo ""

ssh -o StrictHostKeyChecking=no -o ServerAliveInterval=10 "${DROPLET_USER}@${DROPLET_IP}" \
  "bash ${REMOTE_DIR}/scripts/droplet/update.sh" 2>&1

echo ""
echo "$(ts)  ══════════════════════════════════════════════════"
success "  Deployment complete!"
echo ""
echo "$(ts)  🌐  https://mathai.insoftu.com"
echo "$(ts)  🔐  https://mathai.insoftu.com/portal/ops-login  ← Admin Portal"
echo ""
echo "$(ts)  Useful commands on the droplet:"
echo "$(ts)    ssh ${DROPLET_USER}@${DROPLET_IP}"
echo "$(ts)    bash ${REMOTE_DIR}/scripts/droplet/logs.sh"
echo "$(ts)    bash ${REMOTE_DIR}/scripts/droplet/restart.sh"
echo "$(ts)  ══════════════════════════════════════════════════"
echo ""

