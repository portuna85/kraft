#!/usr/bin/env bash
# Restoration drill: restores the latest backup into a temporary database
# inside the mariadb container, verifies schema/data integrity, then drops
# the temporary database. Validates the backup without touching the
# production database.
#
# The mariadb container only listens on the internal `app` Docker network,
# so every step runs via `docker compose exec` using the container's own
# MARIADB_ROOT_PASSWORD rather than a host TCP connection.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="${COMPOSE_PROJECT_DIR:-$(dirname "$SCRIPT_DIR")}"
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.prod.yml}"
COMPOSE_ENV_FILE="${COMPOSE_ENV_FILE:-.env.prod}"
COMPOSE=(docker compose --env-file "$PROJECT_DIR/$COMPOSE_ENV_FILE" -f "$PROJECT_DIR/$COMPOSE_FILE")
EXEC=("${COMPOSE[@]}" exec -T mariadb)

# shellcheck source=/dev/null
source "$SCRIPT_DIR/lib/backup-common.sh"

BACKUP_DIR="${BACKUP_DIR:-/var/backups/kraft}"
DB_NAME="${MARIADB_DATABASE:-kraft_lotto}"
DRILL_DB="kraft_drill_$$"
TEXTFILE_DIR="${NODE_EXPORTER_TEXTFILE_DIR:-/var/lib/node-exporter/textfile}"

# M-13: winning_numbers는 핵심 데이터라 반드시 row가 있어야 통과다. saved_numbers·
# admin_audit_log는 실사용량이 적으면 정상적으로 0건일 수 있으므로(예: 신규/저활동
# 서버), "row가 있어야 통과"가 아니라 "테이블 자체가 존재하고 스키마가 맞아야 통과"로
# 완화한다 — 예전 기준은 저활동 서버에서 정상 백업도 매번 FAIL을 내고, 반대로 구조가
# 깨진(컬럼이 빠진) 덤프도 row 수만 맞으면 통과시킬 수 있었다.
CORE_TABLES_REQUIRE_ROWS=(winning_numbers)
SCHEMA_ONLY_TABLES=(saved_numbers admin_audit_log)

# GitHub Actions에서 이 드릴을 실행하면 배포 호스트의 textfile collector 디렉터리가
# 없다 — 그 경우 메트릭 기록을 조용히 건너뛴다(H-06과 같은 제약, Pushgateway 등은
# 이번 범위 밖). 로컬/배포 호스트에서 수동 실행할 때만 실제로 기록된다.
record_drill_metric() {
  local status="$1"
  if [[ ! -d "$TEXTFILE_DIR" ]]; then
    return 0
  fi
  backup_write_metrics "$TEXTFILE_DIR" "kraft_backup_restore_drill_status" "$(cat <<EOF
# HELP kraft_backup_restore_drill_last_status 1 if the last restore drill succeeded, 0 otherwise
# TYPE kraft_backup_restore_drill_last_status gauge
kraft_backup_restore_drill_last_status ${status}
EOF
)"
  if [[ "$status" == "1" ]]; then
    backup_write_metrics "$TEXTFILE_DIR" "kraft_backup_restore_drill" "$(cat <<EOF
# HELP kraft_backup_restore_drill_last_success_timestamp_seconds Unix time of the last successful restore drill
# TYPE kraft_backup_restore_drill_last_success_timestamp_seconds gauge
kraft_backup_restore_drill_last_success_timestamp_seconds $(date +%s)
EOF
)"
  fi
}

# 시도됨(0) 상태를 먼저 기록 — 스크립트가 도중에 죽어도 이전 성공값이 남는 거짓 양성을
# 만들지 않는다.
record_drill_metric 0

# Find latest backup
LATEST=$(find "$BACKUP_DIR" -name "kraft_${DB_NAME}_*.sql.gz" | sort | tail -1)
if [[ -z "$LATEST" ]]; then
  echo "ERROR: no backup found in $BACKUP_DIR" >&2
  exit 1
fi
echo "==> Drill restore from: $LATEST"

