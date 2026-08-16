#!/usr/bin/env bash
# Post-deploy smoke test suite.
# Checks: /api/v1/stats/* returns 200;
#         key public headers are present;
#         previously removed paths are 404;
#         /admin is blocked (403) on the public domain.
set -euo pipefail

BASE="${KRAFT_PUBLIC_BASE_URL:-http://localhost}"
API="$BASE/api/v1"
FAIL=0

check_status() {
  local desc="$1" url="$2" expected="$3"
  local actual attempt
  for attempt in 1 2 3; do
    actual=$(curl -o /dev/null -sS -w "%{http_code}" --max-time 10 "$url" 2>/dev/null || echo "000")
    [[ "$actual" == "$expected" ]] && break
    [[ "$attempt" -lt 3 ]] && sleep 2
  done
  if [[ "$actual" == "$expected" ]]; then
    echo "  OK  [$actual] $desc"
  else
    echo "  FAIL[$actual != $expected] $desc ($url)" >&2
    FAIL=1
  fi
}

check_header_contains() {
  local desc="$1" url="$2" header_name="$3" expected="$4" max_attempts="${5:-3}"
  local headers value attempt
  # 컨테이너 재기동 직후 첫 요청은 콜드스타트(ISR 리빌드 등)로 일시적으로 헤더가
  # 빠질 수 있다 — check_status와 동일하게 재시도해 그 순간을 배포 실패로 오판하지 않는다.
  # 홈(/)처럼 웹 컨테이너가 백엔드로 SSR fetch를 하는 경로는 재기동 직후 그 fetch
  # 자체가 몇 차례 더 실패할 수 있어 호출부에서 더 긴 재시도 예산을 넘길 수 있다.
  for attempt in $(seq 1 "$max_attempts"); do
    headers=$(curl -sSI --max-time 10 "$url" 2>/dev/null || true)
    value=$(printf '%s\n' "$headers" | awk -F': ' -v key="$header_name" 'tolower($1) == tolower(key) {print $2}' | tr -d '\r')
    [[ -n "$value" && ( -z "$expected" || "$value" == *"$expected"* ) ]] && break
    [[ "$attempt" -lt "$max_attempts" ]] && sleep 3
  done
  if [[ -z "$value" ]]; then
    echo "  FAIL[missing header '$header_name'] $desc ($url)" >&2
    FAIL=1
  elif [[ -n "$expected" && "$value" != *"$expected"* ]]; then
    echo "  FAIL[header '$header_name' != '$expected'] $desc ($url)" >&2
    FAIL=1
  else
    echo "  OK  [header:$header_name] $desc"
  fi
}

check_oauth_redirect() {
  local provider="$1" expected_host="$2"
  local url="$BASE/oauth2/authorization/$provider"
  local headers status location actual_host attempt
  for attempt in 1 2 3; do
    headers=$(curl -sS -D - -o /dev/null --max-time 10 "$url" 2>/dev/null || true)
    status=$(printf '%s\n' "$headers" \
      | awk '$1 ~ /^HTTP\// {code=$2} END {print code}' | tr -d '\r')
    location=$(printf '%s\n' "$headers" \
      | awk -F': ' 'tolower($1) == "location" {print $2; exit}' | tr -d '\r')
    actual_host=$(printf '%s' "$location" | sed -nE 's#^https?://([^/:]+).*#\1#p')
    [[ "$status" == "302" && "$actual_host" == "$expected_host" ]] && break
    [[ "$attempt" -lt 3 ]] && sleep 2
  done
  if [[ "$status" == "302" && "$actual_host" == "$expected_host" ]]; then
    echo "  OK  [302 -> $actual_host] GET /oauth2/authorization/$provider"
  else
    # Location 전체에는 공개 client_id와 일회용 state가 있으므로 실패 로그에도 남기지 않는다.
    echo "  FAIL[status=${status:-000} redirect-host=${actual_host:-missing} expected=$expected_host] OAuth $provider" >&2
    FAIL=1
  fi
}

check_body_matches() {
  local desc="$1" url="$2" pattern="$3" max_attempts="${4:-3}"
  local body attempt
  for attempt in $(seq 1 "$max_attempts"); do
    body=$(curl -sS --max-time 10 "$url" 2>/dev/null || true)
    echo "$body" | grep -Eq "$pattern" && break
    [[ "$attempt" -lt "$max_attempts" ]] && sleep 3
  done
  if echo "$body" | grep -Eq "$pattern"; then
    echo "  OK  [body match] $desc"
  else
    echo "  FAIL[body did not match /$pattern/] $desc ($url)" >&2
    FAIL=1
  fi
}

