#!/usr/bin/env bash
# C-01: CI가 요구하는 전체 게이트(strict 정적분석·strict 커버리지·e2e 3트랙·컨테이너
# 하드닝·env 드리프트·shellcheck 등)를 로컬에서 한 명령으로 실행한다. 목적은 "깜빡하고
# strict 플래그나 content e2e·광고 오버레이 빌드 없이 커밋"하는 사고를 막는 것 —
# .github/workflows/ci.yml의 각 job과 1:1로 대응시켜 둔다(잡 이름은 스크립트 섹션
# 제목에 그대로 남겨 대조하기 쉽게 했다).
#
# 사용법:
#   bash scripts/verify-all.sh            # 전체 게이트(느림, CI와 동일 범위)
#   bash scripts/verify-all.sh --fast      # e2e 3트랙 제외(백엔드/프론트 정적 게이트만, 빠른 루프용)
#   bash scripts/verify-all.sh --skip-npm-ci  # web/node_modules가 이미 최신이면 npm ci 생략
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

# Windows Git Bash(MSYS)가 docker 인자의 절대경로를 자동으로 Windows 경로로 변환하는 것을
# 막는다 — Linux(CI)에서는 이 변수가 아무 영향도 없는 무해한 no-op이다.
export MSYS_NO_PATHCONV=1

FAST=0
SKIP_NPM_CI=0
for arg in "$@"; do
  case "$arg" in
    --fast) FAST=1 ;;
    --skip-npm-ci) SKIP_NPM_CI=1 ;;
    *) echo "unknown flag: $arg" >&2; exit 2 ;;
  esac
done

STEP=0
section() {
  STEP=$((STEP + 1))
  echo ""
  echo "==> [$STEP] $1"
}

section "Backend Build & Test + Static Analysis (backend-build-test + static-analysis)"
./gradlew check bootJar -PstrictStatic=true -PstrictCoverage=true --console=plain

section "Container Hardening Guard (container-hardening-guard, C-03)"
node scripts/check-container-hardening.mjs

section "Env Var Drift Guard (env-drift-guard, C-02)"
bash scripts/check-env-drift.sh

section "Shellcheck Guard (shellcheck-guard, C-05)"
if command -v shellcheck >/dev/null 2>&1; then
  mapfile -t ACTIVE_SCRIPTS < <(find scripts -name '*.sh' -not -path 'scripts/archive/*')
  shellcheck "${ACTIVE_SCRIPTS[@]}"
else
  echo "SKIP: shellcheck 미설치 — CI(shellcheck-guard)에서는 실행됨. 로컬 설치: https://github.com/koalaman/shellcheck#installing"
fi

section "Removed Feature Guard (removed-feature-guard)"
bash scripts/check-no-removed-features.sh

section "Caddy Validate (caddy-validate)"
if command -v docker >/dev/null 2>&1; then
  docker run --rm \
    -v "$ROOT/caddy:/etc/caddy" \
    -e KRAFT_DOMAIN=kraft.io.kr \
    -e KRAFT_ADMIN_DOMAIN=admin.kraft.io.kr \
    caddy:2-alpine@sha256:77c07d5ebfa5be9fd6c820d2094ae662c9e7eeb9bf98346b7f639900263ee2a2 \
    caddy validate --config /etc/caddy/Caddyfile
else
  echo "SKIP: docker 미설치 — Caddyfile 검증 불가"
fi

cd web
if [[ "$SKIP_NPM_CI" -eq 0 ]]; then
  section "Web Build & Test — npm ci"
  npm ci
fi

section "Web Build & Test — lint"
npm run lint

section "Web Build & Test — typecheck"
npx tsc --noEmit

section "Web Build & Test — test:coverage"
npm run test:coverage

section "Web Build & Test — build (offline, 백엔드 없음 전제)"
KRAFT_BACKEND_INTERNAL_URL=http://localhost:8080 KRAFT_PUBLIC_BASE_URL=http://localhost npm run build

if [[ "$FAST" -eq 1 ]]; then
  cd "$ROOT"
  echo ""
  echo "==> --fast 지정됨: e2e 3트랙(web-e2e / web-e2e-content / web-e2e-ad-overlay) 생략"
  echo "==> 전체 게이트 통과(e2e 제외)"
  exit 0
fi

section "Playwright 브라우저 설치(chromium, 이미 있으면 빠르게 스킵됨)"
npx playwright install --with-deps chromium

section "Web E2E — base 트랙(web-e2e, 백엔드 없음 전제)"
KRAFT_BACKEND_INTERNAL_URL=http://127.0.0.1:59999 KRAFT_PUBLIC_BASE_URL=http://127.0.0.1:3100 \
  NEXT_PUBLIC_UNDO_WINDOW_MS=200 npm run build
npm run test:e2e

section "Web E2E — content 트랙(web-e2e-content, 픽스처 백엔드)"
KRAFT_BACKEND_INTERNAL_URL=http://127.0.0.1:4101 KRAFT_PUBLIC_BASE_URL=http://127.0.0.1:3101 \
  NEXT_PUBLIC_UNDO_WINDOW_MS=200 npm run build
npm run test:e2e:content

section "Web E2E — ad-overlay 트랙(web-e2e-ad-overlay, 별도 빌드 디렉터리)"
NEXT_DIST_DIR=.next-ad-overlay NEXT_PUBLIC_KAKAO_ADFIT_UNIT_STICKY=DAN-ci-ad-overlay-test \
  KRAFT_BACKEND_INTERNAL_URL=http://127.0.0.1:59999 KRAFT_PUBLIC_BASE_URL=http://127.0.0.1:3103 npm run build
npm run test:e2e:ad-overlay

cd "$ROOT"
echo ""
echo "==> 전체 게이트 통과(CI와 동일 범위: strict 정적분석 + strict 커버리지 + e2e 3트랙 + 컨테이너 하드닝 + env 드리프트 + shellcheck)"
