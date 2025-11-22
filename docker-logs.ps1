# Docker 로그 확인 스크립트
# 모든 컨테이너의 로그를 실시간으로 확인합니다.

param(
    [string]$Service = "all"
)

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Kraft Docker 로그 확인" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

if ($Service -eq "all") {
    Write-Host "모든 서비스 로그 확인 중..." -ForegroundColor Yellow
    docker-compose logs -f
} elseif ($Service -eq "mariadb") {
    Write-Host "MariaDB 로그 확인 중..." -ForegroundColor Yellow
    docker logs -f kraft-mariadb
} elseif ($Service -eq "redis") {
    Write-Host "Redis 로그 확인 중..." -ForegroundColor Yellow
    docker logs -f kraft-redis
} else {
    Write-Host "사용법:" -ForegroundColor Cyan
    Write-Host "  .\docker-logs.ps1              # 모든 로그" -ForegroundColor White
    Write-Host "  .\docker-logs.ps1 -Service mariadb  # MariaDB만" -ForegroundColor White
    Write-Host "  .\docker-logs.ps1 -Service redis    # Redis만" -ForegroundColor White
}

