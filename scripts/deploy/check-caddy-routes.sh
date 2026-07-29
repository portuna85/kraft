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

echo "==> Caddy local routing check"
check_status "public domain /admin blocked"        "$KRAFT_DOMAIN"       "/admin"          "403"
check_status "public domain /actuator blocked"      "$KRAFT_DOMAIN"       "/actuator/health" "403"
check_status "public domain /ops blocked"           "$KRAFT_DOMAIN"       "/ops/x"          "403"
check_status "admin domain /actuator blocked"       "$KRAFT_ADMIN_DOMAIN" "/actuator/health" "403"
check_status "admin domain /admin/login reachable"  "$KRAFT_ADMIN_DOMAIN" "/admin/login"     "200"
check_status "admin domain /ops-api routes to backend /ops" "$KRAFT_ADMIN_DOMAIN" "/ops-api/summary" "401"
check_status "public domain /api/v1/community/session routes to backend" "$KRAFT_DOMAIN" "/api/v1/community/session" "200"
check_status_not_404 "public domain /oauth2/authorization/google routes to backend (not Next.js 404)" "$KRAFT_DOMAIN" "/oauth2/authorization/google"

if [[ $FAIL -ne 0 ]]; then
  echo "==> Caddy local routing check FAILED — Caddyfile is misconfigured" >&2
  exit 1
fi
echo "==> Caddy local routing check passed"
