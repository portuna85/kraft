#!/bin/bash
# Fix Docker Snap Issue on Ubuntu 24.04
# Run on server as: sudo bash fix-docker-snap.sh

set -e

echo "=== Fixing Docker Snap Issue ==="
echo ""

# Check current docker installation
echo "Current Docker installation:"
which docker
docker --version
snap list docker 2>/dev/null || echo "Docker snap not found"

echo ""
echo "🔧 Removing snap version of Docker..."
sudo snap remove docker --purge 2>/dev/null || echo "No snap docker to remove"

echo ""
echo "📦 Installing Docker from official repository..."

# Remove any old versions
sudo apt-get remove -y docker docker-engine docker.io containerd runc 2>/dev/null || true

# Update package index
sudo apt-get update

# Install prerequisites
sudo apt-get install -y \
    ca-certificates \
    curl \
    gnupg \
    lsb-release

# Add Docker's official GPG key
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg

# Set up repository
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

# Install Docker Engine
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# Add user to docker group
sudo usermod -aG docker steve

echo ""
echo "✅ Docker installation complete!"
echo ""
echo "New Docker version:"
docker --version
docker compose version

echo ""
echo "⚠️  IMPORTANT: Logout and login again to apply docker group membership"
echo "   Or run: newgrp docker"
echo ""
echo "Then test with:"
echo "   cd /app/kraft"
echo "   docker compose -f docker-compose.prod.yml up -d mariadb redis minio"

