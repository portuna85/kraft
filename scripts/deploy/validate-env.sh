#!/usr/bin/env bash
# Validates that all required environment variables are set before deploy.
set -euo pipefail

# KRAFT_DB_URL and KRAFT_DB_USERNAME are hardcoded in .env.prod.example — not shell env secrets
REQUIRED_VARS=(
  MARIADB_ROOT_PASSWORD
  MARIADB_PASSWORD
  KRAFT_DB_PASSWORD
  KRAFT_OPS_TOKEN
  KRAFT_REVALIDATE_SECRET
  KRAFT_PUBLIC_BASE_URL
  GRAFANA_ADMIN_PASSWORD
  KRAFT_ADMIN_ALLOWED_CIDR
)

OPTIONAL_VARS=(
  KRAFT_ADMIN_BOOTSTRAP_USERNAME
  KRAFT_ADMIN_BOOTSTRAP_PASSWORD
  ALERTMANAGER_DISCORD_WEBHOOK_URL
  KRAFT_EXTERNAL_LOTTO_URL_TEMPLATE
  KRAFT_EXTERNAL_LOTTO_AUTO_COLLECT_CRON
  KRAFT_SECURITY_TRUSTED_PROXY_CIDR
  KRAFT_SECURITY_RATE_LIMIT_PER_MINUTE
  KRAFT_SECURITY_RATE_LIMIT_MAX_KEYS
  KRAFT_RATE_LIMIT_BACKEND
  KRAFT_REDIS_HOST
  KRAFT_REDIS_PORT
  KRAFT_SAVED_MAX_PER_CLIENT
  # 비어 있으면 해당 provider의 커뮤니티 로그인만 비활성화되고 나머지 앱은 정상 기동한다
  # (application.yml의 provider별 profile 등록 + CommunityOAuth2FallbackConfig, 2026-07-24).
  KRAFT_COMMUNITY_GOOGLE_CLIENT_ID
  KRAFT_COMMUNITY_GOOGLE_CLIENT_SECRET
  KRAFT_COMMUNITY_NAVER_CLIENT_ID
  KRAFT_COMMUNITY_NAVER_CLIENT_SECRET
)

error=0
for var in "${REQUIRED_VARS[@]}"; do
  if [[ -z "${!var:-}" ]]; then
    echo "ERROR: required variable not set: $var" >&2
    error=1
  fi
done

for var in "${OPTIONAL_VARS[@]}"; do
  if [[ -z "${!var:-}" ]]; then
    echo "WARN:  optional variable not set (will use default): $var"
  fi
done

validate_oauth_pair() {
  local provider="$1"
  local client_id_var="$2"
  local client_secret_var="$3"
  local client_id="${!client_id_var:-}"
  local client_secret="${!client_secret_var:-}"

  if [[ -n "$client_id" && -z "$client_secret" ]]; then
    echo "ERROR: $provider OAuth client ID is set but client secret is missing: $client_secret_var" >&2
    error=1
  elif [[ -z "$client_id" && -n "$client_secret" ]]; then
    echo "ERROR: $provider OAuth client secret is set but client ID is missing: $client_id_var" >&2
    error=1
  fi
}

# OAuth provider는 선택 사항이지만, 활성화할 때는 ID와 secret이 반드시 한 쌍이어야 한다.
# 부분 설정을 허용하면 Spring Boot가 등록 검증 중 실패해 전체 서비스가 기동하지 못한다.
validate_oauth_pair "Google" \
  KRAFT_COMMUNITY_GOOGLE_CLIENT_ID KRAFT_COMMUNITY_GOOGLE_CLIENT_SECRET
validate_oauth_pair "Naver" \
  KRAFT_COMMUNITY_NAVER_CLIENT_ID KRAFT_COMMUNITY_NAVER_CLIENT_SECRET

if [[ "${KRAFT_ADMIN_ALLOWED_CIDR:-}" == *"0.0.0.0/0"* && "${KRAFT_ALLOW_WORLD_OPEN_ADMIN:-}" != "true" ]]; then
  echo "ERROR: KRAFT_ADMIN_ALLOWED_CIDR가 전체 개방(0.0.0.0/0)입니다. 의도라면 KRAFT_ALLOW_WORLD_OPEN_ADMIN=true를 명시하세요." >&2
  error=1
fi

[[ $error -eq 0 ]] && echo "OK: all required variables are set" || exit 1