echo "==> Smoke tests against $BASE"

# Core API: stats work even with an empty DB.
check_status "GET /api/v1/stats/frequency -> 200" "$API/stats/frequency" "200"
check_status "GET /api/v1/stats/patterns -> 200" "$API/stats/patterns" "200"
check_status "GET /api/v1/stats/companion -> 200" "$API/stats/companion" "200"
check_header_contains "GET /api/v1/stats/frequency exposes Cache-Control" "$API/stats/frequency" "Cache-Control" "max-age=60"
check_header_contains "GET /api/v1/stats/frequency exposes X-Request-Id" "$API/stats/frequency" "X-Request-Id" ""
check_header_contains "GET /api/v1/rounds/latest exposes Cache-Control" "$API/rounds/latest" "Cache-Control" "max-age=60"

# 홈이 CSP nonce 헤더를 반환하는지, 최신 회차가 실제 렌더링되는지.
# 홈은 서버 컴포넌트에서 backend로 SSR fetch(AbortSignal.timeout 5s)를 하는데, web
# 컨테이너 재기동 직후에는 그 fetch가 몇 차례 더 실패해 error.tsx(5xx, 의도된 동작 —
# page.tsx의 "실패를 숨기지 않는다" 주석 참고)로 떨어질 수 있다. 다른 체크보다 훨씬
# 긴 재시도 예산(최대 20회 × 3초 ≈ 60초)을 줘서 그 콜드스타트 구간을 배포 실패로
# 오판하지 않게 한다 — 2026-07-24 CD가 이 두 체크의 일시적 실패로 4연속 롤백됨.
check_header_contains "홈이 CSP 헤더를 반환" "$BASE/" "Content-Security-Policy" "nonce-" 20
# 홈 h1이 "이번 주 당첨결과"로 바뀌면서 제목에서 회차가 빠졌다(2026-08-02). 회차는 결과
# 카드 안의 data-testid="latest-round"가 소유하므로 그 훅을 앵커로 삼는다 — 문구나
# 클래스가 또 바뀌어도 흔들리지 않고, 실패 시 error.tsx가 아니라 정상 SSR임을 보장한다.
# Next.js 하이드레이션이 숫자와 "회" 사이에 <!-- --> 주석을 삽입할 수 있어 옵셔널로 허용.
check_body_matches "홈 최신 회차 렌더링" "$BASE/" 'data-testid="latest-round"[^>]*><strong[^>]*>[0-9]{3,4}(<!-- -->)?회' 20

# I-12: 2026-08-15 21:19~21:25경 RSC prefetch(?_rsc=... 쿼리 + RSC:1 헤더)가 여러 라우트에서
# 동시에 503을 낸 사건이 있었다(같은 URL의 전체 문서 로드는 내내 200이었음 — 계측 오류가
# 아니라 실제 prefetch 경로에서만 벌어진 문제). Lighthouse/기존 체크는 콜드 단일 페이지
# 로드만 재서 이런 종류의 회귀를 못 잡는다. Next 라우터가 실제로 보내는 두 시그널(쿼리
# 파라미터 + RSC 헤더)을 그대로 재현해 라우트별로 한 번씩 확인한다.
for rsc_path in / /recommend /analysis /saved /data /frequency /stats /companion /community /status; do
  rsc_url="${BASE}${rsc_path}?_rsc=smoketest"
  actual=$(curl -o /dev/null -sS -w "%{http_code}" --max-time 10 -H "RSC: 1" "$rsc_url" 2>/dev/null || echo "000")
  if [[ "$actual" == "200" ]]; then
    echo "  OK  [$actual] RSC prefetch $rsc_path"
  else
    echo "  FAIL[$actual != 200] RSC prefetch $rsc_path ($rsc_url)" >&2
    FAIL=1
  fi
done

# Removed paths (blueprint 17) must be 404.
check_status "GET /api/v1/push/token -> 404" "$API/push/token" "404"
check_status "GET /news -> 404" "$BASE/news" "404"

# Next.js permanent:true redirects return 308 (not 301).
check_status "GET /data-source -> 308" "$BASE/data-source" "308"

# /admin* is blocked with 403 on the public domain (Caddyfile security rule).
check_status "GET /admin -> 403 on public domain" "$BASE/admin" "403"

