#!/usr/bin/env bash
# restart.sh — Restart MathAI containers on droplet (no rebuild)

APP_DIR="/opt/mathai"
cd "$APP_DIR"

echo "Restarting MathAI..."
docker compose restart

IP=$(curl -s --max-time 3 ifconfig.me 2>/dev/null || echo "YOUR_DROPLET_IP")
echo "Restarted -> http://$IP"

