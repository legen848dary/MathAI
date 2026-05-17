#!/usr/bin/env bash
# update.sh — Pull latest code and rebuild on droplet (zero-downtime swap)
set -e
APP_DIR="/opt/mathai"
cd "$APP_DIR"

echo "======================================"
echo "  MathAI — Update & Redeploy"
echo "======================================"
echo ""
echo ">>> Pulling latest code..."
git pull

echo ""
echo ">>> Backing up database before update..."
mkdir -p "$APP_DIR/backups"
TIMESTAMP=$(date '+%Y-%m-%d-%H%M%S')
BACKUP_FILE="$APP_DIR/backups/mathai-${TIMESTAMP}-pre-update.sql"
if docker compose ps -q db > /dev/null 2>&1; then
  if docker compose exec -T db pg_dump -U mathai -d mathai --clean --if-exists > "$BACKUP_FILE" 2>/dev/null; then
    gzip -f "$BACKUP_FILE"
    echo "    Backup saved: ${BACKUP_FILE}.gz ($(du -h ${BACKUP_FILE}.gz | cut -f1))"
  else
    echo "    WARNING: pg_dump failed — proceeding without backup."
  fi
else
  echo "    DB container not running — skipping backup."
fi
echo ""

echo ""
echo ">>> Saving current images for rollback..."
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
echo "    Rollback tags saved."

echo ">>> Rebuilding and restarting containers..."
docker compose up -d --build

echo ""
echo ">>> Waiting for backend to be healthy..."
for i in {1..24}; do
  if docker compose ps backend | grep -q "healthy"; then
    break
  fi
  printf "."
  sleep 5
done
echo ""

# Only clean up if backend became healthy
if docker compose ps backend 2>/dev/null | grep -q "healthy"; then
  echo ">>> Cleaning up old rollback tags..."
  docker rmi mathai-backend:rollback 2>/dev/null || true
  docker rmi mathai-frontend:rollback 2>/dev/null || true
  echo "    Rollback tags cleaned."
else
  echo "    ⚠ Rollback tags preserved — health check timed out."
  echo "    If broken, run: bash $APP_DIR/scripts/droplet/rollback.sh"
fi
echo ""

IP=$(curl -s --max-time 5 ifconfig.me 2>/dev/null || hostname -I | awk '{print $1}')
echo ""
echo "Update complete ->  http://$IP"
echo "View logs: bash $APP_DIR/scripts/droplet/logs.sh"