# 커뮤니티: 세션 조회는 익명이어도 200 + no-store(개인화 응답), 로그인 리다이렉트는
# Caddy가 Next.js가 아니라 backend로 라우팅해야 한다(빠뜨리면 오탐 없이 404로 조용히 샌다).
check_status "GET /api/v1/community/session -> 200(익명)" "$API/community/session" "200"
check_header_contains "GET /api/v1/community/session no-store" "$API/community/session" "Cache-Control" "no-store"
check_status "GET /community -> 200" "$BASE/community" "200"
# 자격 증명이 설정된 provider는 단순히 backend에 도달하는 것으로 충분하지 않다. 실제
# registration이 활성화되어 정확한 provider host로 302 되는지 확인한다. 첫 OAuth 배포 때
# 500도 통과시키던 기존 검사가 설정 오류를 놓쳤으므로 이 조건은 배포 차단 게이트다.
if [[ -n "${KRAFT_COMMUNITY_GOOGLE_CLIENT_ID:-}" && -n "${KRAFT_COMMUNITY_GOOGLE_CLIENT_SECRET:-}" ]]; then
  check_oauth_redirect "google" "accounts.google.com"
else
  echo "  SKIP[Google OAuth credentials not configured] Google authorization redirect"
fi
if [[ -n "${KRAFT_COMMUNITY_NAVER_CLIENT_ID:-}" && -n "${KRAFT_COMMUNITY_NAVER_CLIENT_SECRET:-}" ]]; then
  check_oauth_redirect "naver" "nid.naver.com"
else
  echo "  SKIP[Naver OAuth credentials not configured] Naver authorization redirect"
fi

# Admin UI must still be reachable on the admin domain.
if [[ -n "${KRAFT_ADMIN_DOMAIN:-}" ]]; then
  check_status "GET admin domain /admin/login -> 200" "https://${KRAFT_ADMIN_DOMAIN}/admin/login" "200"
fi

if [[ "${KRAFT_SMOKE_RATE_LIMIT:-false}" == "true" ]]; then
  RATE_LIMIT_URL="$API/rounds/latest"
  for _ in $(seq 1 "${KRAFT_SMOKE_RATE_LIMIT_BURST:-130}"); do
    actual=$(curl -o /dev/null -sS -w "%{http_code}" --max-time 10 "$RATE_LIMIT_URL" 2>/dev/null || echo "000")
    [[ "$actual" == "429" ]] && break
  done
  check_status "GET /api/v1/rounds/latest -> 429 after burst" "$RATE_LIMIT_URL" "429"
  check_header_contains "429 responses expose Retry-After" "$RATE_LIMIT_URL" "Retry-After" "60"
fi

# Flyway가 리포에 존재하는 최신 마이그레이션 버전까지 도달했는지 확인.
# 배포 호스트 밖(예: CI 로컬 실행)에서는 kraft-mariadb 컨테이너가 없을 수 있으므로
# 그 경우는 실패가 아니라 SKIP으로 처리한다.
if command -v docker >/dev/null 2>&1 && docker inspect kraft-mariadb >/dev/null 2>&1; then
  repo_root="$(cd "$(dirname "$0")/../.." && pwd)"
  expected_version=$(find "$repo_root/src/main/resources/db/migration" -maxdepth 1 -name 'V*.sql' \
    | sed -E 's#.*/V([0-9]+)__.*#\1#' | sort -n | tail -1)
  applied_version=$(docker exec kraft-mariadb sh -c \
    'MYSQL_PWD=$MARIADB_PASSWORD mariadb -u"$MARIADB_USER" kraft_lotto -N -e \
    "SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history WHERE success=1"' 2>/dev/null || echo "")
  # Flyway 마이그레이션은 비가역 expand 방식이라 애플리케이션 이미지 롤백 후에도 DB 버전은
  # 앞으로 나가 있을 수 있다. DB가 코드 기대 버전 이상이면 호환 가능한 정상 상태이며,
  # DB가 뒤처진 경우만 실패시킨다.
  if [[ "$expected_version" =~ ^[0-9]+$ && "$applied_version" =~ ^[0-9]+$ ]] \
      && (( applied_version >= expected_version )); then
    echo "  OK  [flyway applied=$applied_version expected-at-least=$expected_version] Flyway 호환 버전 도달"
  else
    echo "  FAIL[flyway applied=$applied_version expected-at-least=$expected_version] Flyway 호환 버전 미도달" >&2
    FAIL=1
  fi
else
  echo "  SKIP[kraft-mariadb container not found] Flyway 버전 체크 — 배포 호스트 밖에서 실행 중으로 판단"
fi

if [[ $FAIL -ne 0 ]]; then
  echo "==> Smoke test FAILED" >&2
  exit 1
fi
echo "==> All smoke tests passed"
