#!/usr/bin/env bash
# Restores a gzip-compressed mariadb-dump into the kraft_lotto database.
# Usage: db-restore.sh <backup-file.sql.gz>
#
# The mariadb container only listens on the internal `app` Docker network,
# so the restore runs via `docker compose exec` using the container's own
# MARIADB_* credentials rather than a host TCP connection.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="${COMPOSE_PROJECT_DIR:-$(dirname "$SCRIPT_DIR")}"
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.prod.yml}"
COMPOSE_ENV_FILE="${COMPOSE_ENV_FILE:-.env.prod}"
COMPOSE=(docker compose --env-file "$PROJECT_DIR/$COMPOSE_ENV_FILE" -f "$PROJECT_DIR/$COMPOSE_FILE")

BACKUP_FILE="${1:?Usage: db-restore.sh <backup-file.sql.gz>}"
DB_NAME="${MARIADB_DATABASE:-kraft_lotto}"

if [[ ! -f "$BACKUP_FILE" ]]; then
  echo "ERROR: backup file not found: $BACKUP_FILE" >&2
  exit 1
fi

echo "WARNING: This will OVERWRITE the $DB_NAME database in the mariadb container."
read -r -p "Type 'yes' to continue: " confirm
[[ "$confirm" == "yes" ]] || { echo "Aborted."; exit 0; }

echo "==> Restoring $BACKUP_FILE into $DB_NAME ..."
# shellcheck disable=SC2016 # 작은따옴표가 의도적 — 컨테이너 안 sh -c에서 그 환경(mariadb)의
# $MARIADB_PASSWORD 등으로 전개돼야 하며, 이 스크립트의 셸에서 전개되면 안 된다.
gunzip -c "$BACKUP_FILE" | "${COMPOSE[@]}" exec -T mariadb sh -c \
  'MYSQL_PWD="$MARIADB_PASSWORD" mariadb -u"$MARIADB_USER" "$MARIADB_DATABASE"'

echo "==> Restore complete."
