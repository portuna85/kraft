# Docker 상태 확인 스크립트
# 컨테이너, 볼륨, 네트워크 상태를 확인합니다.

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Kraft Docker 상태 확인" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 컨테이너 상태
Write-Host "📦 컨테이너 상태:" -ForegroundColor Yellow
docker-compose ps
Write-Host ""

# 헬스체크 상태
Write-Host "🏥 헬스체크 상태:" -ForegroundColor Yellow
$mariadb = docker inspect kraft-mariadb --format='{{.State.Health.Status}}' 2>$null
$redis = docker inspect kraft-redis --format='{{.State.Health.Status}}' 2>$null

if ($mariadb) {
    $mariadbColor = if ($mariadb -eq "healthy") { "Green" } else { "Red" }
    Write-Host "  MariaDB: $mariadb" -ForegroundColor $mariadbColor
} else {
    Write-Host "  MariaDB: not running" -ForegroundColor Red
}

if ($redis) {
    $redisColor = if ($redis -eq "healthy") { "Green" } else { "Red" }
    Write-Host "  Redis:   $redisColor" -ForegroundColor $redisColor
} else {
    Write-Host "  Redis:   not running" -ForegroundColor Red
}
Write-Host ""

# 볼륨 상태
Write-Host "💾 볼륨 상태:" -ForegroundColor Yellow
docker volume ls --filter name=kraft
Write-Host ""

# 네트워크 상태
Write-Host "🌐 네트워크 상태:" -ForegroundColor Yellow
docker network ls --filter name=kraft
Write-Host ""

# 리소스 사용량
Write-Host "📊 리소스 사용량:" -ForegroundColor Yellow
docker stats --no-stream --format "table {{.Container}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.NetIO}}" kraft-mariadb kraft-redis 2>$null
Write-Host ""

# 연결 테스트
Write-Host "🔌 연결 테스트:" -ForegroundColor Yellow

# MariaDB 연결 테스트
Write-Host -NoNewline "  MariaDB (3306): "
$mariadbTest = Test-NetConnection -ComputerName localhost -Port 3306 -WarningAction SilentlyContinue
if ($mariadbTest.TcpTestSucceeded) {
    Write-Host "✓ 연결 가능" -ForegroundColor Green
} else {
    Write-Host "✗ 연결 불가" -ForegroundColor Red
}

# Redis 연결 테스트
Write-Host -NoNewline "  Redis (6379):   "
$redisTest = Test-NetConnection -ComputerName localhost -Port 6379 -WarningAction SilentlyContinue
if ($redisTest.TcpTestSucceeded) {
    Write-Host "✓ 연결 가능" -ForegroundColor Green
} else {
    Write-Host "✗ 연결 불가" -ForegroundColor Red
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "상태 확인 완료" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

