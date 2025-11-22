# Docker 빠른 재시작 스크립트 (데이터 보존)
# 컨테이너만 재시작하고 볼륨은 유지합니다.

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Kraft Docker 재시작" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 컨테이너 재시작
Write-Host "컨테이너 재시작 중..." -ForegroundColor Yellow
docker-compose restart

Write-Host ""
Write-Host "컨테이너 상태:" -ForegroundColor Cyan
docker-compose ps

Write-Host ""
Write-Host "✓ 재시작 완료!" -ForegroundColor Green

