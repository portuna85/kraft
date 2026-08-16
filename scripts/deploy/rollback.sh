#!/usr/bin/env bash
# Rolls back to the previous image tag by restarting with the prior SHA tag.
# Usage: rollback.sh <service> <previous-image-ref>
# Example: rollback.sh backend ghcr.io/owner/kraft-backend:sha-abc1234
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
ENV_FILE="${ENV_FILE:-.env.prod}"
COMPOSE_FILE="$REPO_ROOT/docker-compose.prod.yml"
MANIFEST="${KNOWN_GOOD_MANIFEST:-$REPO_ROOT/deploy/known-good.env}"

SERVICE="${1:?Usage: rollback.sh <service> <image-ref>}"
IMAGE_REF="${2:?Usage: rollback.sh <service> <image-ref>}"

cd "$REPO_ROOT"

echo "==> Rolling back $SERVICE to $IMAGE_REF"

# Split full image ref (repo:tag) into separate env vars that docker-compose.prod.yml expects
IMAGE_TAG="${IMAGE_REF##*:}"
IMAGE_REPO="${IMAGE_REF%:*}"

case "$SERVICE" in
  backend)
    ENV_KEY_REF="KRAFT_BACKEND_IMAGE_REF"
    ENV_KEY_TAG="KRAFT_BACKEND_IMAGE_TAG"
    ;;
  web)
    ENV_KEY_REF="KRAFT_WEB_IMAGE_REF"
    ENV_KEY_TAG="KRAFT_WEB_IMAGE_TAG"
    ;;
  *)
    echo "Unknown service: $SERVICE (expected backend or web)" >&2
    exit 1
    ;;
esac
export "${ENV_KEY_REF}=$IMAGE_REPO"
export "${ENV_KEY_TAG}=$IMAGE_TAG"

# 컨테이너 재기동뿐 아니라 다음 재기동/재부팅 시점에도 이 버전이 유지되도록
# git HEAD와 .env.prod 자체를 되돌린다. 그렇지 않으면 재기동 시 여전히 실패한
# SHA/이미지 태그를 가리키는 상태로 남는다.
if [[ -f "$MANIFEST" ]]; then
  # shellcheck source=/dev/null
  source "$MANIFEST"
  if [[ -n "${KNOWN_GOOD_SHA:-}" ]]; then
    echo "==> known-good SHA로 git HEAD 되돌림: $KNOWN_GOOD_SHA"
    git -C "$REPO_ROOT" reset --hard "$KNOWN_GOOD_SHA"
  fi
else
  echo "==> WARN: known-good manifest($MANIFEST) 없음 — git HEAD/.env.prod는 되돌리지 않고 컨테이너만 재기동합니다." >&2
fi

if [[ -f "$ENV_FILE" ]]; then
  TMP_ENV="$(mktemp "$(dirname "$ENV_FILE")/.env.prod.rollback.XXXXXX")"
  sed -E \
    -e "s|^${ENV_KEY_REF}=.*|${ENV_KEY_REF}=${IMAGE_REPO}|" \
    -e "s|^${ENV_KEY_TAG}=.*|${ENV_KEY_TAG}=${IMAGE_TAG}|" \
    "$ENV_FILE" > "$TMP_ENV"
  chmod 600 "$TMP_ENV"
  mv "$TMP_ENV" "$ENV_FILE"
  echo "==> $ENV_FILE의 ${ENV_KEY_REF}/${ENV_KEY_TAG}를 롤백 대상으로 갱신"
fi

docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" \
  up -d --no-deps "$SERVICE"

echo "==> Waiting for readiness after rollback..."
bash scripts/deploy/wait-readiness.sh

# CD 파이프라인(cd.yml)은 SSH 세션 자체에 KRAFT_PUBLIC_BASE_URL 등을 이미 주입해 두고
# pull-and-up.sh를 실행하지만, rollback.sh는 실제 장애 대응 중 운영자가 SSH로 직접
# 실행하는 경로라 그 env가 없다 — 그러면 smoke-test.sh가 기본값 http://localhost로
# 떨어져 Caddy의 자동 HTTPS 리다이렉트(308)에 전부 걸려 "롤백은 성공했는데 스모크
# 테스트만 실패"로 보이는 거짓 경보가 뜬다(실제로 이 스크립트로 겪음). .env.prod를
# 자급자족용으로 읽어들이되, `source`로 셸 스크립트처럼 실행하지 않는다 —
# KRAFT_EXTERNAL_LOTTO_AUTO_COLLECT_CRON="0 30/15 21-23 * * SAT"처럼 공백 포함
# 값이 있으면 `source`는 이를 "VAR=0 명령 30/15 인자..."로 잘못 파싱해 `30/15`를
# 명령으로 실행하려다 실패한다(2026-08-16 실제 장애: 롤백 자체가 이 줄에서 죽어
# "수동 개입 필요"로 남았다). 값을 셸 문법으로 재해석하지 않는 read 루프로 대신한다.
if [[ -f "$ENV_FILE" ]]; then
  set -a
  while IFS='=' read -r env_key env_value; do
    [[ -z "$env_key" || "$env_key" == \#* ]] && continue
    export "$env_key=$env_value"
  done < "$ENV_FILE"
  set +a
fi

echo "==> Running smoke test..."
bash scripts/deploy/smoke-test.sh

echo "Rollback complete: $SERVICE → $IMAGE_REF"
