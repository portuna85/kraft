#!/usr/bin/env bash
# Fast local sanity check of Caddy's actual routing behavior, run on the deploy
# host right after Caddy starts — hits 127.0.0.1:443 directly instead of going
# over the internet, so a broken Caddyfile (wrong block order, wrong matcher,
# etc.) fails in ~1s instead of waiting through the full external smoke test.
set -euo pipefail

FAIL=0

check_status() {
  local desc="$1" host="$2" path="$3" expected="$4"
  # Routes that proxy through to backend/web (anything beyond a Caddy-level
  # static_response) can briefly connection-refuse/timeout ("000") right after
  # --force-recreate while the new container's network stack settles — retry
  # before declaring a real Caddyfile bug.
  local actual attempt
  for attempt in 1 2 3; do
    actual=$(curl -sk -o /dev/null -w "%{http_code}" --max-time 5 \
      --resolve "${host}:443:127.0.0.1" "https://${host}${path}" 2>/dev/null || echo "000")
    [[ "$actual" == "$expected" ]] && break
    [[ "$attempt" -lt 3 ]] && sleep 1
  done
  if [[ "$actual" == "$expected" ]]; then
    echo "  OK  [$actual] $desc"
  else
    echo "  FAIL[$actual != $expected] $desc (https://${host}${path})" >&2
    FAIL=1
  fi
}

# 이 스크립트는 "Caddy가 backend로 제대로 라우팅하는가"만 검증하는 것이지 커뮤니티
# OAuth 앱이 실제로 설정됐는지는 관심사가 아니다(provider 미설정이면
# CommunityOAuth2FallbackConfig가 앱을 정상 기동시키되 로그인 시도는 500을 반환한다,
# 2026-07-24). 그래서 "정확히 302"가 아니라 "404가 아님"(=Next.js catch-all로 새지
# 않고 backend까지는 도달함)으로 판정한다 — Caddyfile 설정 오류와 OAuth 미설정을 혼동하지 않는다.
check_status_not_404() {
  local desc="$1" host="$2" path="$3"
  local actual attempt
  for attempt in 1 2 3; do
    actual=$(curl -sk -o /dev/null -w "%{http_code}" --max-time 5 \
      --resolve "${host}:443:127.0.0.1" "https://${host}${path}" 2>/dev/null || echo "000")
    [[ "$actual" != "404" ]] && break
    [[ "$attempt" -lt 3 ]] && sleep 1
  done
  if [[ "$actual" != "404" ]]; then
    echo "  OK  [$actual] $desc"
  else
    echo "  FAIL[404] $desc (https://${host}${path}) — Next.js catch-all로 샌 것으로 보임" >&2
    FAIL=1
  fi
}

# H-05: /api/client-error 본문 크기 제한(caddy/Caddyfile의 @client_error, 4KB)이 실제로
# 걸려있는지 확인한다. 오버사이즈 요청이 413로 edge에서 끊기는지(Content-Length 있는 요청과
# chunked 요청 둘 다), 그리고 정상 크기 요청이 matcher 때문에 깨지지 않는지 함께 검증한다.
check_body_over_limit_rejected() {
  local desc="$1" host="$2" path="$3" size_bytes="$4" chunked="$5"
  local body actual attempt curl_args
  body=$(head -c "$size_bytes" /dev/zero | tr '\0' 'a')
  curl_args=(-sk -o /dev/null -w "%{http_code}" --max-time 5 --resolve "${host}:443:127.0.0.1"
    -X POST -H "Content-Type: application/json")
  [[ "$chunked" == "chunked" ]] && curl_args+=(-H "Transfer-Encoding: chunked")
  for attempt in 1 2 3; do
    actual=$(printf '%s' "$body" | curl "${curl_args[@]}" --data-binary @- "https://${host}${path}" 2>/dev/null || echo "000")
    [[ "$actual" == "413" ]] && break
    [[ "$attempt" -lt 3 ]] && sleep 1
  done
  if [[ "$actual" == "413" ]]; then
    echo "  OK  [$actual] $desc"
  else
    echo "  FAIL[$actual != 413] $desc (https://${host}${path})" >&2
    FAIL=1
  fi
}

check_body_within_limit_not_rejected() {
  local desc="$1" host="$2" path="$3" size_bytes="$4"
  local body actual attempt
  body=$(head -c "$size_bytes" /dev/zero | tr '\0' 'a')
  for attempt in 1 2 3; do
    actual=$(printf '%s' "$body" | curl -sk -o /dev/null -w "%{http_code}" --max-time 5 \
      --resolve "${host}:443:127.0.0.1" -X POST -H "Content-Type: application/json" \
      --data-binary @- "https://${host}${path}" 2>/dev/null || echo "000")
    [[ "$actual" != "413" ]] && break
    [[ "$attempt" -lt 3 ]] && sleep 1
  done
  if [[ "$actual" != "413" ]]; then
    echo "  OK  [$actual] $desc"
  else
    echo "  FAIL[413] $desc (https://${host}${path}) — 정상 크기 요청까지 차단됨" >&2
    FAIL=1
  fi
}

