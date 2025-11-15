#!/bin/bash
# Quick Deployment Test Script for Ubuntu Server
# Usage: bash deploy-test.sh

set -e

SERVER_IP="192.168.0.9"
SERVER_USER="steve"

echo "=== Kraft Deployment Test ==="
echo "Target: ${SERVER_USER}@${SERVER_IP}"
echo ""

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m' # No Color

check_step() {
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓${NC} $1"
    else
        echo -e "${RED}✗${NC} $1"
        exit 1
    fi
}

# 1. SSH 연결 테스트
echo "1. Testing SSH connection..."
ssh -o ConnectTimeout=5 ${SERVER_USER}@${SERVER_IP} "echo 'SSH OK'" > /dev/null 2>&1
check_step "SSH connection"

# 2. Docker 설치 확인
echo "2. Checking Docker installation..."
ssh ${SERVER_USER}@${SERVER_IP} "docker --version" > /dev/null 2>&1
check_step "Docker installed"

# 3. Docker Compose 확인
echo "3. Checking Docker Compose..."
ssh ${SERVER_USER}@${SERVER_IP} "docker compose version" > /dev/null 2>&1
check_step "Docker Compose installed"

# 4. 디렉토리 구조 확인
echo "4. Checking application directory..."
ssh ${SERVER_USER}@${SERVER_IP} "test -d /app/kraft"
check_step "/app/kraft directory exists"

# 5. 환경 파일 확인
echo "5. Checking .env file..."
ssh ${SERVER_USER}@${SERVER_IP} "test -f /app/kraft/.env"
check_step ".env file exists"

# 6. Docker 네트워크 확인
echo "6. Checking Docker network..."
ssh ${SERVER_USER}@${SERVER_IP} "docker network ls | grep kraft_network" > /dev/null 2>&1
check_step "Docker network (may not exist yet - OK)"

# 7. 실행 중인 컨테이너 확인
echo "7. Checking running containers..."
CONTAINERS=$(ssh ${SERVER_USER}@${SERVER_IP} "docker ps --filter name=kraft --format '{{.Names}}'")
if [ -z "$CONTAINERS" ]; then
    echo "   No kraft containers running (first deployment)"
else
    echo "   Running containers:"
    echo "$CONTAINERS" | sed 's/^/   - /'
fi

# 8. 포트 8080 확인
echo "8. Checking port 8080 availability..."
ssh ${SERVER_USER}@${SERVER_IP} "curl -s http://localhost:8080/actuator/health" > /dev/null 2>&1
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓${NC} Application is running and healthy"
else
    echo "   Port 8080 not responding (app not deployed yet or down)"
fi

# 9. 디스크 공간 확인
echo "9. Checking disk space..."
DISK_USAGE=$(ssh ${SERVER_USER}@${SERVER_IP} "df -h / | tail -1 | awk '{print \$5}' | sed 's/%//'")
if [ "$DISK_USAGE" -lt 80 ]; then
    echo -e "${GREEN}✓${NC} Disk usage: ${DISK_USAGE}%"
else
    echo -e "${RED}⚠${NC} Disk usage high: ${DISK_USAGE}%"
fi

# 10. 메모리 확인
echo "10. Checking memory..."
MEM_USAGE=$(ssh ${SERVER_USER}@${SERVER_IP} "free | grep Mem | awk '{printf \"%.0f\", \$3/\$2 * 100}'")
echo "    Memory usage: ${MEM_USAGE}%"

echo ""
echo "=== Test Summary ==="
echo -e "${GREEN}✓${NC} Server is ready for deployment"
echo ""
echo "Next steps:"
echo "1. Ensure GitHub Actions secrets are configured:"
echo "   - SERVER_HOST=${SERVER_IP}"
echo "   - SERVER_USER=${SERVER_USER}"
echo "   - SSH_PRIVATE_KEY=(your private key)"
echo "   - SERVER_PORT=22"
echo ""
echo "2. Push code to main branch to trigger auto-deployment"
echo "3. Monitor: https://github.com/YOUR_USERNAME/kraft/actions"
echo ""

