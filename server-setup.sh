#!/bin/bash
# Ubuntu 24.04 Server Setup for Kraft Zero-Downtime Deployment
# Usage: sudo bash server-setup.sh

set -e

echo "=== Kraft Production Server Setup ==="
echo "Target: Ubuntu 24.04.3 LTS"
echo "IP: 192.168.0.9"
echo ""

# Update system
echo "📦 Updating system packages..."
apt-get update
apt-get upgrade -y

# Install essential packages
echo "📦 Installing essential packages..."
apt-get install -y \
    apt-transport-https \
    ca-certificates \
    curl \
    gnupg \
    lsb-release \
    software-properties-common \
    git \
    ufw \
    fail2ban

# Install Docker
echo "🐳 Installing Docker..."
if ! command -v docker &> /dev/null; then
    # Add Docker's official GPG key
    install -m 0755 -d /etc/apt/keyrings
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
    chmod a+r /etc/apt/keyrings/docker.gpg

    # Add repository
    echo \
      "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
      $(lsb_release -cs) stable" | tee /etc/apt/sources.list.d/docker.list > /dev/null

    apt-get update
    apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

    # Add steve to docker group
    usermod -aG docker steve

    echo "✅ Docker installed successfully"
else
    echo "✅ Docker already installed"
fi

# Verify Docker Compose
docker compose version

# Configure firewall
echo "🔥 Configuring UFW firewall..."
ufw --force enable
ufw default deny incoming
ufw default allow outgoing
ufw allow 22/tcp comment 'SSH'
ufw allow 8080/tcp comment 'Kraft App'
ufw allow 80/tcp comment 'HTTP'
ufw allow 443/tcp comment 'HTTPS'
ufw status verbose

# Create application directory structure
echo "📁 Creating application directories..."
mkdir -p /app/kraft
mkdir -p /app/kraft/docker/nginx
mkdir -p /app/kraft/docker/mariadb/init
mkdir -p /app/kraft/logs
mkdir -p /app/kraft/.env.d

chown -R steve:steve /app/kraft

# Configure Docker daemon for better performance
echo "⚙️ Configuring Docker daemon..."
cat > /etc/docker/daemon.json <<EOF
{
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "10m",
    "max-file": "3"
  },
  "storage-driver": "overlay2",
  "live-restore": true,
  "userland-proxy": false
}
EOF

systemctl restart docker

# Enable Docker to start on boot
systemctl enable docker

# Setup logrotate for application logs
echo "📋 Setting up log rotation..."
cat > /etc/logrotate.d/kraft <<EOF
/app/kraft/logs/*.log {
    daily
    rotate 30
    compress
    delaycompress
    missingok
    notifempty
    create 0644 steve steve
    sharedscripts
    postrotate
        docker kill -s USR1 kraft-nginx 2>/dev/null || true
    endscript
}
EOF

# Install monitoring tools (optional but recommended)
echo "📊 Installing monitoring tools..."
apt-get install -y htop iotop nethogs

# Create systemd service for automatic container startup
echo "🔧 Creating systemd service..."
cat > /etc/systemd/system/kraft.service <<EOF
[Unit]
Description=Kraft Application Stack
Requires=docker.service
After=docker.service

[Service]
Type=oneshot
RemainAfterExit=yes
WorkingDirectory=/app/kraft
ExecStart=/usr/bin/docker compose -f docker-compose.prod.yml up -d
ExecStop=/usr/bin/docker compose -f docker-compose.prod.yml down
User=steve
Group=steve

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
systemctl enable kraft.service

echo ""
echo "✅ Server setup completed!"
echo ""
echo "📝 Next steps:"
echo "1. Login as steve user: su - steve"
echo "2. Clone repository to /app/kraft"
echo "3. Create .env file with secrets"
echo "4. Run: docker compose -f docker-compose.prod.yml up -d"
echo ""
echo "🔐 GitHub Actions Required Secrets:"
echo "- SERVER_HOST: 192.168.0.9"
echo "- SERVER_USER: steve"
echo "- SSH_PRIVATE_KEY: (steve's private key)"
echo "- SERVER_PORT: 22"
echo ""

