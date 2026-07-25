#Requires -Version 5.1
<#
.SYNOPSIS
    Spring Boot 백엔드를 로컬에서 실행합니다.
.DESCRIPTION
    기본값: H2 인메모리 DB (Docker 불필요).
    -MariaDB 스위치를 주면 로컬 MariaDB에 연결합니다 (docker compose up -d mariadb로 미리 기동해 두세요).
.PARAMETER MariaDB
    MariaDB 모드로 실행합니다. .env.local 의 MariaDB 주석을 활성화해야 합니다.
.EXAMPLE
    .\scripts\dev-backend.ps1
    .\scripts\dev-backend.ps1 -MariaDB
#>
param(
    [switch]$MariaDB
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$root = Split-Path $PSScriptRoot -Parent

# .env.local 없으면 예시 파일에서 복사
$envLocal = Join-Path $root '.env.local'
$envExample = Join-Path $root '.env.local.example'
if (-not (Test-Path $envLocal)) {
    Write-Host ".env.local 이 없습니다. .env.local.example 에서 복사합니다..."
    Copy-Item $envExample $envLocal
    Write-Host "  -> $envLocal 생성 완료. 필요한 값을 채운 뒤 다시 실행하세요."
    if ($MariaDB) {
        Write-Host "  MariaDB 모드: .env.local 안의 MariaDB 섹션 주석을 해제하세요."
    }
    exit 0
}

if ($MariaDB) {
    Write-Host "[local-backend] MariaDB 모드로 시작합니다 (localhost:3306)"
} else {
    Write-Host "[local-backend] H2 인메모리 DB 모드로 시작합니다"
    Write-Host "  H2 콘솔: http://localhost:8080/h2-console"
}
Write-Host "  백엔드:  http://localhost:8080"
Write-Host ""

Set-Location $root
& ".\gradlew" bootRun
