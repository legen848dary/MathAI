#!/usr/bin/env zsh
# start-all.sh — Start MathAI backend + frontend together (local)

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "======================================"
echo "  MathAI — Starting all services"
echo "======================================"
echo ""

zsh "$SCRIPT_DIR/start-server.sh"
echo ""
zsh "$SCRIPT_DIR/start-ui.sh"

echo ""
echo "======================================"
echo "  All services started!"
echo "  App -> http://localhost:5173"
echo "  API -> http://localhost:8080"
echo ""
echo "  Logs:"
echo "    Backend: tail -f logs/mathai.log"
echo "    UI:      tail -f logs/ui.log"
echo "    Both:    ./scripts/local/logs.sh"
echo ""
echo "  To stop:   ./scripts/local/stop-all.sh"
echo "======================================"

