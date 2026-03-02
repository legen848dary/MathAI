#!/usr/bin/env zsh
# stop-server.sh — Stop MathAI Spring Boot backend (local)

SCRIPT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
PID_FILE="$SCRIPT_DIR/.server.pid"

if [[ -f "$PID_FILE" ]]; then
  PID=$(cat "$PID_FILE")
  if kill -0 "$PID" 2>/dev/null; then
    echo "Stopping backend (PID $PID)..."
    kill "$PID" 2>/dev/null
    sleep 2
    if kill -0 "$PID" 2>/dev/null; then
      kill -9 "$PID" 2>/dev/null
    fi
    echo "Backend stopped."
  else
    echo "PID $PID is not running."
  fi
  rm -f "$PID_FILE"
else
  echo "No PID file found — checking port 8080..."
fi

PORT_PID=$(lsof -ti :8080 2>/dev/null)
if [[ -n "$PORT_PID" ]]; then
  echo "Killing process on port 8080 (PID $PORT_PID)..."
  kill -9 "$PORT_PID" 2>/dev/null
  echo "Port 8080 is now free."
else
  echo "Port 8080 is already free."
fi
