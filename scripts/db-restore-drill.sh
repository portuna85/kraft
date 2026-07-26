#!/usr/bin/env bash
# Restoration drill: restores the latest backup into a temporary database
# inside the mariadb container, verifies required tables have rows, then
# drops the temporary database. Validates the backup without touching the
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

BACKUP_DIR="${BACKUP_DIR:-/var/backups/kraft}"
DB_NAME="${MARIADB_DATABASE:-kraft_lotto}"
DRILL_DB="kraft_drill_$$"

REQUIRED_TABLES=(winning_numbers saved_numbers admin_audit_log)

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

FAIL=0
for table in "${REQUIRED_TABLES[@]}"; do
  count=$("${EXEC[@]}" sh -c "MYSQL_PWD=\"\$MARIADB_ROOT_PASSWORD\" mariadb -uroot --batch --skip-column-names -e \"SELECT COUNT(*) FROM $DRILL_DB.$table;\"" 2>/dev/null || echo "-1")
  if [[ "$count" -gt 0 ]]; then
    echo "  OK  $table: $count rows"
  else
    echo "  FAIL $table: $count rows (expected > 0)" >&2
    FAIL=1
  fi
done

DRILL_SECONDS=$(( $(date +%s) - DRILL_START ))
echo "==> RTO(복구 소요 시간): ${DRILL_SECONDS}초"

if [[ $FAIL -eq 0 ]]; then
  echo "==> Drill PASSED"
else
  echo "==> Drill FAILED" >&2
  exit 1
fi
