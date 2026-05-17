#!/usr/bin/env bash
# update.sh — Rebuild and restart MathAI on the droplet (runs via syncAndDeploy.sh)
set -e

# ── Timestamp helper (HKT) ──────────────────────────────────────────────────
ts() { TZ='Asia/Hong_Kong' date '+%H:%M:%S'; }

APP_DIR="/opt/mathai"
cd "$APP_DIR"

echo "$(ts)  ======================================"
echo "$(ts)    MathAI — Update & Redeploy"
echo "$(ts)  ======================================"
echo ""

# Check docker access
if ! docker ps >/dev/null 2>&1; then
  echo "$(ts)  ✖ ERROR: docker not accessible. Is ${USER} in the docker group?"
  echo "$(ts)    Run as root: usermod -aG docker ubuntu"
  echo "$(ts)    Then log out and back in."
  exit 1
fi

# ── Pre-upgrade DB backup ──────────────────────────────────────────────────
echo "$(ts)  >>> Backing up database before upgrade..."
mkdir -p "$APP_DIR/backups"
TIMESTAMP=$(TZ='Asia/Hong_Kong' date '+%Y-%m-%d-%H%M%S')
BACKUP_FILE="$APP_DIR/backups/mathai-${TIMESTAMP}-pre-upgrade.sql"
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

# ── Save current images for rollback ───────────────────────────────────────
echo "$(ts)  >>> Saving current images for rollback..."
CONTAINER_ID=$(docker compose ps -q backend 2>/dev/null || true)
if [ -n "$CONTAINER_ID" ]; then
  IMAGE_ID=$(docker container inspect "$CONTAINER_ID" --format='{{.Image}}' 2>/dev/null || true)
  [ -n "$IMAGE_ID" ] && docker tag "$IMAGE_ID" mathai-backend:rollback 2>/dev/null || true
fi
CONTAINER_ID=$(docker compose ps -q frontend 2>/dev/null || true)
if [ -n "$CONTAINER_ID" ]; then
  IMAGE_ID=$(docker container inspect "$CONTAINER_ID" --format='{{.Image}}' 2>/dev/null || true)
  [ -n "$IMAGE_ID" ] && docker tag "$IMAGE_ID" mathai-frontend:rollback 2>/dev/null || true
fi
echo "$(ts)  ✔ Rollback tags saved."

# ── Rebuild & restart ──────────────────────────────────────────────────────
echo "$(ts)  >>> docker compose down --remove-orphans"
docker compose down --remove-orphans || echo "$(ts)  (down had issues, continuing...)"

echo "$(ts)  >>> docker compose up -d --build  (this is the rebuild step)"
if ! docker compose up -d --build; then
  echo "$(ts)  ✖ ERROR: docker compose up failed. Check the output above."
  exit 1
fi
echo "$(ts)  >>> docker compose up completed."

# ── Health check ───────────────────────────────────────────────────────────
echo ""
echo "$(ts)  >>> Waiting for backend to be healthy (up to 120s)..."
HEALTHY=0
for i in $(seq 1 24); do
  if docker compose ps backend 2>/dev/null | grep -q "healthy"; then
    echo "$(ts)  ✔ Backend is healthy."
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

# ── Cleanup rollback tags if healthy ───────────────────────────────────────
if [ "$HEALTHY" -eq 1 ]; then
  echo "$(ts)  >>> Cleaning up old rollback tags..."
  docker rmi mathai-backend:rollback 2>/dev/null || true
  docker rmi mathai-frontend:rollback 2>/dev/null || true
  echo "$(ts)  ✔ Rollback tags cleaned."
else
  echo "$(ts)  ⚠ Rollback tags preserved — backend health check timed out."
  echo "$(ts)    If the deploy is broken, run: bash $APP_DIR/scripts/droplet/rollback.sh"
fi

# ── Housekeeping ───────────────────────────────────────────────────────────
echo "$(ts)  >>> Housekeeping..."
echo ""

# Rotate backups: keep last 10
BACKUP_COUNT=$(ls -1 "$APP_DIR/backups"/*.sql.gz 2>/dev/null | wc -l)
if [ "$BACKUP_COUNT" -gt 10 ]; then
  TO_DELETE=$((BACKUP_COUNT - 10))
  echo "$(ts)  → Rotating backups: removing $TO_DELETE old file(s)..."
  ls -1t "$APP_DIR/backups"/*.sql.gz | tail -n "$TO_DELETE" | xargs rm -f
  echo "$(ts)  ✔ Kept 10 most recent backups."
fi

# Prune dangling images
echo "$(ts)  → Pruning dangling Docker images..."
docker image prune -f 2>&1 | tail -1

# Prune build cache (keep up to 2 GB)
echo "$(ts)  → Pruning Docker build cache..."
docker builder prune -f --reserved-space=2GB 2>&1 || \
  docker builder prune -f --keep-storage=2GB 2>&1 || \
  docker builder prune -f 2>&1 | tail -1

# ── Final status ───────────────────────────────────────────────────────────
echo ""
echo "$(ts)  >>> Container status:"
docker compose ps 2>&1
echo ""
echo "$(ts)  ✔ Update complete."
