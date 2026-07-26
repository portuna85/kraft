#!/usr/bin/env bash
# Creates a gzip-compressed mariadb-dump of the kraft_lotto database.
# The dump is written to $BACKUP_DIR (default: /var/backups/kraft).
#
# The mariadb container only listens on the internal `app` Docker network
# (docker-compose.prod.yml has no `ports:` mapping for it), so the dump is
# taken via `docker compose exec` using the container's own MARIADB_*
# credentials rather than a host TCP connection.
set -euo pipefail

# 원격 백업 사본 설정(BACKUP_REMOTE_DEST, rclone 자격증명 등)은 레포에 커밋하지 않고
# 배포 서버의 /etc/kraft/backup.env에 둔다. 파일이 있으면 자동으로 불러온다.
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
DB_NAME="${MARIADB_DATABASE:-kraft_lotto}"
RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-14}"

TIMESTAMP=$(date +%Y%m%d_%H%M%S)
FILENAME="$BACKUP_DIR/kraft_${DB_NAME}_${TIMESTAMP}.sql.gz"

mkdir -p "$BACKUP_DIR"
chmod 700 "$BACKUP_DIR"

echo "==> Backing up $DB_NAME to $FILENAME"
# shellcheck disable=SC2016 # 작은따옴표가 의도적 — 컨테이너 안 sh -c에서 그 환경(mariadb)의
# $MARIADB_PASSWORD 등으로 전개돼야 하며, 이 스크립트의 셸에서 전개되면 안 된다.
"${COMPOSE[@]}" exec -T mariadb sh -c \
  'MYSQL_PWD="$MARIADB_PASSWORD" mariadb-dump --single-transaction --routines --triggers --set-gtid-purged=OFF -u"$MARIADB_USER" "$MARIADB_DATABASE"' \
  | gzip -9 > "$FILENAME"

echo "==> Backup complete: $FILENAME ($(du -sh "$FILENAME" | cut -f1))"

# node-exporter textfile collector로 백업 성공 시각/크기를 노출한다(Prometheus의
# KraftBackupStale alert가 이 값을 참조). 디렉터리가 없으면(=collector 미설정) 조용히 건너뛴다.
TEXTFILE_DIR="${NODE_EXPORTER_TEXTFILE_DIR:-/var/lib/node-exporter/textfile}"
if [[ -d "$TEXTFILE_DIR" ]]; then
  BACKUP_SIZE_BYTES=$(stat -c%s "$FILENAME" 2>/dev/null || stat -f%z "$FILENAME")
  TMP_METRICS="$(mktemp "$TEXTFILE_DIR/.kraft_backup.XXXXXX")"
  {
    echo "# HELP kraft_backup_last_success_timestamp_seconds Unix time of the last successful DB backup"
    echo "# TYPE kraft_backup_last_success_timestamp_seconds gauge"
    echo "kraft_backup_last_success_timestamp_seconds $(date +%s)"
    echo "# HELP kraft_backup_last_size_bytes Size of the last successful DB backup file in bytes"
    echo "# TYPE kraft_backup_last_size_bytes gauge"
    echo "kraft_backup_last_size_bytes $BACKUP_SIZE_BYTES"
  } > "$TMP_METRICS"
  mv "$TMP_METRICS" "$TEXTFILE_DIR/kraft_backup.prom"
  echo "==> 백업 메트릭 기록: $TEXTFILE_DIR/kraft_backup.prom"
else
  echo "==> [WARN] $TEXTFILE_DIR 없음 — 백업 메트릭을 노출하지 않습니다(node-exporter textfile collector 미설정)." >&2
fi

# 단일 서버/볼륨 장애에 대비한 오프서버 사본. BACKUP_REMOTE_DEST(예: s3:my-bucket/kraft-backups/)가
# 설정되어 있고 rclone이 설치돼 있을 때만 동작 — 기본값(미설정)이면 기존과 동일하게 로컬에만 보관한다.
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
  echo "==> [WARN] BACKUP_REMOTE_DEST 미설정 — 백업이 로컬(DB와 동일 호스트/볼륨)에만 저장됩니다. 단일 VM/디스크 장애 시 DB와 백업이 함께 유실될 수 있습니다." >&2
fi

# Prune old backups
find "$BACKUP_DIR" -name "kraft_${DB_NAME}_*.sql.gz" -mtime +"$RETENTION_DAYS" -delete
echo "==> Pruned backups older than ${RETENTION_DAYS} days"
