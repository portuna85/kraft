#!/usr/bin/env bash
# 로컬 전용 편의 wrapper: clean clone에서 전체 스택을 렌더→빌드→기동→smoke까지 한 번에
# 검증한다. CD 워크플로우는 이 스크립트를 쓰지 않는다 — 그쪽은 render-env.sh +
# render-alertmanager.sh + pull-and-up.sh를 docker-compose.prod.yml 대상으로 직접 사용한다
# (.github/workflows/cd.yml 참고).
#
# OAuth 로컬 가이드(README "OAuth2 로컬 설정")와 동일하게 docker-compose.local.yml을 얹어
# Caddy까지 띄운다 — smoke-test.sh는 Caddy가 만드는 단일 진입점(http://localhost)을
# 전제로 하므로, Caddy가 없는 기본 docker-compose.yml 단독 구성으로는 검증할 수 없다.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$REPO_ROOT"

if [[ ! -f .env ]]; then
  echo "ERROR: .env not found. Copy .env.example to .env and fill in required values first:" >&2
  echo "  cp .env.example .env" >&2
  exit 1
fi

echo "==> [1/4] Alertmanager 설정 렌더링"
bash scripts/deploy/render-alertmanager.sh

echo "==> [2/4] 전체 스택 빌드·기동 (docker-compose.yml + docker-compose.local.yml)"
# mariadb를 뺀 나머지(backend/web/모니터링/caddy-local)는 기본 프로필에서 빠져 있다
# (docker-compose.yml 주석 참고 — IntelliJ 직접 실행 워크플로용 기본값). 이 스크립트는
# 전체 스택이 목적이므로 --profile full을 명시해야 한다.
docker compose -f docker-compose.yml -f docker-compose.local.yml --profile full up -d --build

echo "==> [3/4] 컨테이너 준비 대기"
CADDY_CONTAINER=kraft-caddy-local bash scripts/deploy/wait-readiness.sh

echo "==> [4/4] Smoke test"
KRAFT_PUBLIC_BASE_URL="${KRAFT_PUBLIC_BASE_URL:-http://localhost}" bash scripts/deploy/smoke-test.sh

echo "==> 전체 스택 기동 및 smoke test 통과"