# RPO 추정치: 이 백업 파일이 얼마나 오래됐는지(=장애 시 최악의 경우 잃을 수 있는 데이터 기간).
BACKUP_AGE_SECONDS=$(( $(date +%s) - $(stat -c %Y "$LATEST" 2>/dev/null || stat -f %m "$LATEST") ))
echo "==> RPO(백업 경과 시간): $((BACKUP_AGE_SECONDS / 3600))시간 $(((BACKUP_AGE_SECONDS % 3600) / 60))분"
DRILL_START=$(date +%s)

cleanup() {
  echo "==> Dropping drill database $DRILL_DB"
  "${EXEC[@]}" sh -c "MYSQL_PWD=\"\$MARIADB_ROOT_PASSWORD\" mariadb -uroot -e \"DROP DATABASE IF EXISTS $DRILL_DB;\"" 2>/dev/null || true
}
trap cleanup EXIT

echo "==> Creating drill database $DRILL_DB"
"${EXEC[@]}" sh -c "MYSQL_PWD=\"\$MARIADB_ROOT_PASSWORD\" mariadb -uroot -e \"CREATE DATABASE $DRILL_DB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;\""

echo "==> Restoring dump..."
gunzip -c "$LATEST" | "${EXEC[@]}" sh -c "MYSQL_PWD=\"\$MARIADB_ROOT_PASSWORD\" mariadb -uroot $DRILL_DB"

query() {
  local sql="$1"
  "${EXEC[@]}" sh -c "MYSQL_PWD=\"\$MARIADB_ROOT_PASSWORD\" mariadb -uroot --batch --skip-column-names -e \"$sql\"" 2>/dev/null || echo ""
}

FAIL=0

for table in "${CORE_TABLES_REQUIRE_ROWS[@]}"; do
  count=$(query "SELECT COUNT(*) FROM $DRILL_DB.$table;")
  if [[ -n "$count" && "$count" -gt 0 ]]; then
    echo "  OK  $table: $count rows"
  else
    echo "  FAIL $table: ${count:-조회 실패} rows (핵심 데이터 — 1개 이상 있어야 함)" >&2
    FAIL=1
  fi
done

for table in "${SCHEMA_ONLY_TABLES[@]}"; do
  exists=$(query "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$DRILL_DB' AND table_name='$table';")
  if [[ "$exists" == "1" ]]; then
    count=$(query "SELECT COUNT(*) FROM $DRILL_DB.$table;")
    echo "  OK  $table: 테이블 존재(row ${count:-확인불가}개 — 0개도 정상)"
  else
    echo "  FAIL $table: 테이블이 덤프에 없음(스키마 손상 가능성)" >&2
    FAIL=1
  fi
done

# M-13: Flyway 버전이 배포 중인 스키마와 같은지 확인한다 — 덤프 자체는 row 수 기준으로는
# "정상"으로 보여도, 백업 시점 이후 마이그레이션이 밀려 있으면 실제 장애 복구 시 그
# 간극만큼 스키마가 뒤처진 채로 복원된다.
LIVE_VERSION=$(query "SELECT MAX(version) FROM $DB_NAME.flyway_schema_history WHERE success=1;")
DRILL_VERSION=$(query "SELECT MAX(version) FROM $DRILL_DB.flyway_schema_history WHERE success=1;")
if [[ -n "$LIVE_VERSION" && -n "$DRILL_VERSION" && "$LIVE_VERSION" == "$DRILL_VERSION" ]]; then
  echo "  OK  Flyway 버전 일치: $DRILL_VERSION"
else
  echo "  FAIL Flyway 버전 불일치: 백업=$DRILL_VERSION, 운영=$LIVE_VERSION" >&2
  FAIL=1
fi

DRILL_SECONDS=$(( $(date +%s) - DRILL_START ))
echo "==> RTO(복구 소요 시간): ${DRILL_SECONDS}초"

if [[ $FAIL -eq 0 ]]; then
  echo "==> Drill PASSED"
  record_drill_metric 1
else
  echo "==> Drill FAILED" >&2
  exit 1
fi
