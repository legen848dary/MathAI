#!/usr/bin/env zsh
# ─────────────────────────────────────────────────────────────
#  start-server.sh  —  Start MathAI Spring Boot backend (local)
# ─────────────────────────────────────────────────────────────
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
PID_FILE="$SCRIPT_DIR/.server.pid"
LOG_DIR="$SCRIPT_DIR/logs"
LOG_FILE="$LOG_DIR/mathai.log"

mkdir -p "$LOG_DIR"

# ── Check GEMINI_API_KEY ─────────────────────────────────────
if [[ -z "$GEMINI_API_KEY" ]]; then
  # Try loading from .env file
  if [[ -f "$SCRIPT_DIR/.env" ]]; then
    export $(grep -v '^#' "$SCRIPT_DIR/.env" | xargs)
    echo "✅  Loaded GEMINI_API_KEY from .env"
  else
    echo "❌  ERROR: GEMINI_API_KEY is not set."
    echo "    Either:"
    echo "      export GEMINI_API_KEY=your_key_here"
    echo "    Or create a .env file in the project root:"
    echo "      echo 'GEMINI_API_KEY=your_key_here' > $SCRIPT_DIR/.env"
    exit 1
  fi
fi

# ── Check if already running ─────────────────────────────────
if [[ -f "$PID_FILE" ]]; then
  EXISTING_PID=$(cat "$PID_FILE")
  if kill -0 "$EXISTING_PID" 2>/dev/null; then
    echo "⚠️   Backend is already running (PID $EXISTING_PID)"
    echo "    Run stop-server.sh first if you want to restart."
    exit 0
  else
    rm -f "$PID_FILE"
  fi
fi

# ── Kill anything on port 8080 ───────────────────────────────
PORT_PID=$(lsof -ti :8080 2>/dev/null || true)
if [[ -n "$PORT_PID" ]]; then
  echo "⚠️   Port 8080 in use by PID $PORT_PID — killing it..."
  kill -9 "$PORT_PID" 2>/dev/null || true
  sleep 1
fi

# ── Start backend ────────────────────────────────────────────
echo "🚀  Starting MathAI backend..."
echo "    Logs → $LOG_FILE"

cd "$SCRIPT_DIR"
nohup ./gradlew bootRun > "$LOG_FILE" 2>&1 &
echo $! > "$PID_FILE"

echo "    PID  → $(cat $PID_FILE)"
echo ""
echo "⏳  Waiting for startup..."

# Wait up to 60s for port 8080 to open
for i in {1..60}; do
  if lsof -ti :8080 &>/dev/null; then
    echo "✅  Backend is UP → http://localhost:8080"
    echo ""
    echo "    To view logs:  tail -f $LOG_FILE"
    echo "    To stop:       ./scripts/local/stop-server.sh"
    exit 0
  fi
  sleep 1
done

echo "❌  Backend failed to start within 60s. Check logs:"
echo "    tail -50 $LOG_FILE"
exit 1

