#!/usr/bin/env bash
# deploy.sh — First-time deploy OR full redeploy on DigitalOcean droplet
# Run ON the droplet as root: bash /opt/mathai/scripts/droplet/deploy.sh
set -e

APP_DIR="/opt/mathai"
REPO="https://github.com/legen848dary/MathAI.git"

echo "======================================"
echo "  MathAI — Deploy to Droplet"
echo "======================================"
echo ""

# ── Install Docker (Ubuntu 24 / Debian method) ────────────────────────────────
if ! command -v docker &>/dev/null; then
  echo ">>> Installing Docker..."
  apt-get update -qq
  apt-get install -y ca-certificates curl gnupg lsb-release git

  # Official Docker apt repo
  install -m 0755 -d /etc/apt/keyrings
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
    | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
  chmod a+r /etc/apt/keyrings/docker.gpg

  echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
    https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" \
    > /etc/apt/sources.list.d/docker.list

  apt-get update -qq
  apt-get install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin

  systemctl enable docker
  systemctl start docker
  echo ">>> Docker installed: $(docker --version)"
else
  echo ">>> Docker already installed: $(docker --version)"
fi

# ── Verify Docker Compose ─────────────────────────────────────────────────────
if ! docker compose version &>/dev/null 2>&1; then
  echo ">>> Installing Docker Compose plugin..."
  apt-get install -y docker-compose-plugin
fi
echo ">>> Docker Compose: $(docker compose version)"
echo ""

# ── Clone or pull repo ────────────────────────────────────────────────────────
if [[ -d "$APP_DIR/.git" ]]; then
  echo ">>> Pulling latest code from GitHub..."
  cd "$APP_DIR"
  git pull
else
  echo ">>> Cloning repo to $APP_DIR..."
  mkdir -p "$APP_DIR"
  git clone "$REPO" "$APP_DIR"
  cd "$APP_DIR"
fi
echo ""

# ── Ensure .env exists ────────────────────────────────────────────────────────
if [[ ! -f "$APP_DIR/.env" ]]; then
  echo ">>> No .env file found. Creating one now..."
  echo ""
  read -rp "  Enter your GEMINI_API_KEY: " GEMINI_API_KEY
  if [[ -z "$GEMINI_API_KEY" ]]; then
    echo "ERROR: GEMINI_API_KEY cannot be empty."
    exit 1
  fi
  echo "GEMINI_API_KEY=${GEMINI_API_KEY}" > "$APP_DIR/.env"
  echo "GEMINI_MODEL=gemini-2.5-flash"   >> "$APP_DIR/.env"
  echo ">>> .env created."
else
  echo ">>> .env file found."
fi
echo ""

# ── Build and start ───────────────────────────────────────────────────────────
echo ">>> Building and starting containers..."
echo "    (First build takes 3-5 minutes — subsequent deploys are faster)"
echo ""
cd "$APP_DIR"
docker compose up -d --build

# ── Wait and verify ───────────────────────────────────────────────────────────
echo ""
echo ">>> Waiting for backend to be healthy..."
for i in {1..24}; do
  if docker compose ps backend | grep -q "healthy"; then
    break
  fi
  printf "."
  sleep 5
done
echo ""

echo ""
echo "======================================"
echo "  Deployment complete!"
echo ""
IP=$(curl -s --max-time 5 ifconfig.me 2>/dev/null || hostname -I | awk '{print $1}')
echo "  App  ->  http://$IP"
echo "  API  ->  http://$IP/api/topics?grade=6"
echo ""
echo "  View logs:   bash $APP_DIR/scripts/droplet/logs.sh"
echo "  Stop:        bash $APP_DIR/scripts/droplet/stop.sh"
echo "  Update:      bash $APP_DIR/scripts/droplet/update.sh"
echo "======================================"