check_header_present() {
  local desc="$1" host="$2" path="$3" header_line="$4"
  local headers actual attempt found
  for attempt in 1 2 3; do
    headers=$(curl -sk -D - -o /dev/null --max-time 5 --resolve "${host}:443:127.0.0.1" \
      "https://${host}${path}" 2>/dev/null || echo "")
    found=$(printf '%s' "$headers" | grep -Fi "$header_line" || true)
    [[ -n "$found" ]] && break
    [[ "$attempt" -lt 3 ]] && sleep 1
  done
  if [[ -n "$found" ]]; then
    echo "  OK  [header present] $desc"
  else
    echo "  FAIL[header missing: $header_line] $desc (https://${host}${path})" >&2
    FAIL=1
  fi
}

check_header_count_exactly_one() {
  local desc="$1" host="$2" path="$3" header_name="$4"
  local headers count attempt
  for attempt in 1 2 3; do
    headers=$(curl -sk -D - -o /dev/null --max-time 5 --resolve "${host}:443:127.0.0.1" \
      "https://${host}${path}" 2>/dev/null || echo "")
    count=$(printf '%s' "$headers" | grep -ci "^${header_name}:" || true)
    [[ "$count" -gt 0 ]] && break
    [[ "$attempt" -lt 3 ]] && sleep 1
  done
  if [[ "$count" -eq 1 ]]; then
    echo "  OK  [count=1] $desc"
  else
    echo "  FAIL[count=$count != 1] $desc (https://${host}${path}, header: $header_name)" >&2
    FAIL=1
  fi
}

# EDGE-CACHE-01(docs/improvement.md): check_header_present와 동일하지만 요청에 쿠키를 실어
# 보낼 수 있다 — kraft_logged_in 쿠키 유무와 무관하게 (public) 셸이 캐시 불가 헤더를 받는지
# 확인하는 데 쓴다.
check_header_present_with_cookie() {
  local desc="$1" host="$2" path="$3" header_line="$4" cookie="$5"
  local headers actual attempt found
  for attempt in 1 2 3; do
    headers=$(curl -sk -D - -o /dev/null --max-time 5 --resolve "${host}:443:127.0.0.1" \
      -b "$cookie" "https://${host}${path}" 2>/dev/null || echo "")
    found=$(printf '%s' "$headers" | grep -Fi "$header_line" || true)
    [[ -n "$found" ]] && break
    [[ "$attempt" -lt 3 ]] && sleep 1
  done
  if [[ -n "$found" ]]; then
    echo "  OK  [header present] $desc"
  else
    echo "  FAIL[header missing: $header_line] $desc (https://${host}${path}, cookie: $cookie)" >&2
    FAIL=1
  fi
}

# EDGE-CACHE-01: PublicAccountSlot이 kraft_logged_in 쿠키로 실제로 서버 분기를 하는지 —
# 로그인 UI(LoginPopover, aria-label="로그인 방법 선택")가 쿠키 유무에 따라 나타나거나 사라지는지
# 확인한다. 이 마크업이 두 상태에서 똑같다면 캐시 헤더를 고쳐도 애초에 분기가 죽어 있었다는 뜻이다.
check_login_branch_present() {
  local desc="$1" host="$2" path="$3" cookie="$4" should_contain="$5"
  local body attempt found
  for attempt in 1 2 3; do
    body=$(curl -sk --max-time 5 --resolve "${host}:443:127.0.0.1" -b "$cookie" \
      "https://${host}${path}" 2>/dev/null || echo "")
    found=$(printf '%s' "$body" | grep -Fc 'aria-label="로그인 방법 선택"' || true)
    { [[ "$should_contain" == "yes" && "$found" -gt 0 ]] || [[ "$should_contain" == "no" && "$found" -eq 0 ]]; } && break
    [[ "$attempt" -lt 3 ]] && sleep 1
  done
  if [[ "$should_contain" == "yes" && "$found" -gt 0 ]]; then
    echo "  OK  [login UI present] $desc"
  elif [[ "$should_contain" == "no" && "$found" -eq 0 ]]; then
    echo "  OK  [login UI absent] $desc"
  else
    echo "  FAIL[login UI presence=$found, expected should_contain=$should_contain] $desc (https://${host}${path}, cookie: $cookie)" >&2
    FAIL=1
  fi
}

