#!/bin/bash
# Nginx Configuration Check Script
# Usage on server: bash check-nginx.sh

echo "=== Nginx Configuration Check ==="
echo ""

SERVER_IP="192.168.0.9"
KRAFT_DIR="/app/kraft"
NGINX_CONF="${KRAFT_DIR}/docker/nginx/nginx.conf"

echo "📍 Checking Nginx configuration file..."
echo ""

# 1. Check if nginx.conf exists
if [ -f "$NGINX_CONF" ]; then
    echo "✅ Nginx config file exists: $NGINX_CONF"
    echo ""

    # Show file permissions
    echo "📋 File permissions:"
    ls -lh "$NGINX_CONF"
    echo ""

    # Show file content
    echo "📄 Nginx Configuration Content:"
    echo "════════════════════════════════════════"
    cat "$NGINX_CONF"
    echo "════════════════════════════════════════"
    echo ""

else
    echo "❌ Nginx config file NOT found: $NGINX_CONF"
    echo ""
    echo "Creating nginx.conf from template..."

    mkdir -p "${KRAFT_DIR}/docker/nginx"

    cat > "$NGINX_CONF" << 'EOF'
user  nginx;
worker_processes  auto;

error_log  /var/log/nginx/error.log warn;
pid        /var/run/nginx.pid;

events { worker_connections 1024; }

http {
    include       /etc/nginx/mime.types;
    default_type  application/octet-stream;

    log_format  main  '$remote_addr - $remote_user [$time_local] "$request" '
                      '$status $body_bytes_sent "$http_referer" '
                      '"$http_user_agent" "$http_x_forwarded_for"';

    access_log  /var/log/nginx/access.log  main;

    sendfile        on;
    keepalive_timeout  65;

    upstream kraft_backend {
        least_conn;
        server kraft-app-a:8080 max_fails=3 fail_timeout=30s;
        server kraft-app-b:8080 max_fails=3 fail_timeout=30s;
    }

    server {
        listen 8080;
        server_name _;

        location /healthz {
            proxy_pass http://kraft_backend/actuator/health;
        }

        location / {
            proxy_pass http://kraft_backend;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto $scheme;
        }

        # Websocket support (if needed later)
        map $http_upgrade $connection_upgrade { default upgrade; '' close; }
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection $connection_upgrade;
    }
}
EOF

    echo "✅ Created nginx.conf"
    echo ""
fi

# 2. Check if docker/nginx directory structure exists
echo "📁 Checking directory structure:"
ls -lah "${KRAFT_DIR}/docker/" 2>/dev/null || echo "docker/ directory not found"
echo ""

# 3. Validate nginx config syntax (if nginx container is running)
echo "🔍 Validating Nginx configuration..."
if docker ps --filter name=kraft-nginx --format '{{.Names}}' | grep -q kraft-nginx; then
    echo "Testing nginx config in running container..."
    docker exec kraft-nginx nginx -t 2>&1
    if [ $? -eq 0 ]; then
        echo "✅ Nginx configuration is valid"
    else
        echo "❌ Nginx configuration has errors"
    fi
else
    echo "⚠️  Nginx container not running - cannot validate config"
    echo "   Will validate when container starts"
fi
echo ""

# 4. Show nginx container status
echo "🐳 Nginx container status:"
docker ps -a --filter name=kraft-nginx --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}' 2>/dev/null || echo "No nginx container found"
echo ""

# 5. Show nginx logs if container exists
if docker ps -a --filter name=kraft-nginx --format '{{.Names}}' | grep -q kraft-nginx; then
    echo "📋 Recent Nginx logs (last 20 lines):"
    docker logs kraft-nginx --tail 20 2>&1 || echo "Cannot fetch logs"
    echo ""
fi

# 6. Test connectivity to backend apps
echo "🔗 Testing backend connectivity:"
for app in kraft-app-a kraft-app-b; do
    if docker ps --filter name=$app --filter status=running --format '{{.Names}}' | grep -q $app; then
        echo "  $app: Running ✅"
        docker exec $app curl -f http://localhost:8080/actuator/health -s > /dev/null 2>&1
        if [ $? -eq 0 ]; then
            echo "    └─ Health check: OK ✅"
        else
            echo "    └─ Health check: Failed ❌"
        fi
    else
        echo "  $app: Not running ⏸️"
    fi
done
echo ""

# 7. Port check
echo "🔌 Port 8080 status:"
if command -v ss &> /dev/null; then
    ss -tlnp | grep :8080 || echo "Port 8080 not listening"
elif command -v netstat &> /dev/null; then
    netstat -tlnp | grep :8080 || echo "Port 8080 not listening"
else
    lsof -i :8080 2>/dev/null || echo "Cannot check port (lsof not available)"
fi
echo ""

# 8. Test external access
echo "🌐 Testing external access:"
curl -s http://localhost:8080/healthz > /dev/null 2>&1
if [ $? -eq 0 ]; then
    echo "✅ http://localhost:8080/healthz is accessible"
    curl -s http://localhost:8080/healthz | head -5
else
    echo "❌ Cannot access http://localhost:8080/healthz"
    echo "   This is expected if app containers are not running yet"
fi
echo ""

echo "=== Check Complete ==="
echo ""
echo "📝 Next steps if issues found:"
echo "1. If nginx.conf missing: File created automatically above"
echo "2. If nginx not running: docker compose -f docker-compose.prod.yml up -d nginx"
echo "3. If apps not running: docker compose -f docker-compose.prod.yml up -d app_a app_b"
echo "4. View detailed logs: docker logs kraft-nginx -f"
echo ""

