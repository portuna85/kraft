#!/bin/bash
# Database Connection Diagnostic Script
# Usage on server: bash diagnose-db.sh

echo "=== Database Connection Diagnostics ==="
echo ""

cd /app/kraft

# 1. Check all container status
echo "1️⃣ Checking all containers..."
docker compose -f docker-compose.prod.yml ps
echo ""

# 2. Check MariaDB specifically
echo "2️⃣ MariaDB container status:"
docker ps -a --filter name=kraft-mariadb --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
echo ""

# 3. Check if MariaDB is healthy
echo "3️⃣ MariaDB health check:"
MARIADB_HEALTH=$(docker inspect kraft-mariadb-prod --format='{{.State.Health.Status}}' 2>/dev/null || echo "not_found")
echo "   Status: $MARIADB_HEALTH"
if [ "$MARIADB_HEALTH" != "healthy" ]; then
    echo "   ⚠️  MariaDB is not healthy yet!"
    echo "   Recent logs:"
    docker logs kraft-mariadb-prod --tail 30
fi
echo ""

# 4. Check Docker network
echo "4️⃣ Docker network inspection:"
docker network ls | grep kraft
NETWORK_NAME=$(docker inspect kraft-mariadb-prod --format='{{range .NetworkSettings.Networks}}{{.NetworkID}}{{end}}' 2>/dev/null || echo "")
if [ -n "$NETWORK_NAME" ]; then
    echo "   Network details:"
    docker network inspect kraft_network 2>/dev/null | grep -A 5 "kraft-mariadb\|kraft-app" || echo "   Cannot inspect network"
fi
echo ""

# 5. Check if app can resolve mariadb hostname
echo "5️⃣ Testing hostname resolution from app container:"
if docker ps --filter name=kraft-app-a --format '{{.Names}}' | grep -q kraft-app-a; then
    echo "   Trying to ping mariadb from app_a..."
    docker exec kraft-app-a ping -c 2 kraft-mariadb-prod 2>&1 || echo "   Cannot ping (expected if app crashed)"

    echo "   Checking DNS resolution..."
    docker exec kraft-app-a nslookup mariadb 2>&1 || echo "   nslookup not available"

    echo "   Checking network connectivity..."
    docker exec kraft-app-a nc -zv kraft-mariadb-prod 3306 2>&1 || echo "   netcat not available or connection failed"
else
    echo "   ⚠️  app_a container not running - cannot test"
fi
echo ""

# 6. Check .env configuration
echo "6️⃣ Database configuration in .env:"
if [ -f .env ]; then
    echo "   MARIADB settings:"
    grep -E "MARIADB|SPRING_DATASOURCE" .env | grep -v PASSWORD | grep -v SECRET || echo "   No config found"
    echo "   (Passwords hidden)"
else
    echo "   ⚠️  .env file not found!"
fi
echo ""

# 7. Test direct connection to MariaDB
echo "7️⃣ Testing direct MariaDB connection:"
MARIADB_PASS=$(grep MARIADB_ROOT_PASSWORD .env 2>/dev/null | cut -d= -f2)
if [ -n "$MARIADB_PASS" ]; then
    docker exec kraft-mariadb-prod mariadb -uroot -p"$MARIADB_PASS" -e "SELECT 'Connection OK' as status;" 2>&1 || echo "   Connection test failed"
else
    echo "   Cannot find MARIADB_ROOT_PASSWORD in .env"
fi
echo ""

# 8. Check depends_on configuration
echo "8️⃣ Checking service dependencies:"
echo "   App containers should wait for MariaDB to be healthy"
docker compose -f docker-compose.prod.yml config --services
echo ""

echo "=== Diagnostic Summary ==="
echo ""
echo "Common Issues & Solutions:"
echo ""
echo "Issue 1: MariaDB not running or not healthy"
echo "  → Solution: docker compose -f docker-compose.prod.yml up -d mariadb"
echo "  → Wait 1-2 minutes for health check to pass"
echo ""
echo "Issue 2: Containers not on same network"
echo "  → Solution: docker compose -f docker-compose.prod.yml down"
echo "             docker compose -f docker-compose.prod.yml up -d mariadb redis minio"
echo ""
echo "Issue 3: Wrong hostname in SPRING_DATASOURCE_URL"
echo "  → Should be: jdbc:mariadb://mariadb:3306/..."
echo "  → Check docker-compose.prod.yml environment variables"
echo ""
echo "Issue 4: App started before MariaDB was healthy"
echo "  → Solution: docker compose -f docker-compose.prod.yml restart app_a"
echo ""

echo "📝 Recommended fix sequence:"
echo "1. Stop app: docker compose -f docker-compose.prod.yml stop app_a app_b"
echo "2. Check MariaDB: docker compose -f docker-compose.prod.yml ps mariadb"
echo "3. Wait for healthy: watch 'docker compose -f docker-compose.prod.yml ps'"
echo "4. Start app: docker compose -f docker-compose.prod.yml up -d app_a"
echo ""

