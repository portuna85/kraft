#!/usr/bin/env bash
# Renders infra/alertmanager/alertmanager.yml from the .tmpl file.
# Requires: ALERTMANAGER_DISCORD_WEBHOOK_URL, unless ALERTING_DISABLED=true is
# explicitly set (로컬/스테이징 전용 — 운영 배포는 옵트아웃 없이 webhook을 요구한다).
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TMPL="$REPO_ROOT/infra/alertmanager/alertmanager.yml.tmpl"
OUT="$REPO_ROOT/infra/alertmanager/alertmanager.yml"

# OUT은 .gitignore 대상이라 clean clone에는 존재하지 않는다. 이 스크립트보다 먼저
# `docker compose up`을 실행하면 Docker가 bind-mount 대상을 디렉터리로 자동 생성해버려서,
# 이후 여기서 envsubst로 쓰려고 하면 "Is a directory" 오류로 조용히 실패한다 — 원인을
# 바로 알 수 있도록 여기서 먼저 감지하고 복구 방법을 안내한다.
if [[ -d "$OUT" ]]; then
  echo "ERROR: $OUT exists as a DIRECTORY, not a file." >&2
  echo "Docker likely auto-created it as a bind-mount target before this script ran" >&2
  echo "(e.g. 'docker compose up' ran before this render). Fix:" >&2
  echo "  rmdir \"$OUT\"" >&2
  echo "then re-run this script before 'docker compose up'." >&2
  exit 1
fi

if [[ -z "${ALERTMANAGER_DISCORD_WEBHOOK_URL:-}" ]]; then
  if [[ "${ALERTING_DISABLED:-false}" != "true" ]]; then
    echo "ERROR: ALERTMANAGER_DISCORD_WEBHOOK_URL not set. Set it, or set ALERTING_DISABLED=true to explicitly opt out (로컬/스테이징 전용)." >&2
    exit 1
  fi
  echo "WARN: ALERTING_DISABLED=true — Discord 알림 비활성화(더미 webhook 사용)"
  # Dummy but URL-valid value: Alertmanager rejects an empty webhook_url at
  # startup ("unsupported scheme \"\" for URL") and crash-loops. http://localhost
  # parses fine, so the container stays up; alerts just silently fail to send.
  ALERTMANAGER_DISCORD_WEBHOOK_URL="http://localhost"
fi
# envsubst runs as a separate child process and only sees *exported* vars —
# a plain assignment isn't enough if the var was never exported in the caller's
# shell to begin with.
export ALERTMANAGER_DISCORD_WEBHOOK_URL

# 외부 heartbeat(healthchecks.io 등)는 선택 기능이다 — Discord와 달리 옵트아웃
# 요구 없이 미설정을 허용하고, 더미 URL로 채워 Alertmanager 기동은 항상 성공시킨다
# (Watchdog 경보 전송이 조용히 실패할 뿐 배포가 막히지는 않는다).
if [[ -z "${KRAFT_HEARTBEAT_URL:-}" ]]; then
  echo "WARN: KRAFT_HEARTBEAT_URL not set — Watchdog heartbeat alerts will target a dummy URL and silently fail to send. Prometheus/Alertmanager 자체 장애를 감지할 외부 heartbeat가 없다는 뜻이다."
  KRAFT_HEARTBEAT_URL="http://localhost"
fi
export KRAFT_HEARTBEAT_URL

envsubst < "$TMPL" > "$OUT"
echo "OK: rendered $OUT"
