#!/usr/bin/env bash
# Creates a gzip-compressed mariadb-dump of the kraft_lotto database.
# The dump is written to $BACKUP_DIR (default: /var/backups/kraft).
#
# The mariadb container only listens on the internal `app` Docker network
# (docker-compose.prod.yml has no `ports:` mapping for it), so the dump is
# taken via `docker compose exec` using the container's own MARIADB_*
# credentials rather than a host TCP connection.
set -euo pipefail

# 원격 백업 사본 설정(BACKUP_REMOTE_DEST, BACKUP_MODE, rclone 자격증명 등)은 레포에
# 커밋하지 않고 배포 서버의 /etc/kraft/backup.env에 둔다. 파일이 있으면 자동으로 불러온다.
if [[ -f /etc/kraft/backup.env ]]; then
  # shellcheck source=/dev/null
  source /etc/kraft/backup.env
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="${COMPOSE_PROJECT_DIR:-$(dirname "$SCRIPT_DIR")}"
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.prod.yml}"
COMPOSE_ENV_FILE="${COMPOSE_ENV_FILE:-.env.prod}"
COMPOSE=(docker compose --env-file "$PROJECT_DIR/$COMPOSE_ENV_FILE" -f "$PROJECT_DIR/$COMPOSE_FILE")

# shellcheck source=/dev/null
source "$SCRIPT_DIR/lib/backup-common.sh"

# H-06: local-only(기본, 개발/단일 서버 초기 설정) | offsite-required(prod에서 준비되면
# /etc/kraft/backup.env에서 명시적으로 켠다). offsite-required에서는 원격 업로드 실패가
# 이 스크립트 자체의 실패(exit 1)로 승격되어 cron 실패 감시로도 잡힌다.
BACKUP_MODE="$(backup_validate_mode)"

BACKUP_DIR="${BACKUP_DIR:-/var/backups/kraft}"
DB_NAME="${MARIADB_DATABASE:-kraft_lotto}"
RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-14}"
TEXTFILE_DIR="${NODE_EXPORTER_TEXTFILE_DIR:-/var/lib/node-exporter/textfile}"

TIMESTAMP=$(date +%Y%m%d_%H%M%S)
FILENAME="$BACKUP_DIR/kraft_${DB_NAME}_${TIMESTAMP}.sql.gz"

mkdir -p "$BACKUP_DIR"
chmod 700 "$BACKUP_DIR"

# 시도됨 상태를 먼저 0으로 기록한다 — 이 이후 어느 지점에서 스크립트가 죽어도(덤프 실패,
# gzip 실패 등) 이전 실행이 남긴 성공(1) 값이 그대로 남아있는 거짓 양성을 만들지 않는다.
# 성공 지점에서만 1로 덮어쓴다.
backup_write_metrics "$TEXTFILE_DIR" "kraft_backup_status" "$(backup_status_metric_body "kraft_backup" 0)"
if [[ "$BACKUP_MODE" == "offsite-required" ]]; then
  backup_write_metrics "$TEXTFILE_DIR" "kraft_backup_remote_status" "$(backup_remote_status_metric_body "kraft_backup" 0)"
fi

# 덤프가 중간에 실패하면(옵션 오류, 디스크 부족, 커넥션 끊김) 리다이렉션이 이미 만들어 둔
# 잘린 .sql.gz가 남는다 — 보존 기간 안에서는 그게 정상 백업처럼 보여 "복구 지점이 있다"는
# 착각을 만든다. 성공 지점에서 trap을 해제하므로, 실패 경로에서만 파일을 지운다.
trap 'rm -f "$FILENAME"' ERR

echo "==> Backing up $DB_NAME to $FILENAME"
# --set-gtid-purged는 MySQL 전용 옵션이라 mariadb-dump가
# `unknown variable 'set-gtid-purged=OFF'`로 즉시 죽는다(운영 MariaDB 11.7.2에서 확인).
# 이 옵션이 붙어 있는 한 이 스크립트는 단 한 번도 성공할 수 없었다 — cron이 등록돼
# 있었더라도 백업 파일은 생기지 않았다는 뜻이다.
# MariaDB는 기본적으로 GTID 관련 구문을 덤프에 넣지 않고(넣으려면 명시적으로 --gtid를
# 줘야 한다) 그 기본 동작이 곧 MySQL의 set-gtid-purged=OFF와 같으므로, 옵션을 제거하는
# 것만으로 원래 의도(다른 서버로 복원 가능한 덤프)가 그대로 유지된다.
# shellcheck disable=SC2016 # 작은따옴표가 의도적 — 컨테이너 안 sh -c에서 그 환경(mariadb)의
# $MARIADB_PASSWORD 등으로 전개돼야 하며, 이 스크립트의 셸에서 전개되면 안 된다.
"${COMPOSE[@]}" exec -T mariadb sh -c \
  'MYSQL_PWD="$MARIADB_PASSWORD" mariadb-dump --single-transaction --routines --triggers -u"$MARIADB_USER" "$MARIADB_DATABASE"' \
  | gzip -9 > "$FILENAME"

trap - ERR

echo "==> Backup complete: $FILENAME ($(du -sh "$FILENAME" | cut -f1))"

# node-exporter textfile collector로 백업 성공 시각/크기를 노출한다(Prometheus의
# KraftBackupStale/KraftBackupFailed alert가 이 값을 참조).
BACKUP_SIZE_BYTES=$(stat -c%s "$FILENAME" 2>/dev/null || stat -f%z "$FILENAME")
backup_write_metrics "$TEXTFILE_DIR" "kraft_backup" "$(cat <<EOF
# HELP kraft_backup_last_success_timestamp_seconds Unix time of the last successful DB backup
# TYPE kraft_backup_last_success_timestamp_seconds gauge
kraft_backup_last_success_timestamp_seconds $(date +%s)
# HELP kraft_backup_last_size_bytes Size of the last successful DB backup file in bytes
# TYPE kraft_backup_last_size_bytes gauge
kraft_backup_last_size_bytes $BACKUP_SIZE_BYTES
EOF
)"
backup_write_metrics "$TEXTFILE_DIR" "kraft_backup_status" "$(backup_status_metric_body "kraft_backup" 1)"

# 단일 서버/볼륨 장애에 대비한 오프서버 사본. BACKUP_MODE=offsite-required면 실패 시
# 이 스크립트가 exit 1로 종료된다(backup_upload_remote 내부에서 처리).
backup_upload_remote "$FILENAME" "$BACKUP_MODE" "kraft_backup" "$TEXTFILE_DIR"

# Prune old backups
find "$BACKUP_DIR" -name "kraft_${DB_NAME}_*.sql.gz" -mtime +"$RETENTION_DAYS" -delete
echo "==> Pruned backups older than ${RETENTION_DAYS} days"
