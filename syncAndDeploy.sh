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

ssh -o StrictHostKeyChecking=no "${DROPLET_USER}@${DROPLET_IP}" bash << 'REMOTE'
cd /opt/mathai

# Ensure log directory exists on the host
mkdir -p /home/ubuntu/logs 2>/dev/null || mkdir -p /tmp/logs 2>/dev/null || true
export LOG_DIR=$( [ -d /home/ubuntu/logs ] && echo /home/ubuntu/logs || echo /tmp/logs )

# Check docker access
if ! docker ps >/dev/null 2>&1; then
  echo "  ✖ ERROR: docker not accessible. Is ${USER} in the docker group?"
  echo "    Run as root: usermod -aG docker ubuntu"
  echo "    Then log out and back in."
  exit 1
fi

# ── Pre-upgrade DB backup ──────────────────────────────────────────────────
echo ""
echo "  >>> Backing up database before upgrade..."
mkdir -p /opt/mathai/backups
TIMESTAMP=$(date '+%Y-%m-%d-%H%M%S')
BACKUP_FILE="/opt/mathai/backups/mathai-${TIMESTAMP}-pre-upgrade.sql"
if docker compose ps -q db > /dev/null 2>&1; then
  if docker compose exec -T db pg_dump -U mathai -d mathai --clean --if-exists > "$BACKUP_FILE" 2>/dev/null; then
    gzip -f "$BACKUP_FILE"
    echo "  ✔ Backup saved: ${BACKUP_FILE}.gz ($(du -h ${BACKUP_FILE}.gz | cut -f1))"
  else
    echo "  ⚠ WARNING: pg_dump failed — proceeding without backup."
  fi
else
  echo "  ⚠ DB container not running — skipping backup."
fi
echo ""

echo ""
echo "  >>> Saving current images for rollback..."
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
echo "  ✔ Rollback tags saved."

echo "  >>> docker compose down --remove-orphans"
docker compose down --remove-orphans 2>&1 || echo "  (down had issues, continuing...)"

echo "  >>> docker compose up -d --build  (this is the rebuild step)"
if ! docker compose up -d --build 2>&1; then
  echo "  ✖ ERROR: docker compose up failed. Check the output above."
  exit 1
fi

echo ""
echo "  >>> Waiting for backend to be healthy..."
HEALTHY=0
for i in $(seq 1 24); do
  if docker compose ps backend 2>/dev/null | grep -q "healthy"; then
    echo "  >>> Backend is healthy."
    HEALTHY=1
    break
  fi
  printf "."
  sleep 5
done
if [ "$HEALTHY" -eq 0 ]; then
  echo ""
  echo "  ✖ WARNING: Backend did not become healthy within 120s."
  echo "  Check logs: docker compose logs backend --tail 50"
fi
echo ""

if [ "$HEALTHY" -eq 1 ]; then
  echo "  >>> Cleaning up old rollback tags..."
  docker rmi mathai-backend:rollback 2>/dev/null || true
  docker rmi mathai-frontend:rollback 2>/dev/null || true
  echo "  ✔ Rollback tags cleaned."
else
  echo "  ⚠ Rollback tags preserved — backend health check timed out."
  echo "    If the deploy is broken, run: bash /opt/mathai/scripts/droplet/rollback.sh"
fi

# ── Housekeeping ────────────────────────────────────────────────────────────
echo "  >>> Housekeeping..."
echo ""

# Rotate backups: keep last 10, delete older
BACKUP_COUNT=$(ls -1 /opt/mathai/backups/*.sql.gz 2>/dev/null | wc -l)
if [ "$BACKUP_COUNT" -gt 10 ]; then
  TO_DELETE=$((BACKUP_COUNT - 10))
  echo "  → Rotating backups: removing $TO_DELETE old file(s)..."
  ls -1t /opt/mathai/backups/*.sql.gz | tail -n "$TO_DELETE" | xargs rm -f
  echo "  ✔ Kept 10 most recent backups."
fi

# Prune dangling images from rebuilds
echo "  → Pruning dangling Docker images..."
docker image prune -f 2>&1 | tail -1

# Prune build cache (keep up to 2 GB)
echo "  → Pruning Docker build cache..."
docker builder prune -f --reserved-space=2GB 2>&1 || \
  docker builder prune -f --keep-storage=2GB 2>&1 || \
  docker builder prune -f 2>&1 | tail -1

echo ""
echo "  >>> Container status:"
docker compose ps 2>&1
REMOTE

echo ""
echo "══════════════════════════════════════════════════"
success "  Deployment complete!"
echo ""
echo "  🌐  https://mathai.insoftu.com"
echo "  🔐  https://mathai.insoftu.com/portal/ops-login  ← Admin Portal"
echo ""
echo "  Useful commands on the droplet:"
echo "    ssh ${DROPLET_USER}@${DROPLET_IP}"
echo "    bash ${REMOTE_DIR}/scripts/droplet/logs.sh"
echo "    bash ${REMOTE_DIR}/scripts/droplet/restart.sh"
echo "══════════════════════════════════════════════════"
echo ""

