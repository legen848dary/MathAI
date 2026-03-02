#!/usr/bin/env bash
# logs.sh — View MathAI logs on droplet
# Usage:
#   bash logs.sh             # both containers
#   bash logs.sh backend     # backend only
#   bash logs.sh frontend    # frontend only

APP_DIR="/opt/mathai"
cd "$APP_DIR"

case "${1:-all}" in
  server|backend)
    echo "=== Backend logs (Ctrl+C to stop) ==="
    docker compose logs -f backend
    ;;
  ui|frontend)
    echo "=== Frontend logs (Ctrl+C to stop) ==="
    docker compose logs -f frontend
    ;;
  *)
    echo "=== All logs (Ctrl+C to stop) ==="
    docker compose logs -f
    ;;
esac

