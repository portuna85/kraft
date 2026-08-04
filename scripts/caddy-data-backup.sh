#!/usr/bin/env bash
# L-6: caddy-data 볼륨(ACME 계정 키·인증서)을 백업한다. DB 백업 파이프라인은
# db-backup.sh로 잘 갖춰져 있는데 이 볼륨은 아무 데도 백업되지 않았다 — 유실되면
# Let's Encrypt rate limit이 걸린 채로 재발급을 시도해야 하는데, 그게 필요한 시점은
# 정확히 서버 장애 복구 중이다.
#
# caddy-data는 Docker named volume이라 Compose 프로젝트 이름에 따라 실제 볼륨명이
# 달라진다(예: kraft_caddy-data) — 하드코딩하지 않고 실행 중인 caddy 컨테이너의
# 마운트 정보에서 실제 볼륨명을 읽어온다.
set -euo pipefail

if [[ -f /etc/kraft/backup.env ]]; then
  # shellcheck source=/dev/null
  source /etc/kraft/backup.env
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="${COMPOSE_PROJECT_DIR:-$(dirname "$SCRIPT_DIR")}"
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.prod.yml}"
COMPOSE_ENV_FILE="${COMPOSE_ENV_FILE:-.env.prod}"
COMPOSE=(docker compose --env-file "$PROJECT_DIR/$COMPOSE_ENV_FILE" -f "$PROJECT_DIR/$COMPOSE_FILE")

BACKUP_DIR="${BACKUP_DIR:-/var/backups/kraft}"
RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-14}"

TIMESTAMP=$(date +%Y%m%d_%H%M%S)
FILENAME="$BACKUP_DIR/kraft_caddy-data_${TIMESTAMP}.tar.gz"

mkdir -p "$BACKUP_DIR"
chmod 700 "$BACKUP_DIR"

CADDY_CONTAINER=$("${COMPOSE[@]}" ps -q caddy)
[[ -n "$CADDY_CONTAINER" ]] || { echo "==> [FAIL] caddy 컨테이너가 실행 중이지 않습니다." >&2; exit 1; }

CADDY_VOLUME=$(docker inspect "$CADDY_CONTAINER" \
  --format '{{ range .Mounts }}{{ if eq .Destination "/data" }}{{ .Name }}{{ end }}{{ end }}')
[[ -n "$CADDY_VOLUME" ]] || { echo "==> [FAIL] caddy 컨테이너에서 /data 볼륨을 찾지 못했습니다." >&2; exit 1; }

echo "==> Backing up volume $CADDY_VOLUME to $FILENAME"
docker run --rm \
  -v "${CADDY_VOLUME}:/data:ro" \
  -v "${BACKUP_DIR}:/backup" \
  alpine:3 \
  tar czf "/backup/$(basename "$FILENAME")" -C /data .

echo "==> Backup complete: $FILENAME ($(du -sh "$FILENAME" | cut -f1))"

# db-backup.sh와 같은 textfile collector 규약이되, 별도 메트릭 이름을 쓴다 —
# DB 백업 성공과 뒤섞이면 "DB는 됐는데 caddy-data는 안 됐다" 같은 상태를 구분할 수 없다.
TEXTFILE_DIR="${NODE_EXPORTER_TEXTFILE_DIR:-/var/lib/node-exporter/textfile}"
if [[ -d "$TEXTFILE_DIR" ]]; then
  BACKUP_SIZE_BYTES=$(stat -c%s "$FILENAME" 2>/dev/null || stat -f%z "$FILENAME")
  TMP_METRICS="$(mktemp "$TEXTFILE_DIR/.kraft_caddy_data_backup.XXXXXX")"
  {
    echo "# HELP kraft_caddy_data_backup_last_success_timestamp_seconds Unix time of the last successful caddy-data backup"
    echo "# TYPE kraft_caddy_data_backup_last_success_timestamp_seconds gauge"
    echo "kraft_caddy_data_backup_last_success_timestamp_seconds $(date +%s)"
    echo "# HELP kraft_caddy_data_backup_last_size_bytes Size of the last successful caddy-data backup file in bytes"
    echo "# TYPE kraft_caddy_data_backup_last_size_bytes gauge"
    echo "kraft_caddy_data_backup_last_size_bytes $BACKUP_SIZE_BYTES"
  } > "$TMP_METRICS"
  mv "$TMP_METRICS" "$TEXTFILE_DIR/kraft_caddy_data_backup.prom"
  echo "==> 백업 메트릭 기록: $TEXTFILE_DIR/kraft_caddy_data_backup.prom"
else
  echo "==> [WARN] $TEXTFILE_DIR 없음 — 백업 메트릭을 노출하지 않습니다(node-exporter textfile collector 미설정)." >&2
fi

# db-backup.sh와 동일한 오프서버 사본 관례 — 같은 BACKUP_REMOTE_DEST를 공유한다.
if [[ -n "${BACKUP_REMOTE_DEST:-}" ]]; then
  if command -v rclone >/dev/null 2>&1; then
    if rclone copy "$FILENAME" "$BACKUP_REMOTE_DEST" --quiet; then
      echo "==> 원격 사본 업로드 완료: $BACKUP_REMOTE_DEST"
    else
      echo "==> [FAIL] 원격 사본 업로드 실패: $BACKUP_REMOTE_DEST" >&2
    fi
  else
    echo "==> [WARN] BACKUP_REMOTE_DEST가 설정됐지만 rclone이 설치되어 있지 않아 원격 사본을 건너뜁니다." >&2
  fi
else
  echo "==> [WARN] BACKUP_REMOTE_DEST 미설정 — 백업이 로컬(서버와 동일 호스트/디스크)에만 저장됩니다." >&2
fi

# Prune old backups
find "$BACKUP_DIR" -name "kraft_caddy-data_*.tar.gz" -mtime +"$RETENTION_DAYS" -delete
echo "==> Pruned backups older than ${RETENTION_DAYS} days"
