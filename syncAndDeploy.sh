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
# STEP 3 — SSH → docker compose build & restart
# ─────────────────────────────────────────────────────────────────────────────
info "STEP 3/3 — DB backup → rebuild & restart on droplet..."
echo "  (This takes ~2 min on code changes, ~5 min if base images changed)"
echo ""

ssh -o StrictHostKeyChecking=no -o ServerAliveInterval=10 "${DROPLET_USER}@${DROPLET_IP}" bash << 'REMOTE'
set -e
cd /opt/mathai

# ── Timestamp helper (HKT) ──────────────────────────────────────────────────
ts() { TZ='Asia/Hong_Kong' date '+%H:%M:%S'; }

# Ensure log directory exists on the host
mkdir -p /home/ubuntu/logs 2>/dev/null || mkdir -p /tmp/logs 2>/dev/null || true
export LOG_DIR=$( [ -d /home/ubuntu/logs ] && echo /home/ubuntu/logs || echo /tmp/logs )

# Check docker access
if ! docker ps >/dev/null 2>&1; then
  echo "$(ts)  ✖ ERROR: docker not accessible. Is ${USER} in the docker group?"
  echo "$(ts)    Run as root: usermod -aG docker ubuntu"
  echo "$(ts)    Then log out and back in."
  exit 1
fi

# ── Pre-upgrade DB backup ──────────────────────────────────────────────────
echo ""
echo "$(ts)  >>> Backing up database before upgrade..."
mkdir -p /opt/mathai/backups
TIMESTAMP=$(TZ='Asia/Hong_Kong' date '+%Y-%m-%d-%H%M%S')
BACKUP_FILE="/opt/mathai/backups/mathai-${TIMESTAMP}-pre-upgrade.sql"
if docker compose ps -q db > /dev/null 2>&1; then
  if docker compose exec -T db pg_dump -U mathai -d mathai --clean --if-exists > "$BACKUP_FILE" 2>/dev/null; then
    gzip -f "$BACKUP_FILE"
    echo "$(ts)  ✔ Backup saved: ${BACKUP_FILE}.gz ($(du -h ${BACKUP_FILE}.gz | cut -f1))"
  else
    echo "$(ts)  ⚠ WARNING: pg_dump failed — proceeding without backup."
  fi
else
  echo "$(ts)  ⚠ DB container not running — skipping backup."
fi
echo ""

echo ""
echo "$(ts)  >>> Saving current images for rollback..."
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
echo "$(ts)  ✔ Rollback tags saved."

echo "$(ts)  >>> docker compose down --remove-orphans"
docker compose down --remove-orphans || echo "$(ts)  (down had issues, continuing...)"

echo "$(ts)  >>> docker compose up -d --build  (this is the rebuild step)"
if ! docker compose up -d --build; then
  echo "$(ts)  ✖ ERROR: docker compose up failed. Check the output above."
  exit 1
fi
echo "$(ts)  >>> docker compose up completed."

echo ""
echo "$(ts)  >>> Waiting for backend to be healthy..."
HEALTHY=0
for i in $(seq 1 24); do
  if docker compose ps backend 2>/dev/null | grep -q "healthy"; then
    echo "$(ts)  >>> Backend is healthy."
    HEALTHY=1
    break
  fi
  printf "."
  sleep 5
done
if [ "$HEALTHY" -eq 0 ]; then
  echo ""
  echo "$(ts)  ✖ WARNING: Backend did not become healthy within 120s."
  echo "$(ts)  Check logs: docker compose logs backend --tail 50"
fi
echo ""

if [ "$HEALTHY" -eq 1 ]; then
  echo "$(ts)  >>> Cleaning up old rollback tags..."
  docker rmi mathai-backend:rollback 2>/dev/null || true
  docker rmi mathai-frontend:rollback 2>/dev/null || true
  echo "$(ts)  ✔ Rollback tags cleaned."
else
  echo "$(ts)  ⚠ Rollback tags preserved — backend health check timed out."
  echo "$(ts)    If the deploy is broken, run: bash /opt/mathai/scripts/droplet/rollback.sh"
fi

# ── Housekeeping ────────────────────────────────────────────────────────────
echo "$(ts)  >>> Housekeeping..."
echo ""

# Rotate backups: keep last 10, delete older
BACKUP_COUNT=$(ls -1 /opt/mathai/backups/*.sql.gz 2>/dev/null | wc -l)
if [ "$BACKUP_COUNT" -gt 10 ]; then
  TO_DELETE=$((BACKUP_COUNT - 10))
  echo "$(ts)  → Rotating backups: removing $TO_DELETE old file(s)..."
  ls -1t /opt/mathai/backups/*.sql.gz | tail -n "$TO_DELETE" | xargs rm -f
  echo "$(ts)  ✔ Kept 10 most recent backups."
fi

# Prune dangling images from rebuilds
echo "$(ts)  → Pruning dangling Docker images..."
docker image prune -f 2>&1 | tail -1

# Prune build cache (keep up to 2 GB)
echo "$(ts)  → Pruning Docker build cache..."
docker builder prune -f --reserved-space=2GB 2>&1 || \
  docker builder prune -f --keep-storage=2GB 2>&1 || \
  docker builder prune -f 2>&1 | tail -1

echo ""
echo "$(ts)  >>> Container status:"
docker compose ps 2>&1
echo "$(ts)  >>> Remote script completed."
REMOTE

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

