#!/bin/bash
#
# 운영 서버의 정기 백업. DB 덤프 + 업로드 이미지 + 암호화 키 위치를
# 같은 시점 스냅샷으로 묶는다 — 셋 중 하나라도 시점이 어긋나거나 빠지면
# 복구가 실패하거나(BackupRestoreRehearsalTest 참고), 되살려도 회원 이메일을
# 읽을 수 없거나, 글은 열리는데 이미지만 깨진 상태가 된다.
#
# 이 스크립트는 저장소에만 있다. 실행은 사람이 crontab에 등록해야 한다(예:
#   0 3 * * * /opt/kraft/backup.sh >> /opt/kraft/backup.log 2>&1
# ) — 이 파일은 그 등록을 대신하지 않는다.
#
# deploy-apply.sh와 달리 forced command로 묶이지 않는다. 배포 키가 아니라
# 운영자의 일반 접근(또는 cron)으로 돈다는 뜻이므로, 여기서 실행하는 것은
# 읽기 전용 덤프·복사뿐이고 서비스 상태는 건드리지 않는다.
set -euo pipefail

APP_DIR=/opt/kraft/app
UPLOAD_DIR="$APP_DIR/uploads/images"
BACKUP_DIR="$APP_DIR/backups/full"
LOG=/opt/kraft/backup.log
# 최근 며칠치만 남긴다. 매일 도는 작업이 무한히 쌓이면 디스크를 채운다.
KEEP_DAYS=14

log() {
    printf '[%s] %s\n' "$(date -Is)" "$*" | tee -a "$LOG"
}

fail() {
    log "실패: $*"
    exit 1
}

[ -f "$APP_DIR/.env" ] || fail "$APP_DIR/.env 를 찾을 수 없다"

STAMP=$(date +%Y%m%d-%H%M%S)
DEST="$BACKUP_DIR/$STAMP"
mkdir -p "$DEST"

log "───── 백업 시작 ($STAMP) ─────"

# 1. DB 덤프. deploy-apply.sh의 3번과 같은 방식 — MARIADB_DATABASE를 .env에서 읽는다
#    (하드코딩하면 .env에서 db 이름을 바꿨을 때 조용히 엉뚱한 db를 대상으로 하게 된다).
DB_NAME=$(grep '^MARIADB_DATABASE=' "$APP_DIR/.env" | cut -d= -f2-)
[ -n "$DB_NAME" ] || fail ".env에 MARIADB_DATABASE가 없다"
if ! (cd "$APP_DIR" && docker compose --env-file .env exec -T mariadb \
        mariadb-dump -u root -p"$(grep '^MARIADB_ROOT_PASSWORD=' .env | cut -d= -f2-)" \
        --single-transaction "$DB_NAME") > "$DEST/db.sql" 2>>"$LOG"; then
    rm -rf "$DEST"
    fail "DB 덤프 실패"
fi
[ -s "$DEST/db.sql" ] || { rm -rf "$DEST"; fail "덤프가 비어 있다"; }
log "DB 덤프 완료: $(stat -c%s "$DEST/db.sql") 바이트"

# 2. 업로드 이미지. --single-transaction으로 DB 쪽은 일관된 스냅샷이지만, 이 tar는 별도
#    시점에 파일시스템을 그대로 훑는다 — 그 사이(짧은 창)에 이미지가 새로 올라오거나
#    지워지면 DB의 post_images 행과 완벽히 같은 순간의 파일 목록이라는 보장은 없다.
#    이 스크립트는 서비스를 멈추지 않으므로 이 경합은 구조적으로 남아 있다. 실무에서는
#    미미한 창(수 초 내 tar)이라 감수하고, 완전한 일관성이 필요하면 배포 스냅샷처럼
#    서비스를 멈추고 떠야 한다.
if [ -d "$UPLOAD_DIR" ]; then
    tar -czf "$DEST/uploads.tar.gz" -C "$(dirname "$UPLOAD_DIR")" "$(basename "$UPLOAD_DIR")"
    log "업로드 이미지 백업 완료: $(stat -c%s "$DEST/uploads.tar.gz") 바이트"
else
    log "경고: 업로드 디렉터리($UPLOAD_DIR)가 없다 — 아직 이미지가 없는 새 서버일 수 있다"
fi

# 3. 암호화 키는 이 백업에 복사하지 않는다. 키 자체를 평문으로 이곳저곳에 늘리면 유출
#    표면만 넓어진다 — 대신 "지금 이 시점에 쓰인 키가 어디 있는지"만 기록해 둔다.
#    복구할 때는 이 기록을 보고 그 시점의 키 보관소(비밀번호 관리자 등)에서 직접 가져온다.
{
    echo "백업 시점: $(date -Is)"
    echo "EMAIL_ENCRYPTION_KEY는 이 백업에 포함하지 않는다."
    echo "복구 시 이 시점에 운영 중이던 키를 별도 보관소에서 가져와야 한다."
    echo "(README '백업·복구' 절차 참고 — DB·업로드·키 셋을 같은 시점으로 맞출 것)"
} > "$DEST/KEY-NOTICE.txt"

log "───── 백업 완료: $DEST ─────"

# 오래된 백업 정리.
find "$BACKUP_DIR" -mindepth 1 -maxdepth 1 -type d -mtime "+$KEEP_DAYS" -print -exec rm -rf {} \; \
    | while read -r removed; do log "오래된 백업 삭제: $removed"; done
