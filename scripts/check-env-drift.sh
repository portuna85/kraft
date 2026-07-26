#!/usr/bin/env bash
# C-02: 환경 변수 계약이 여러 곳(Compose 파일, Spring 프로퍼티, 배포 스크립트,
# .env*.example, CI 워크플로 build-args)에 중복 표현되는데, 하나를 고치고 나머지를
# 깜빡하는 드리프트를 값 노출 없이(이름만 비교) 잡는다. 실패 시 어느 변수가
# 어느 방향으로 어긋났는지만 보고하고, 값은 절대 출력하지 않는다.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

VAR_PATTERN='[A-Z][A-Z0-9_]*'
PREFIX_FILTER='^(KRAFT_|MARIADB_|SPRING_PROFILES_ACTIVE$|NEXT_PUBLIC_|GRAFANA_|ALERTMANAGER_|ALERTING_DISABLED$)'

# 실제로 소비되는 변수: Compose 파일의 ${VAR}, application*.yml의 ${VAR:default},
# 배포 스크립트(scripts/deploy/*.sh)가 참조하는 변수.
used_vars() {
  {
    grep -ohE "\\\$\\{${VAR_PATTERN}(:[^}]*)?\\}" docker-compose.yml docker-compose.local.yml docker-compose.prod.yml 2>/dev/null \
      | sed -E "s/\\\$\\{(${VAR_PATTERN}).*/\\1/"
    grep -ohE "\\\$\\{${VAR_PATTERN}(:[^}]*)?\\}" src/main/resources/application.yml src/main/resources/application-prod.yml src/main/resources/application-local.yml 2>/dev/null \
      | sed -E "s/\\\$\\{(${VAR_PATTERN}).*/\\1/"
    grep -ohE "\\\$\\{?${VAR_PATTERN}\\}?" scripts/deploy/*.sh 2>/dev/null \
      | sed -E 's/[${}]//g'
  } | grep -E "$PREFIX_FILTER" | sort -u
}

# 문서화된 변수: 3종 .env*.example의 키, CI 워크플로의 secrets.VAR/vars.VAR 참조
# (NEXT_PUBLIC_* 광고 변수처럼 런타임 .env가 아니라 CI 빌드 시점 시크릿으로만 공급되는
# 계약도 있다 — build-args=... 형태 자체가 "이 변수가 존재한다"는 문서다).
documented_vars() {
  {
    # 주석 처리된 채로 존재하는 항목(예: 위험한 opt-in 플래그, 선택적 provider 쌍)도
    # "이 변수 이름이 존재한다는 사실"은 이미 문서화된 것이므로 활성/비활성 여부와
    # 무관하게 키만 뽑는다 — 앞에 붙은 "# "는 제거하고 매칭한다.
    sed -E 's/^#[[:space:]]*//' .env.example .env.local.example .env.prod.example 2>/dev/null \
      | grep -ohE "^${VAR_PATTERN}=" \
      | sed 's/=$//'
    grep -ohE "(secrets|vars)\.${VAR_PATTERN}" .github/workflows/*.yml 2>/dev/null \
      | sed -E 's/(secrets|vars)\.//'
  } | grep -E "$PREFIX_FILTER" | sort -u
}

USED_FILE="$(mktemp)"
DOCUMENTED_FILE="$(mktemp)"
trap 'rm -f "$USED_FILE" "$DOCUMENTED_FILE"' EXIT

used_vars > "$USED_FILE"
documented_vars > "$DOCUMENTED_FILE"

MISSING=$(comm -23 "$USED_FILE" "$DOCUMENTED_FILE")

error=0
if [[ -n "$MISSING" ]]; then
  echo "ERROR: 다음 변수는 Compose/Spring 설정/배포 스크립트에서 실제로 쓰이지만," >&2
  echo "       어떤 .env*.example에도, CI 워크플로 secrets/vars 참조에도 문서화되지 않았다:" >&2
  while IFS= read -r var; do echo "  - $var" >&2; done <<< "$MISSING"
  error=1
fi

[[ $error -eq 0 ]] && echo "OK: 실제 사용되는 환경 변수가 모두 최소 한 곳에 문서화되어 있다"
exit $error
