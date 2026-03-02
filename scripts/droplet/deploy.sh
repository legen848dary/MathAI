#!/usr/bin/env bash
# deploy.sh — First-time deploy OR full redeploy on DigitalOcean droplet
# Run ON the droplet as root: bash /opt/mathai/scripts/droplet/deploy.sh
set -e

APP_DIR="/opt/mathai"
REPO="https://github.com/legen848dary/MathAI.git"

echo "======================================"
echo "  MathAI — Deploy to Droplet"
echo "======================================"

# ── Install Docker if missing ─────────────────────────────────────────────────
if ! command -v docker &>/dev/null; then
  echo "Installing Docker..."
  apt-get update -qq
  apt-get install -y docker.io curl git
  systemctl enable docker
  systemctl start docker
  echo "Docker installed."
fi

# ── Install Docker Compose plugin if missing ─────────────────────────────────
if ! docker compose version &>/dev/null 2>&1; then
  echo "Installing Docker Compose plugin..."
  mkdir -p /usr/local/lib/docker/cli-plugins
  curl -SL https://github.com/docker/compose/releases/download/v2.27.0/docker-compose-linux-x86_64 \
    -o /usr/local/lib/docker/cli-plugins/docker-compose
  chmod +x /usr/local/lib/docker/cli-plugins/docker-compose
  echo "Docker Compose installed."
fi

# ── Clone or pull repo ───────────────────────────────────────────────────────
if [[ -d "$APP_DIR/.git" ]]; then
  echo "Pulling latest code..."
  cd "$APP_DIR"
  git pull
else
  echo "Cloning repo to $APP_DIR..."
  mkdir -p "$APP_DIR"
  git clone "$REPO" "$APP_DIR"
  cd "$APP_DIR"
fi

# ── Ensure .env exists ───────────────────────────────────────────────────────
if [[ ! -f "$APP_DIR/.env" ]]; then
  echo ""
  echo "ERROR: No .env file found at $APP_DIR/.env"
  echo ""
  echo "Create it now:"
  echo "  echo 'GEMINI_API_KEY=your_key_here' > $APP_DIR/.env"
  echo ""
  echo "Then rerun: bash $APP_DIR/scripts/droplet/deploy.sh"
  exit 1
fi

# ── Build and start ──────────────────────────────────────────────────────────
echo ""
echo "Building and starting containers (2-3 min on first run)..."
docker compose up -d --build

echo ""
echo "======================================"
echo "  Deployment complete!"
echo ""
IP=$(curl -s --max-time 3 ifconfig.me 2>/dev/null || echo "YOUR_DROPLET_IP")
echo "  App  -> http://$IP"
echo "  API  -> http://$IP/api/topics?grade=6"
echo ""
echo "  Logs:     bash $APP_DIR/scripts/droplet/logs.sh"
echo "  Stop:     bash $APP_DIR/scripts/droplet/stop.sh"
echo "  Update:   bash $APP_DIR/scripts/droplet/update.sh"
echo "======================================"

