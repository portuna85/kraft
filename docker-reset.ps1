# Docker 완전 초기화 스크립트 (PowerShell)
# 모든 컨테이너, 이미지, 볼륨, 네트워크를 삭제하고 재생성합니다.
# ⚠️ 경고: 모든 데이터가 삭제됩니다! 백업이 필요한 경우 먼저 백업하세요.

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Kraft Docker 완전 초기화 시작" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 1. 실행 중인 컨테이너 중지 및 제거
Write-Host "[1/6] 컨테이너 중지 및 제거 중..." -ForegroundColor Yellow
docker-compose down -v
docker stop kraft-mariadb kraft-redis 2>$null
docker rm -f kraft-mariadb kraft-redis 2>$null
Write-Host "✓ 컨테이너 제거 완료" -ForegroundColor Green
Write-Host ""

# 2. 볼륨 삭제
Write-Host "[2/6] 볼륨 삭제 중..." -ForegroundColor Yellow
docker volume rm kraft_mariadb_data kraft_redis_data 2>$null
docker volume rm mariadb_data redis_data 2>$null
docker volume prune -f
Write-Host "✓ 볼륨 삭제 완료" -ForegroundColor Green
Write-Host ""

# 3. 네트워크 삭제
Write-Host "[3/6] 네트워크 삭제 중..." -ForegroundColor Yellow
docker network rm kraft-network kraft_kraft-network 2>$null
docker network prune -f
Write-Host "✓ 네트워크 삭제 완료" -ForegroundColor Green
Write-Host ""

# 4. 이미지 삭제 (선택적)
Write-Host "[4/6] 이미지 삭제 중..." -ForegroundColor Yellow
docker rmi mariadb:10.11 redis:7-alpine 2>$null
Write-Host "✓ 이미지 삭제 완료" -ForegroundColor Green
Write-Host ""

# 5. 시스템 정리
Write-Host "[5/6] 시스템 정리 중..." -ForegroundColor Yellow
docker system prune -f
Write-Host "✓ 시스템 정리 완료" -ForegroundColor Green
Write-Host ""

# 6. 새로운 환경 구축
Write-Host "[6/6] 새로운 Docker 환경 구축 중..." -ForegroundColor Yellow
docker-compose up -d
Write-Host "✓ Docker 환경 구축 완료" -ForegroundColor Green
Write-Host ""

# 상태 확인
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "컨테이너 상태 확인" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
docker-compose ps

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "헬스체크 대기 중 (약 30초 소요)..." -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Start-Sleep -Seconds 30

# 최종 헬스체크
Write-Host ""
docker-compose ps

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "✓ 초기화 완료!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""
Write-Host "다음 명령어로 로그를 확인할 수 있습니다:" -ForegroundColor Cyan
Write-Host "  docker-compose logs -f" -ForegroundColor White
Write-Host ""
Write-Host "개별 컨테이너 로그:" -ForegroundColor Cyan
Write-Host "  docker logs -f kraft-mariadb" -ForegroundColor White
Write-Host "  docker logs -f kraft-redis" -ForegroundColor White

