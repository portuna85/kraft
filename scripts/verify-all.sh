#!/usr/bin/env bash
# C-01: CI가 요구하는 전체 게이트(strict 정적분석·strict 커버리지·e2e 5트랙·컨테이너
# 하드닝·env 드리프트·shellcheck 등)를 로컬에서 한 명령으로 실행한다. 목적은 "깜빡하고
# strict 플래그나 e2e 트랙·성능 예산 없이 커밋"하는 사고를 막는 것 —
# .github/workflows/ci.yml의 각 job과 1:1로 대응시켜 둔다(잡 이름은 스크립트 섹션
# 제목에 그대로 남겨 대조하기 쉽게 했다).
#
# M-06: 예전에는 이 스크립트 전체가 web-legacy/를 검증했다 — web-legacy는 Phase 10
# 컷오버 이후 더 이상 배포되지 않는데, "verify-all이 초록"이라는 결과가 실제로는
# 배포되지 않는 프론트엔드를 검증한 것이었다. 이제 실제 배포 대상인 web/을 CI
# (web-next-* 잡들)와 동일하게 검증한다. web-legacy는 M-14(레거시 삭제)까지만
# 과도기적으로 남아 있고, CI 자체는 이미 별도 잡(api-types-drift-guard,
# web-build-artifacts 등)으로 계속 검증한다 — 이 로컬 스크립트에서는 더 이상
# 다루지 않는다.
#
# 사용법:
#   bash scripts/verify-all.sh            # 전체 게이트(느림, CI와 동일 범위)
#   bash scripts/verify-all.sh --fast      # e2e 5트랙 제외(백엔드/프론트 정적 게이트만, 빠른 루프용)
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

section "Photo Asset Metadata Guard (photo-asset-metadata-guard)"
node scripts/check-photo-asset-metadata.mjs

section "API Types Drift Guard (api-types-drift-guard)"
(
  ./gradlew bootRun --args="--spring.profiles.active=local" --console=plain > /tmp/kraft-backend-verify-api-types.log 2>&1 &
  BACKEND_PID=$!
  trap 'kill "$BACKEND_PID" >/dev/null 2>&1 || true' EXIT
  ready=0
  for _ in $(seq 1 60); do
    if curl -sf http://localhost:8080/actuator/health/liveness >/dev/null 2>&1; then
      ready=1
      break
    fi
    sleep 2
  done
  if [[ "$ready" -ne 1 ]]; then
    echo "backend did not become ready in time" >&2
    cat /tmp/kraft-backend-verify-api-types.log >&2
    exit 1
  fi
  cd web
  KRAFT_BACKEND_INTERNAL_URL=http://localhost:8080 npm run verify:api-types
)

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
  section "Web Next Build & Test — npm ci"
  npm ci
fi

section "Web Next Build & Test — format:check"
npm run format:check

section "Web Next Build & Test — lint"
npm run lint

section "Web Next Build & Test — typecheck (next typegen 이후 실행 — .next/types 필요)"
npm run typecheck

section "Web Next Build & Test — test:coverage"
npm run test:coverage

section "Web Next Build & Test — build (기본 트랙, 백엔드 없음 전제)"
KRAFT_BACKEND_INTERNAL_URL=http://127.0.0.1:59999 KRAFT_PUBLIC_BASE_URL=http://127.0.0.1:3100 npm run build

section "Web Next Build & Test — budget:bundle"
npm run budget:bundle

if [[ "$FAST" -eq 1 ]]; then
  cd "$ROOT"
  echo ""
  echo "==> --fast 지정됨: e2e 5트랙(proxy/default/a11y/visual/cross-browser) + 성능 예산 생략"
  echo "==> 전체 게이트 통과(e2e 제외)"
  exit 0
fi

section "Playwright 브라우저 설치(이미 있으면 빠르게 스킵됨)"
npx playwright install --with-deps

section "Web Next E2E — proxy 트랙(CSP·/ops 게이트)"
NEXT_DIST_DIR=.next-proxy KRAFT_BACKEND_INTERNAL_URL=http://127.0.0.1:4110 KRAFT_PUBLIC_BASE_URL=http://127.0.0.1:3110 npm run build
npm run test:e2e:proxy

section "Web Next E2E — default 트랙(주요 흐름, 픽스처 백엔드)"
NEXT_DIST_DIR=.next-default KRAFT_BACKEND_INTERNAL_URL=http://127.0.0.1:4111 KRAFT_PUBLIC_BASE_URL=http://127.0.0.1:3111 npm run build
npm run test:e2e:default

section "Web Next E2E — a11y 트랙(접근성)"
NEXT_DIST_DIR=.next-a11y KRAFT_BACKEND_INTERNAL_URL=http://127.0.0.1:4112 KRAFT_PUBLIC_BASE_URL=http://127.0.0.1:3112 npm run build
npm run test:e2e:a11y

section "Web Next E2E — visual 베이스라인 트랙"
echo "주의: 베이스라인 PNG는 CI(ubuntu-latest, Linux Chromium)에서 생성된 것과 맞춰야 한다 —"
echo "로컬(특히 Windows/macOS)에서 이 트랙을 실행하면 폰트 렌더링 차이로 오탐이 날 수 있다."
echo "베이스라인 갱신은 CI와 동일한 mcr.microsoft.com/playwright Docker 이미지 안에서 하는 것을 권장한다."
NEXT_DIST_DIR=.next-visual KRAFT_BACKEND_INTERNAL_URL=http://127.0.0.1:4113 KRAFT_PUBLIC_BASE_URL=http://127.0.0.1:3113 npm run build
npm run test:e2e:visual

section "Web Next E2E — cross-browser 트랙(Firefox·WebKit)"
NEXT_DIST_DIR=.next-cross-browser KRAFT_BACKEND_INTERNAL_URL=http://127.0.0.1:4114 KRAFT_PUBLIC_BASE_URL=http://127.0.0.1:3114 npm run build
npm run test:e2e:cross-browser

section "Web Next Performance Budget (번들 + Lighthouse)"
# run-web-performance.sh는 budget:bundle과 fixture+서버 기동만 한다 — 앱 빌드 자체는
# CI(web-next-performance-budget 잡)처럼 여기서 미리 해 둬야 한다.
KRAFT_BACKEND_INTERNAL_URL=http://127.0.0.1:4101 KRAFT_PUBLIC_BASE_URL=http://127.0.0.1:3101 npm run build
cd "$ROOT"
PORT=4101 APP_DIST_DIR=.next PERF_BASE_URL=http://127.0.0.1:3101 FIXTURE_BACKEND_URL=http://127.0.0.1:4101 PERF_POST_ID=1 \
  bash scripts/ci/run-web-performance.sh web

cd "$ROOT"
echo ""
echo "==> 전체 게이트 통과(CI와 동일 범위: strict 정적분석 + strict 커버리지 + e2e 5트랙 + 성능 예산 + 컨테이너 하드닝 + env 드리프트 + shellcheck)"
