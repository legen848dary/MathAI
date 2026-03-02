#!/usr/bin/env bash
# start.sh — Start MathAI on droplet (containers already built)

APP_DIR="/opt/mathai"
cd "$APP_DIR"

echo "Starting MathAI..."
docker compose up -d

echo ""
IP=$(curl -s --max-time 3 ifconfig.me 2>/dev/null || echo "YOUR_DROPLET_IP")
echo "App is running -> http://$IP"
echo ""
echo "View logs: bash $APP_DIR/scripts/droplet/logs.sh"

