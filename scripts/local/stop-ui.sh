#!/usr/bin/env zsh
# stop-ui.sh — Stop MathAI React frontend dev server (local)

SCRIPT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
PID_FILE="$SCRIPT_DIR/.ui.pid"

if [[ -f "$PID_FILE" ]]; then
  PID=$(cat "$PID_FILE")
  if kill -0 "$PID" 2>/dev/null; then
    echo "Stopping frontend (PID $PID)..."
    kill "$PID" 2>/dev/null
    sleep 1
    if kill -0 "$PID" 2>/dev/null; then
      kill -9 "$PID" 2>/dev/null
    fi
    echo "Frontend stopped."
  else
    echo "PID $PID is not running."
  fi
  rm -f "$PID_FILE"
else
  echo "No PID file found — checking port 5173..."
fi

PORT_PID=$(lsof -ti :5173 2>/dev/null)
if [[ -n "$PORT_PID" ]]; then
  echo "Killing process on port 5173 (PID $PORT_PID)..."
  kill -9 "$PORT_PID" 2>/dev/null
  echo "Port 5173 is now free."
else
  echo "Port 5173 is already free."
fi

