#!/usr/bin/env zsh
# start-ui.sh — Start MathAI React frontend dev server (local)

SCRIPT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
PID_FILE="$SCRIPT_DIR/.ui.pid"
LOG_DIR="$SCRIPT_DIR/logs"
LOG_FILE="$LOG_DIR/ui.log"

mkdir -p "$LOG_DIR"

if [[ -f "$PID_FILE" ]]; then
  EXISTING_PID=$(cat "$PID_FILE")
  if kill -0 "$EXISTING_PID" 2>/dev/null; then
    echo "Frontend is already running (PID $EXISTING_PID)"
    echo "Open: http://localhost:5173"
    exit 0
  else
    rm -f "$PID_FILE"
  fi
fi

PORT_PID=$(lsof -ti :5173 2>/dev/null)
if [[ -n "$PORT_PID" ]]; then
  echo "Port 5173 in use by PID $PORT_PID — killing it..."
  kill -9 "$PORT_PID" 2>/dev/null
  sleep 1
fi

echo "Starting MathAI frontend..."
echo "Logs -> $LOG_FILE"

cd "$SCRIPT_DIR/frontend"
nohup npm run dev > "$LOG_FILE" 2>&1 &
echo $! > "$PID_FILE"
echo "PID  -> $(cat $PID_FILE)"
echo ""
echo "Waiting for startup..."

for i in {1..30}; do
  if lsof -ti :5173 &>/dev/null; then
    echo "Frontend is UP -> http://localhost:5173"
    echo ""
    echo "To view logs:  tail -f $LOG_FILE"
    echo "To stop:       ./scripts/local/stop-ui.sh"
    exit 0
  fi
  sleep 1
done

echo "Frontend failed to start within 30s. Check logs:"
echo "tail -50 $LOG_FILE"
exit 1

