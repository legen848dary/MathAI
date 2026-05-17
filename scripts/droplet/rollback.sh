#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────────────────────────
#  rollback.sh  —  Remote rollback script for the MathAI droplet
#
#  Usage (run from local machine):
#    ssh ubuntu@<droplet-ip> bash /opt/mathai/scripts/droplet/rollback.sh
#
#  What it does:
#    1. Stops all running containers
#    2. Restores the latest pre-upgrade DB backup
#    3. Swaps Docker images back to the previous build
#    4. Restarts containers (no rebuild — uses rollback images)
# ──────────────────────────────────────────────────────────────────────────────
set -e

APP_DIR="/opt/mathai"
cd "$APP_DIR"

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

# ── Step 1: Stop containers ───────────────────────────────────────────────────
echo ">>> Step 1/4 — Stopping containers..."
docker compose down --remove-orphans 2>&1 || true
echo "✔ Containers stopped."
echo ""

# ── Step 2: Restore DB from latest pre-upgrade backup ────────────────────────
echo ">>> Step 2/4 — Restoring database..."

# Find latest pre-upgrade backup, fall back to any backup
LATEST_BACKUP=$(ls -1t "$APP_DIR/backups/"*-pre-upgrade.sql.gz 2>/dev/null | head -1)
if [[ -z "$LATEST_BACKUP" ]]; then
  LATEST_BACKUP=$(ls -1t "$APP_DIR/backups/"*.sql.gz 2>/dev/null | head -1)
fi

if [[ -z "$LATEST_BACKUP" ]]; then
  echo "⚠ WARNING: No backup found — skipping DB restore."
else
  echo "  Using: $(basename "$LATEST_BACKUP")"
  
  # Start just the DB container for restore
  echo "  Starting DB container..."
  docker compose up -d db 2>&1
  echo "  Waiting for DB to be ready..."
  sleep 5
  
  for i in $(seq 1 10); do
    if docker compose exec -T db pg_isready -U mathai -d mathai > /dev/null 2>&1; then
      break
    fi
    printf "."
    sleep 2
  done
  echo ""
  
  # Restore
  if gunzip -c "$LATEST_BACKUP" | docker compose exec -T -e PGPASSWORD="${DB_PASSWORD:-mathai_secret}" db \
    psql -U mathai -d mathai > /dev/null 2>&1; then
    echo "✔ Database restored from: $(basename "$LATEST_BACKUP")"
  else
    echo "⚠ WARNING: DB restore failed — continuing without restore."
  fi
  
  docker compose stop db 2>&1 || true
fi
echo ""

# ── Step 3: Swap images ──────────────────────────────────────────────────────
echo ">>> Step 3/4 — Swapping to previous images..."

ROLLBACK_OK=0
if docker image inspect mathai-backend:rollback > /dev/null 2>&1 && \
   docker image inspect mathai-frontend:rollback > /dev/null 2>&1; then
  docker tag mathai-backend:rollback mathai-backend:current 2>&1
  docker tag mathai-frontend:rollback mathai-frontend:current 2>&1
  ROLLBACK_OK=1
  echo "✔ Rollback images found and tagged as current."
elif docker image inspect mathai-backend:rollback > /dev/null 2>&1; then
  docker tag mathai-backend:rollback mathai-backend:current 2>&1
  ROLLBACK_OK=1
  echo "⚠ Only backend rollback image found. Frontend will rebuild."
else
  echo "⚠ WARNING: No rollback images found."
  echo "  Will attempt to rebuild from current source code."
fi
echo ""

# ── Step 4: Restart ──────────────────────────────────────────────────────────
echo ">>> Step 4/4 — Restarting services..."

if [[ "$ROLLBACK_OK" -eq 1 ]]; then
  docker compose up -d 2>&1
else
  docker compose up -d --build 2>&1
fi

echo ""
echo ">>> Waiting for backend to be healthy..."
HEALTHY=0
for i in $(seq 1 24); do
  if docker compose ps backend 2>/dev/null | grep -q "healthy"; then
    echo "✔ Backend is healthy."
    HEALTHY=1
    break
  fi
  printf "."
  sleep 5
done
if [ "$HEALTHY" -eq 0 ]; then
  echo ""
  echo "⚠ WARNING: Backend did not become healthy within 120s."
  echo "  Check logs: docker compose logs backend --tail 50"
fi

echo ""
echo "══════════════════════════════════════════════════"
if [[ "$ROLLBACK_OK" -eq 1 ]]; then
  echo "✔ Rollback complete! Previous build restored."
else
  echo "⚠ Rollback attempted with rebuild (no rollback images)."
fi
echo ""
echo "  Verify everything works. Check logs if needed:"
echo "    docker compose logs --tail 100"
echo "══════════════════════════════════════════════════"
