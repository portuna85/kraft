#Requires -Version 5.1
<#
.SYNOPSIS
    Next.js 프론트엔드를 개발 모드로 실행합니다.
.DESCRIPTION
    백엔드가 localhost:8080 에서 실행 중이어야 합니다.
    web/.env.local 이 없으면 web/.env.example 에서 복사 후 로컬 기본값을 설정합니다.
.EXAMPLE
    .\scripts\dev-web.ps1
#>

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$root = Split-Path $PSScriptRoot -Parent
$webDir = Join-Path $root 'web'

# web/.env.local 없으면 예시 파일에서 복사 후 로컬 URL로 패치
$envLocal = Join-Path $webDir '.env.local'
$envExample = Join-Path $webDir '.env.example'
if (-not (Test-Path $envLocal)) {
    Write-Host "web/.env.local 이 없습니다. web/.env.example 에서 복사합니다..."
    Copy-Item $envExample $envLocal

    # 도커 내부 URL을 로컬 개발용 URL로 교체
    (Get-Content $envLocal) `
        -replace 'KRAFT_BACKEND_INTERNAL_URL=.*', 'KRAFT_BACKEND_INTERNAL_URL=http://localhost:8080' `
        -replace 'KRAFT_PUBLIC_BASE_URL=.*', 'KRAFT_PUBLIC_BASE_URL=http://localhost:3000' |
        Set-Content $envLocal -Encoding utf8

    Write-Host "  -> $envLocal 생성 완료 (KRAFT_BACKEND_INTERNAL_URL=http://localhost:8080)."
    Write-Host ""
}

# node_modules 없으면 설치
$nodeModules = Join-Path $webDir 'node_modules'
if (-not (Test-Path $nodeModules)) {
    Write-Host "[local-web] node_modules 없음 — npm ci 실행 중..."
    & npm --prefix $webDir ci
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

Write-Host "[local-web] Next.js 개발 서버를 시작합니다"
Write-Host "  프론트:  http://localhost:3000"
Write-Host "  백엔드:  http://localhost:8080 (별도 터미널에서 local-backend 스크립트 실행)"
Write-Host ""

& npm --prefix $webDir run local
