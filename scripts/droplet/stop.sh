#!/usr/bin/env bash
# stop.sh — Stop MathAI on droplet

APP_DIR="/opt/mathai"
cd "$APP_DIR"

echo "Stopping MathAI..."
docker compose down
echo "All containers stopped."

