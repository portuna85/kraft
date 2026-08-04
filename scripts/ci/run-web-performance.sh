#!/usr/bin/env bash
set -euo pipefail

WEB_ROOT="${1:-.}"
APP_DIST_DIR="${APP_DIST_DIR:-.next-content}"
PERF_BASE_URL="${PERF_BASE_URL:-http://127.0.0.1:3101}"
FIXTURE_BACKEND_URL="${FIXTURE_BACKEND_URL:-http://127.0.0.1:4101}"

cd "$WEB_ROOT"

## Playwright Docker 이미지는 /ms-playwright/chromium-<revision>/에 풀 Chrome을 심는데,
# <revision>은 Playwright 버전이 오를 때마다 바뀐다(예: 1228 -> 다른 숫자) — 하드코딩된
# 리비전 번호는 다음 @playwright/test 범프마다 깨진다. 실제로 설치된 경로를 글롭으로 찾는다.
if [[ -z "${CHROME_PATH:-}" ]]; then
  chrome_candidate=$(find /ms-playwright -maxdepth 2 -type f -path '*/chromium-*/chrome-linux64/chrome' 2>/dev/null | head -n 1)
  if [[ -n "$chrome_candidate" && -x "$chrome_candidate" ]]; then
    export CHROME_PATH="$chrome_candidate"
  fi
fi

npm run budget:bundle

npm run e2e:fixture > /tmp/kraft-fixture-backend.log 2>&1 &
fixture_pid=$!
export NEXT_DIST_DIR="$APP_DIST_DIR"
export KRAFT_BACKEND_INTERNAL_URL="$FIXTURE_BACKEND_URL"
export KRAFT_PUBLIC_BASE_URL="$PERF_BASE_URL"
export PORT=3101
npm run e2e:serve > /tmp/kraft-performance-web.log 2>&1 &
app_pid=$!

# shellcheck disable=SC2317,SC2329
cleanup() {
  pkill -TERM -P "$app_pid" 2>/dev/null || true
  pkill -TERM -P "$fixture_pid" 2>/dev/null || true
  kill "$app_pid" "$fixture_pid" 2>/dev/null || true
}
trap cleanup EXIT

for _ in $(seq 1 30); do
  if curl -sf "$PERF_BASE_URL" >/dev/null 2>&1; then
    PERF_BASE_URL="$PERF_BASE_URL" npm run budget:lighthouse
    exit 0
  fi
  sleep 1
done

echo "::error::Performance application did not become ready" >&2
cat /tmp/kraft-fixture-backend.log >&2 || true
cat /tmp/kraft-performance-web.log >&2 || true
exit 1
