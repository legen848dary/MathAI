#!/usr/bin/env bash
# logs.sh — View MathAI logs on droplet
# Usage:
#   bash logs.sh               # docker compose logs for all containers
#   bash logs.sh backend       # backend docker logs
#   bash logs.sh frontend      # frontend docker logs
#   bash logs.sh file          # tail all log files in /root/logs
#   bash logs.sh file backend  # tail backend log file only
#   bash logs.sh file frontend # tail frontend (nginx) log files only

APP_DIR="/opt/mathai"
LOG_DIR="/root/logs"
cd "$APP_DIR"

case "${1:-all}" in
  server|backend)
    echo "=== Backend container logs (Ctrl+C to stop) ==="
    docker compose logs -f backend
    ;;
  ui|frontend)
    echo "=== Frontend container logs (Ctrl+C to stop) ==="
    docker compose logs -f frontend
    ;;
  file)
    case "${2:-all}" in
      server|backend)
        echo "=== Backend log file: ${LOG_DIR}/mathai.log (Ctrl+C to stop) ==="
        tail -f "${LOG_DIR}/mathai.log"
        ;;
      ui|frontend)
        echo "=== Frontend (nginx) log files (Ctrl+C to stop) ==="
        tail -f "${LOG_DIR}/nginx-access.log" "${LOG_DIR}/nginx-error.log"
        ;;
      *)
        echo "=== All log files in ${LOG_DIR} (Ctrl+C to stop) ==="
        tail -f "${LOG_DIR}/mathai.log" "${LOG_DIR}/nginx-access.log" "${LOG_DIR}/nginx-error.log"
        ;;
    esac
    ;;
  *)
    echo "=== All container logs (Ctrl+C to stop) ==="
    docker compose logs -f
    ;;
esac

