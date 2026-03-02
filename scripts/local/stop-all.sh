#!/usr/bin/env zsh
# stop-all.sh — Stop MathAI backend + frontend together (local)

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "======================================"
echo "  MathAI — Stopping all services"
echo "======================================"
echo ""

zsh "$SCRIPT_DIR/stop-ui.sh"
echo ""
zsh "$SCRIPT_DIR/stop-server.sh"

echo ""
echo "All services stopped."

