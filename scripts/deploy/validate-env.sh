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
  # I-18: docker-compose.prod.yml이 ${VAR} 형태(기본값 없음)로 참조하지만 여태 이
  # 목록에 없었다 — 비어 있으면 Caddy 도메인 매치가 깨지거나(KRAFT_DOMAIN/
  # KRAFT_ADMIN_DOMAIN) 이미지 참조가 ":"만 남는 채로(KRAFT_*_IMAGE_REF/TAG)
  # `docker compose up`까지 그대로 흘러간다.
  KRAFT_DOMAIN
  KRAFT_ADMIN_DOMAIN
  KRAFT_BACKEND_IMAGE_REF
  KRAFT_BACKEND_IMAGE_TAG
  KRAFT_WEB_IMAGE_REF
  KRAFT_WEB_IMAGE_TAG
)

OPTIONAL_VARS=(
  KRAFT_ADMIN_BOOTSTRAP_USERNAME
  KRAFT_ADMIN_BOOTSTRAP_PASSWORD
  ALERTMANAGER_DISCORD_WEBHOOK_URL
  KRAFT_HEARTBEAT_URL
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

if [[ "${KRAFT_ALLOW_WORLD_OPEN_ADMIN:-}" != "true" ]]; then
  if [[ "${KRAFT_ADMIN_ALLOWED_CIDR:-}" == *"0.0.0.0/0"* ]]; then
    echo "ERROR: KRAFT_ADMIN_ALLOWED_CIDR가 IPv4 전체 개방(0.0.0.0/0)입니다. 의도라면 KRAFT_ALLOW_WORLD_OPEN_ADMIN=true를 명시하세요." >&2
    error=1
  fi
  # TD-006: 0.0.0.0/0만 막고 IPv6 동치(::/0)는 놓치고 있었다 — Caddy의 remote_ip 매처는
  # 두 표기 모두 이해하므로, 배포 검증도 동일하게 두 표기 모두 거부해야 한다.
  if [[ "${KRAFT_ADMIN_ALLOWED_CIDR:-}" == *"::/0"* ]]; then
    echo "ERROR: KRAFT_ADMIN_ALLOWED_CIDR가 IPv6 전체 개방(::/0)입니다. 의도라면 KRAFT_ALLOW_WORLD_OPEN_ADMIN=true를 명시하세요." >&2
    error=1
  fi
fi

# BE-SEC-02(docs/improvement.md): docker-compose.prod.yml:122의 기본값(변수 미설정 시)이
# RFC1918 전체(172.16.0.0/12,10.0.0.0/8,192.168.0.0/16)라, 신뢰 프록시 CIDR이 좁혀지지
# 않은 채 배포되면 사실상 앱 네트워크의 모든 컨테이너가 IP 스푸핑으로 rate limit을 우회할
# 수 있다. KRAFT_ADMIN_ALLOWED_CIDR의 0.0.0.0/0 거부와 같은 패턴 — 명시적으로 광역
# 값을 넣었을 때만 거부한다(값을 아예 비워 compose 기본값이 적용되는 경우는 이 셸
# 검증만으로는 잡을 수 없다 — 그 경우는 배포 후 BE-SEC-02의 나머지 항목인 외부 관점
# smoke test로 확인한다).
if [[ "${KRAFT_ALLOW_BROAD_TRUSTED_PROXY:-}" != "true" ]]; then
  cidr="${KRAFT_SECURITY_TRUSTED_PROXY_CIDR:-}"
  if [[ "$cidr" == *"10.0.0.0/8"* || "$cidr" == *"172.16.0.0/12"* || "$cidr" == *"192.168.0.0/16"* ]]; then
    echo "ERROR: KRAFT_SECURITY_TRUSTED_PROXY_CIDR가 RFC1918 광역대를 포함합니다. 의도라면 KRAFT_ALLOW_BROAD_TRUSTED_PROXY=true를 명시하세요." >&2
    error=1
  fi
fi

# L-5: .env.local.example은 로컬 전용 의도로 admin/admin, local-dev-ops-token 같은
# 약한 값을 그대로 적어 두는데, 이 파일을 복붙해 prod에 쓰는 실수를 막을 검증이
# 없었다(KRAFT_ADMIN_ALLOWED_CIDR의 0.0.0.0/0 거부와 같은 패턴을 자격증명에도 적용).
if [[ "${KRAFT_ADMIN_BOOTSTRAP_USERNAME:-}" == "admin" && "${KRAFT_ADMIN_BOOTSTRAP_PASSWORD:-}" == "admin" ]]; then
  echo "ERROR: KRAFT_ADMIN_BOOTSTRAP_USERNAME/PASSWORD가 .env.local.example의 예시값(admin/admin)입니다. 실제 값으로 교체하세요." >&2
  error=1
fi

if [[ "${KRAFT_OPS_TOKEN:-}" == "local-dev-ops-token" ]]; then
  echo "ERROR: KRAFT_OPS_TOKEN이 .env.local.example의 예시값(local-dev-ops-token)입니다. 무작위 값으로 교체하세요." >&2
  error=1
fi

# TD-005: 예시값 blocklist만으로는 "admin"처럼 짧지만 예시값이 아닌 약한 값을 못 거른다.
# 값 자체는 로그에 남기지 않고 길이만 검사한다.
MIN_SECRET_LENGTH=32
check_min_length() {
  local var_name="$1"
  local value="${!var_name:-}"
  if [[ -n "$value" && ${#value} -lt $MIN_SECRET_LENGTH ]]; then
    echo "ERROR: $var_name의 길이가 ${MIN_SECRET_LENGTH}자 미만입니다. 무작위로 생성한 긴 값으로 교체하세요." >&2
    error=1
  fi
}
check_min_length KRAFT_OPS_TOKEN
check_min_length KRAFT_REVALIDATE_SECRET

[[ $error -eq 0 ]] && echo "OK: all required variables are set" || exit 1
