#!/usr/bin/env bash
set -euo pipefail

WEB_ROOT="${1:-.}"
APP_DIST_DIR="${APP_DIST_DIR:-.next-content}"
PERF_BASE_URL="${PERF_BASE_URL:-http://127.0.0.1:3101}"
FIXTURE_BACKEND_URL="${FIXTURE_BACKEND_URL:-http://127.0.0.1:4101}"

cd "$WEB_ROOT"

if [[ -z "${CHROME_PATH:-}" && -x /ms-playwright/chromium-1228/chrome-linux64/chrome ]]; then
  export CHROME_PATH=/ms-playwright/chromium-1228/chrome-linux64/chrome
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

# shellcheck disable=SC2329 # invoked indirectly by trap
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
