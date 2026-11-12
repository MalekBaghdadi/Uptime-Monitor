#!/usr/bin/env bash
# setup.sh - prepares a fresh Ubuntu 24.04 server for the uptime monitor.
# Safe to run more than once. Run it as a normal user with sudo, not as root.

set -euo pipefail

SWAP_FILE="/swapfile"
SWAP_SIZE="2G"

log() {
  echo "==> $1"
}

# apt-get that never asks questions and waits if another apt is running
apt_get() {
  sudo DEBIAN_FRONTEND=noninteractive apt-get -o DPkg::Lock::Timeout=120 "$@"
}

if [ "$(id -u)" -eq 0 ]; then
  echo "Run this as your normal user (malek), not as root." >&2
  exit 1
fi

current_user="$(id -un)"

log "Updating package lists"
apt_get update

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

log "Installed versions"
sudo docker --version
sudo docker compose version

log "Done"