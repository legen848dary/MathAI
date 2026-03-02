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

IP=$(curl -s --max-time 5 ifconfig.me 2>/dev/null || hostname -I | awk '{print $1}')
echo ""
echo "Update complete ->  http://$IP"
echo "View logs: bash $APP_DIR/scripts/droplet/logs.sh"
