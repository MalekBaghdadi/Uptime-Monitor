#!/usr/bin/env bash
# setup.sh - prepares a fresh Ubuntu 24.04 server and starts the uptime monitor.
# Safe to run more than once. Run it as a normal user with sudo, not as root.

set -euo pipefail

SWAP_FILE="/swapfile"
SWAP_SIZE="2G"
REPO_URL="https://github.com/MalekBaghdadi/Uptime-Monitor.git"
REPO_DIR="$HOME/uptime-monitor"

log() {
  echo "==> $1"
}

# apt-get that never asks questions and waits if another apt is running.
# NEEDRESTART_MODE=a restarts services by itself instead of showing a menu.
apt_get() {
  sudo NEEDRESTART_MODE=a DEBIAN_FRONTEND=noninteractive apt-get -o DPkg::Lock::Timeout=120 "$@"
}

if [ "$(id -u)" -eq 0 ]; then
  echo "Run this as a normal user with sudo, not as root." >&2
  exit 1
fi

current_user="$(id -un)"

log "Updating package lists"
apt_get update

log "Installing package updates"
apt_get -y -o Dpkg::Options::=--force-confold upgrade

# ---- Swap file ----
if [ -f "$SWAP_FILE" ]; then
  log "Swap file already exists, skipping creation"
else
  log "Creating a ${SWAP_SIZE} swap file"
  sudo fallocate -l "$SWAP_SIZE" "$SWAP_FILE"
  sudo chmod 600 "$SWAP_FILE"
  sudo mkswap "$SWAP_FILE"
fi

active_swap="$(swapon --show=NAME --noheadings)"
if grep -qx "$SWAP_FILE" <<< "$active_swap"; then
  log "Swap is already on"
else
  log "Turning swap on"
  sudo swapon "$SWAP_FILE"
fi

if grep -q "^${SWAP_FILE} " /etc/fstab; then
  log "Swap is already in /etc/fstab"
else
  log "Making swap survive reboots"
  echo "${SWAP_FILE} none swap sw 0 0" | sudo tee -a /etc/fstab > /dev/null
fi

# ---- Docker ----
if command -v docker > /dev/null; then
  log "Docker is already installed, skipping"
else
  log "Installing Docker from Docker's official apt repository"
  apt_get install -y ca-certificates curl
  sudo install -m 0755 -d /etc/apt/keyrings
  sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
  sudo chmod a+r /etc/apt/keyrings/docker.asc

  sudo tee /etc/apt/sources.list.d/docker.sources > /dev/null <<EOF
Types: deb
URIs: https://download.docker.com/linux/ubuntu
Suites: $(. /etc/os-release && echo "${UBUNTU_CODENAME:-$VERSION_CODENAME}")
Components: stable
Architectures: $(dpkg --print-architecture)
Signed-By: /etc/apt/keyrings/docker.asc
EOF

  apt_get update
  apt_get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
fi

sudo systemctl enable --now docker

# ---- Let your user run docker without sudo ----
user_groups="$(id -nG "$current_user")"
if grep -qw docker <<< "$user_groups"; then
  log "$current_user is already in the docker group"
else
  log "Adding $current_user to the docker group (takes effect at next login)"
  sudo usermod -aG docker "$current_user"
fi

# ---- Get the code ----
apt_get install -y git

if [ -d "$REPO_DIR/.git" ]; then
  log "Repo already cloned, pulling the latest changes"
  git -C "$REPO_DIR" pull --ff-only
else
  log "Cloning the repo"
  git clone "$REPO_URL" "$REPO_DIR"
fi

# ---- Secrets file (it is not in git) ----
if [ -f "$REPO_DIR/.env" ]; then
  log ".env already exists, keeping it"
else
  log "Creating .env with a random database password"
  db_password="$(openssl rand -hex 24)"
  # Write to a temp file, then move it: .env is either complete or missing.
  sed "s/^POSTGRES_PASSWORD=.*/POSTGRES_PASSWORD=${db_password}/" "$REPO_DIR/.env.example" > "$REPO_DIR/.env.tmp"
  chmod 600 "$REPO_DIR/.env.tmp"
  mv "$REPO_DIR/.env.tmp" "$REPO_DIR/.env"
fi

# ---- Start the stack ----
# sudo here, because the docker group only works after you log in again.
log "Building and starting the stack (the first build is slow)"
(cd "$REPO_DIR" && sudo docker compose up -d --build)

log "Installed versions"
sudo docker --version
sudo docker compose version

if [ -f /var/run/reboot-required ]; then
  log "A reboot is needed to finish the updates. Run: sudo reboot"
fi

log "Done"