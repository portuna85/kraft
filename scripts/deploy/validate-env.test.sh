#!/usr/bin/env bash
# validate-env.sh의 시크릿 강도·CIDR 검증 로직에 대한 회귀 테스트.
# bats 등 별도 도구 없이, 매 케이스마다 최소 필수 변수 세트를 채운 서브셸에서
# validate-env.sh를 실행해 종료 코드를 확인한다.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TARGET="$SCRIPT_DIR/validate-env.sh"

pass_count=0
fail_count=0

# 모든 REQUIRED_VARS를 통과 가능한 값으로 채운 뒤, 인자로 받은 env 오버라이드만 덮어써서 실행한다.
run_case() {
  local expect="$1" # "pass" 또는 "fail"
  shift
  local output
  local status=0
  output=$(env -i \
    PATH="$PATH" \
    MARIADB_ROOT_PASSWORD=root-password-value-long-enough \
    MARIADB_PASSWORD=maria-password-value-long-enough \
    KRAFT_DB_PASSWORD=db-password-value-long-enough \
    KRAFT_OPS_TOKEN="$(printf 'a%.0s' {1..40})" \
    KRAFT_REVALIDATE_SECRET="$(printf 'b%.0s' {1..40})" \
    KRAFT_PUBLIC_BASE_URL=https://kraft.io.kr \
    GRAFANA_ADMIN_PASSWORD=grafana-password-value-long-enough \
    KRAFT_ADMIN_ALLOWED_CIDR=203.0.113.4/32 \
    KRAFT_DOMAIN=kraft.io.kr \
    KRAFT_ADMIN_DOMAIN=admin.kraft.io.kr \
    KRAFT_BACKEND_IMAGE_REF=ghcr.io/example/backend \
    KRAFT_BACKEND_IMAGE_TAG=abc1234 \
    KRAFT_WEB_IMAGE_REF=ghcr.io/example/web \
    KRAFT_WEB_IMAGE_TAG=abc1234 \
    "$@" \
    bash "$TARGET" 2>&1) && status=0 || status=$?

  local actual="pass"
  [[ $status -ne 0 ]] && actual="fail"

  if [[ "$actual" == "$expect" ]]; then
    echo "OK: $* -> $actual (expected $expect)"
    pass_count=$((pass_count + 1))
  else
    echo "FAIL: $* -> $actual (expected $expect)" >&2
    echo "$output" >&2
    fail_count=$((fail_count + 1))
  fi
}

# 기준선: 모두 유효한 값이면 통과해야 한다.
run_case pass

# TD-006: IPv4/IPv6 world-open CIDR는 오버라이드 없이 거부돼야 한다.
run_case fail KRAFT_ADMIN_ALLOWED_CIDR=0.0.0.0/0
run_case fail KRAFT_ADMIN_ALLOWED_CIDR=::/0

# 오버라이드 플래그를 명시하면 world-open도 통과해야 한다(기존 동작 유지).
run_case pass KRAFT_ADMIN_ALLOWED_CIDR=0.0.0.0/0 KRAFT_ALLOW_WORLD_OPEN_ADMIN=true
run_case pass KRAFT_ADMIN_ALLOWED_CIDR=::/0 KRAFT_ALLOW_WORLD_OPEN_ADMIN=true

# TD-005: 32자 미만의 짧은 토큰/시크릿은 거부돼야 한다.
run_case fail KRAFT_OPS_TOKEN=short-token
run_case fail KRAFT_REVALIDATE_SECRET=short-secret

# 기존 예시값 blocklist는 계속 동작해야 한다.
run_case fail KRAFT_OPS_TOKEN=local-dev-ops-token
run_case fail KRAFT_ADMIN_BOOTSTRAP_USERNAME=admin KRAFT_ADMIN_BOOTSTRAP_PASSWORD=admin

# BE-SEC-02: RFC1918 광역대를 명시하면 오버라이드 없이 거부돼야 한다.
run_case fail KRAFT_SECURITY_TRUSTED_PROXY_CIDR=10.0.0.0/8
run_case fail KRAFT_SECURITY_TRUSTED_PROXY_CIDR=172.16.0.0/12
run_case fail KRAFT_SECURITY_TRUSTED_PROXY_CIDR=192.168.0.0/16
run_case fail "KRAFT_SECURITY_TRUSTED_PROXY_CIDR=172.16.0.0/12,10.0.0.0/8,192.168.0.0/16"

# 오버라이드 플래그를 명시하면 광역대도 통과해야 한다.
run_case pass KRAFT_SECURITY_TRUSTED_PROXY_CIDR=10.0.0.0/8 KRAFT_ALLOW_BROAD_TRUSTED_PROXY=true

# 좁은 CIDR(운영이 실제로 좁힌 값)은 그대로 통과해야 한다.
run_case pass KRAFT_SECURITY_TRUSTED_PROXY_CIDR=172.28.0.5/32

# I-18: docker-compose.prod.yml이 기본값 없이 참조하는 도메인·이미지 참조/태그
# 변수도 REQUIRED_VARS에 있어야 한다 — 없으면 Caddy 도메인 매치나 이미지 참조가
# 빈 값으로 조용히 `docker compose up`까지 흘러간다.
run_case fail KRAFT_DOMAIN=
run_case fail KRAFT_ADMIN_DOMAIN=
run_case fail KRAFT_BACKEND_IMAGE_REF=
run_case fail KRAFT_BACKEND_IMAGE_TAG=
run_case fail KRAFT_WEB_IMAGE_REF=
run_case fail KRAFT_WEB_IMAGE_TAG=

echo "----"
echo "passed: $pass_count, failed: $fail_count"
[[ $fail_count -eq 0 ]]
