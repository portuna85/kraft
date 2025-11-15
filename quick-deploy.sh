#!/bin/bash
# Quick Setup Script - Upload necessary files to Ubuntu server
# Usage: bash quick-deploy.sh

set -e

SERVER_IP="192.168.0.9"
SERVER_USER="steve"
SERVER_PATH="/app/kraft"

echo "=== Kraft Quick Deployment Setup ==="
echo "Target: ${SERVER_USER}@${SERVER_IP}:${SERVER_PATH}"
echo ""

# Check if required files exist locally
echo "📋 Checking local files..."
FILES_TO_UPLOAD=(
    "docker-compose.prod.yml"
    "docker/nginx/nginx.conf"
)

for file in "${FILES_TO_UPLOAD[@]}"; do
    if [ ! -f "$file" ] && [ ! -d "$(dirname $file)" ]; then
        echo "❌ Missing: $file"
        exit 1
    fi
done
echo "✅ All required files found locally"
echo ""

# Create directory structure on server
echo "📁 Creating directory structure on server..."
ssh ${SERVER_USER}@${SERVER_IP} "mkdir -p ${SERVER_PATH}/docker/nginx ${SERVER_PATH}/docker/mariadb/init ${SERVER_PATH}/logs"

# Upload docker-compose.prod.yml
echo "📤 Uploading docker-compose.prod.yml..."
scp docker-compose.prod.yml ${SERVER_USER}@${SERVER_IP}:${SERVER_PATH}/

# Upload nginx configuration
echo "📤 Uploading nginx.conf..."
scp docker/nginx/nginx.conf ${SERVER_USER}@${SERVER_IP}:${SERVER_PATH}/docker/nginx/

# Create .env template if doesn't exist
echo "📝 Creating .env template..."
ssh ${SERVER_USER}@${SERVER_IP} "cat > ${SERVER_PATH}/.env.example << 'EOF'
# GitHub Container Registry
GITHUB_REPOSITORY=your-username/kraft

# Database
MARIADB_ROOT_PASSWORD=CHANGE_THIS_STRONG_PASSWORD
MARIADB_DATABASE=kraft_db
MARIADB_USER=kraft_user
MARIADB_PASSWORD=CHANGE_THIS_DB_PASSWORD

# Redis
REDIS_PORT=6379

# MinIO
MINIO_ROOT_USER=minio_admin
MINIO_ROOT_PASSWORD=CHANGE_THIS_MINIO_PASSWORD

# OAuth (Optional)
GOOGLE_CLIENT_ID=
GOOGLE_CLIENT_SECRET=
NAVER_CLIENT_ID=
NAVER_CLIENT_SECRET=
EOF
"

# Check if .env exists, if not copy from example
ssh ${SERVER_USER}@${SERVER_IP} "cd ${SERVER_PATH} && if [ ! -f .env ]; then cp .env.example .env; echo '⚠️  Created .env from template - PLEASE EDIT IT!'; fi"

# Verify files on server
echo ""
echo "🔍 Verifying uploaded files..."
ssh ${SERVER_USER}@${SERVER_IP} "ls -lh ${SERVER_PATH}/docker-compose.prod.yml ${SERVER_PATH}/docker/nginx/nginx.conf ${SERVER_PATH}/.env 2>/dev/null || echo 'Some files missing'"

echo ""
echo "✅ Setup complete!"
echo ""
echo "⚠️  IMPORTANT: Edit .env file with your actual secrets:"
echo "   ssh ${SERVER_USER}@${SERVER_IP}"
echo "   nano ${SERVER_PATH}/.env"
echo ""
echo "📝 Then start services:"
echo "   cd ${SERVER_PATH}"
echo "   docker compose -f docker-compose.prod.yml up -d mariadb redis minio"
echo ""