echo "==> Caddy local routing check"
check_status "public domain /admin blocked"        "$KRAFT_DOMAIN"       "/admin"          "403"
check_status "public domain /actuator blocked"      "$KRAFT_DOMAIN"       "/actuator/health" "403"
check_status "public domain /ops blocked"           "$KRAFT_DOMAIN"       "/ops/x"          "403"
check_status "admin domain /actuator blocked"       "$KRAFT_ADMIN_DOMAIN" "/actuator/health" "403"
check_status "admin domain /admin/login reachable"  "$KRAFT_ADMIN_DOMAIN" "/admin/login"     "200"
check_status "admin domain /ops-api routes to backend /ops" "$KRAFT_ADMIN_DOMAIN" "/ops-api/summary" "401"
check_status "public domain /api/v1/community/session routes to backend" "$KRAFT_DOMAIN" "/api/v1/community/session" "200"
check_status_not_404 "public domain /oauth2/authorization/google routes to backend (not Next.js 404)" "$KRAFT_DOMAIN" "/oauth2/authorization/google"
# KF-21(docs/improvement.md): Next 16이 발행하는 실제 OG 경로는 콘텐츠 해시
# 접미사가 붙어(/opengraph-image-1c1a04?...) 예전 정확 경로 매치로는 안 걸렸다
# — 접두 매치로 바꿨다. Caddy의 header 지시자는 경로 매치만으로 실행되므로
# 실제 발행된 해시를 몰라도 임의 접미사로 회귀를 잡을 수 있다.
check_header_present "public domain root-level opengraph-image* gets immutable Cache-Control (KF-21)" \
  "$KRAFT_DOMAIN" "/opengraph-image-regression-check" "Cache-Control: public, max-age=31536000, immutable"
check_header_present "public domain nested /*/opengraph-image* gets immutable Cache-Control (KF-21)" \
  "$KRAFT_DOMAIN" "/analysis/opengraph-image-regression-check" "Cache-Control: public, max-age=31536000, immutable"
check_body_over_limit_rejected "client-error 8KB body (Content-Length) rejected"        "$KRAFT_DOMAIN" "/api/client-error" 8192 ""
check_body_over_limit_rejected "client-error 8KB body (chunked) rejected"               "$KRAFT_DOMAIN" "/api/client-error" 8192 "chunked"
check_body_within_limit_not_rejected "client-error 100B body not rejected by proxy"     "$KRAFT_DOMAIN" "/api/client-error" 100
check_header_present "client-error response has Cache-Control: no-store"               "$KRAFT_DOMAIN" "/api/client-error" "Cache-Control: no-store"

# I-09: reverse_proxy의 header_up 순서 문제로 catch-all 페이지 응답에 Cache-Control이
# 두 번(Caddy가 건 값 + Next.js가 낸 값) 붙었던 회귀를 잡는다.
check_header_count_exactly_one "public domain / has exactly one Cache-Control"              "$KRAFT_DOMAIN" "/" "Cache-Control"
# I-10: Caddy·SecurityHeadersFilter·Spring Security 기본 헤더 writer 세 곳이 같은 보안
# 헤더를 각자 발급하던 회귀를 잡는다. HTML 라우트와 API 라우트 양쪽에서 검증한다.
check_header_count_exactly_one "public domain / has exactly one Strict-Transport-Security"  "$KRAFT_DOMAIN" "/" "Strict-Transport-Security"
check_header_count_exactly_one "public domain / has exactly one X-Frame-Options"             "$KRAFT_DOMAIN" "/" "X-Frame-Options"
check_header_count_exactly_one "public domain / has exactly one X-Content-Type-Options"      "$KRAFT_DOMAIN" "/" "X-Content-Type-Options"
check_header_count_exactly_one "public domain / has exactly one Referrer-Policy"             "$KRAFT_DOMAIN" "/" "Referrer-Policy"
check_header_count_exactly_one "public domain / has exactly one Permissions-Policy"          "$KRAFT_DOMAIN" "/" "Permissions-Policy"
check_header_count_exactly_one "public API /api/v1/rounds/latest has exactly one Strict-Transport-Security" "$KRAFT_DOMAIN" "/api/v1/rounds/latest" "Strict-Transport-Security"
check_header_count_exactly_one "public API /api/v1/rounds/latest has exactly one X-Frame-Options"            "$KRAFT_DOMAIN" "/api/v1/rounds/latest" "X-Frame-Options"

# EDGE-CACHE-01(docs/improvement.md): (public) 셸이 kraft_logged_in 쿠키로 로그인 분기 HTML을
# 만드는데도 catch-all이 public 캐시를 광고하던 문제. 옵션 2(private, no-store 전환) 적용 후
# 익명/로그인 두 상태 모두 캐시 불가 헤더를 받는지, 그리고 실제로 분기가 살아있는지 함께 확인한다.
check_header_present_with_cookie "public domain / (익명) 은 private, no-store" \
  "$KRAFT_DOMAIN" "/" "Cache-Control: private, no-store" ""
check_header_present_with_cookie "public domain / (로그인 쿠키 보유) 도 private, no-store" \
  "$KRAFT_DOMAIN" "/" "Cache-Control: private, no-store" "kraft_logged_in=1"
check_header_present_with_cookie "public domain /info/whatever (익명) 도 private, no-store" \
  "$KRAFT_DOMAIN" "/info/whatever" "Cache-Control: private, no-store" ""
check_login_branch_present "public domain / (익명) 은 로그인 UI를 렌더" \
  "$KRAFT_DOMAIN" "/" "" "yes"
check_login_branch_present "public domain / (로그인 쿠키 보유) 는 로그인 UI를 렌더하지 않음" \
  "$KRAFT_DOMAIN" "/" "kraft_logged_in=1" "no"

if [[ $FAIL -ne 0 ]]; then
  echo "==> Caddy local routing check FAILED — Caddyfile is misconfigured" >&2
  exit 1
fi
echo "==> Caddy local routing check passed"
