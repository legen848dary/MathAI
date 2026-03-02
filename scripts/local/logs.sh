#!/usr/bin/env zsh
# logs.sh — Tail MathAI logs (local)
# Usage:
#   ./scripts/local/logs.sh           # tail both
#   ./scripts/local/logs.sh server    # backend only
#   ./scripts/local/logs.sh ui        # frontend only

SCRIPT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"

case "${1:-all}" in
  server|backend)
    echo "=== Backend log (Ctrl+C to stop) ==="
    tail -f "$SCRIPT_DIR/logs/mathai.log"
    ;;
  ui|frontend)
    echo "=== Frontend log (Ctrl+C to stop) ==="
    tail -f "$SCRIPT_DIR/logs/ui.log"
    ;;
  *)
    echo "=== Tailing all logs (Ctrl+C to stop) ==="
    tail -f "$SCRIPT_DIR/logs/mathai.log" "$SCRIPT_DIR/logs/ui.log"
    ;;
esac

