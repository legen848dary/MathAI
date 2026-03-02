#!/usr/bin/env bash
# update.sh — Pull latest code and rebuild on droplet

set -e
APP_DIR="/opt/mathai"
cd "$APP_DIR"

echo "======================================"
echo "  MathAI — Update & Redeploy"
echo "======================================"
echo ""
echo "Pulling latest code..."
git pull

echo "Rebuilding and restarting containers..."
docker compose up -d --build

echo ""
IP=$(curl -s --max-time 3 ifconfig.me 2>/dev/null || echo "YOUR_DROPLET_IP")
echo "Update complete -> http://$IP"
echo ""
echo "View logs: bash $APP_DIR/scripts/droplet/logs.sh"

