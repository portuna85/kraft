#!/usr/bin/env bash
# Pulls latest images and restarts only the services that changed.
# Usage: pull-and-up.sh [--env-file <path>]
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
ENV_FILE="${ENV_FILE:-.env.prod}"
COMPOSE_FILE="$REPO_ROOT/docker-compose.prod.yml"
DEPLOY_BACKEND_CHANGED="${DEPLOY_BACKEND_CHANGED:-true}"
DEPLOY_WEB_CHANGED="${DEPLOY_WEB_CHANGED:-true}"
DEPLOY_INFRA_CHANGED="${DEPLOY_INFRA_CHANGED:-true}"

cd "$REPO_ROOT"

echo "==> Validating environment..."
bash scripts/deploy/validate-env.sh
echo "==> Admin allowlist CIDR: ${KRAFT_ADMIN_ALLOWED_CIDR:-<unset>}"

pull_services=()
[[ "$DEPLOY_BACKEND_CHANGED" == "true" ]] && pull_services+=(backend)
[[ "$DEPLOY_WEB_CHANGED" == "true" ]] && pull_services+=(web)
if [[ "$DEPLOY_INFRA_CHANGED" == "true" ]]; then
  pull_services+=(mariadb caddy prometheus grafana alertmanager node-exporter)
fi

if (( ${#pull_services[@]} > 0 )); then
  echo "==> Pulling affected images: ${pull_services[*]}"
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" pull --quiet "${pull_services[@]}"
else
  echo "==> No image changes; skipping registry pulls."
fi

echo "==> Starting services (no-deps rolling update)..."
# compose는 web의 depends_on: backend: condition: service_healthy 때문에 backend가
# unhealthy면 이 명령 자체가 실패하고 즉시 return한다 — 이 시점에 실패하면
# wait-readiness.sh의 진단 로그(타임아웃 시 docker logs)는 실행될 기회조차 없으므로
# 여기서 직접 backend 로그를 남겨야 진짜 실패 원인(예외 스택트레이스)을 알 수 있다.
if ! docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up -d --remove-orphans; then
  echo "==> [DEBUG] 'docker compose up' 실패 — backend 컨테이너 로그 (최근 150줄):" >&2
  docker logs kraft-backend --tail 150 2>&1 || true
  exit 1
fi

# Compose only recreates a container when its own config (image/env/etc.) changes —
# it cannot detect content changes inside a bind-mounted file like Caddyfile, so a
# Caddyfile-only edit leaves the old container running untouched. `caddy reload`
# (even with --address/--force) repeatedly failed to pick up new config in
# production for reasons that don't reproduce locally, so instead we force a full
# container recreate every deploy: a fresh `caddy run` always reads the file fresh
# from disk, no reload mechanism involved. Costs a ~1s connection blip, trades
# reliability for that.
#
# 2026-08-16: 이 재생성을 DEPLOY_INFRA_CHANGED로 조건부 실행했었다 — 이 플래그는
# CI의 "Detect Changes"가 github.event.before(직전 커밋)와 비교해 계산하는데, 직전
# 배포가 실패해 건너뛰었으면 그 직전 커밋 자체가 아직 서버에 반영 안 된 상태다.
# 그 경우 "이번 push는 Caddyfile을 안 건드렸다"는 게 사실이어도 서버는 여전히
# 그보다 더 예전 설정을 돌리고 있어 결과적으로 새 설정이 영영 반영되지 않았다
# (실제로 이렇게 한 번 놓쳤다). 재생성 자체는 ~1초 블립뿐이라 비용이 작으므로,
# 플래그로 아끼려 하지 않고 매 배포마다 무조건 재생성한다.
echo "==> Recreating Caddy to guarantee fresh config..."
docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up -d --force-recreate --no-deps caddy
sleep 2

# alertmanager.yml(webhook 렌더링)과 prometheus.yml/rules(alert rule)도 같은 문제를 겪는다 —
# render-alertmanager.sh가 파일을 새로 써도 이미 떠 있는 컨테이너는 기동 시 로드한 예전 설정을
# 계속 메모리에 들고 있다. 예: webhook 값이 바뀌어도 재기동 전까지는 옛 값(더미 localhost 등)으로
# 계속 전송을 시도한다. Caddy와 같은 이유로 플래그에 기대지 않고 매 배포마다 강제 재생성한다.
echo "==> Recreating Alertmanager/Prometheus to guarantee fresh config..."
docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up -d --force-recreate --no-deps alertmanager prometheus

echo "==> Fast local Caddy routing check (catches Caddyfile bugs before the slow external smoke test)..."
bash scripts/deploy/check-caddy-routes.sh

echo "==> Waiting for readiness..."
bash scripts/deploy/wait-readiness.sh

echo "==> Running smoke test..."
bash scripts/deploy/smoke-test.sh

echo "Deploy complete."
