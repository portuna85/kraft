#!/bin/bash
#
# 운영 서버의 정기 백업. DB 덤프 + 업로드 이미지 + 암호화 키 위치를 같은 시점 스냅샷으로 묶는다 — 셋 중 하나라도 시점이 어긋나거나 빠지면 복구가
# 실패하거나(BackupRestoreRehearsalTest), 회원 이메일을 읽을 수 없거나, 글은 열리는데 이미지만 깨진다.
#
# 이 스크립트는 저장소에만 있다. 실행은 사람이 crontab에 등록해야 한다(예:
#   0 3 * * * /opt/kraft/backup.sh >/dev/null 2>>/opt/kraft/backup.log && /opt/kraft/verify-backup.sh >>/opt/kraft/backup.log 2>&1
# ) — log()가 이미 tee로 backup.log에 쓰므로 표준 출력은 버리고 오류만 붙인다(아니면 모든 줄이 두 번 남는다). 백업 직후 verify-backup.sh로 검증한다.
#
# deploy-apply.sh와 달리 forced command가 아니라 운영자의 일반 접근(또는 cron)으로 돈다. 읽기 전용 덤프·복사뿐이고 서비스 상태는 건드리지 않는다.
set -euo pipefail

# 덤프·매니페스트에는 비밀번호 해시·암호화된 이메일·세션 데이터가 들어 있다. 이 스크립트가 만드는 모든 파일·디렉터리를 소유자만 읽고 쓰게 강제한다.
umask 077

APP_DIR=/opt/kraft/app
UPLOAD_DIR="$APP_DIR/uploads/images"
BACKUP_DIR="$APP_DIR/backups/full"
# BACKUP_DIR 밖에 둔다 — 안에 두면 "오래된 백업 정리"의 ls 목록에 걸려 실제로 보장되는 백업 개수가 하나 줄어든다.
LOCK_FILE="$APP_DIR/backup.lock"
LOG=/opt/kraft/backup.log
# 오래된 백업 정리 기준. 날짜만으로 지우면 며칠 연속 실패하다 한 번 성공했을 때 그 사이 백업이 한꺼번에 지워질 수 있어, 나이와 무관하게 최신 KEEP_MIN개는
# 남기고 그보다 오래된 것 중 KEEP_DAYS를 넘긴 것만 지운다.
KEEP_DAYS=14
KEEP_MIN=7
# fail()이 mkdir 이전(예: .env 누락)에 불릴 수 있으므로 미리 빈 값을 준다(set -u에서 정의되지 않은 변수는 다른 오류로 죽는다).
TMP_DEST=""

log() {
    printf '[%s] %s\n' "$(date -Is)" "$*" | tee -a "$LOG"
}

fail() {
    log "실패: $*"
    rm -rf "$TMP_DEST"
    exit 1
}

# fail()을 거치지 않고 set -e로 죽는 경로에도 임시 디렉터리를 남기지 않는다(mv 뒤에는 TMP_DEST가 없어 완성된 백업을 건드리지 않는다).
trap '[ -n "$TMP_DEST" ] && rm -rf "$TMP_DEST"' EXIT

# .env의 EMAIL_ENCRYPTION_KEY를 되돌릴 수 없는 짧은 식별자로 바꾼다(printf로만 파이프에 흘려 ps에 드러나지 않는다). 항목이 없으면 "unknown"을 남기고
# 계속한다 — 키 식별이 안 된다는 사실도 복구 때 알아야 한다.
key_fingerprint() {
    local line value
    line=$(grep -m1 -E '^(export[[:space:]]+)?EMAIL_ENCRYPTION_KEY=' "$APP_DIR/.env" || true)
    if [ -z "$line" ]; then
        echo unknown
        return
    fi
    value=${line#*=}
    value=${value%$'\r'}
    value=${value#[\"\']}
    value=${value%[\"\']}
    printf '%s' "$value" | sha256sum | cut -c1-16
}

[ -f "$APP_DIR/.env" ] || fail "$APP_DIR/.env 를 찾을 수 없다"

mkdir -p "$BACKUP_DIR"

# deploy-apply.sh의 잠금과 같은 이유다 — 느린 실행이 끝나기 전에 다음 예약 실행이 겹치면 같은 임시 디렉터리를 밟는다. 기다리지 않고(-n) 포기한다.
exec 200>"$LOCK_FILE"
if ! flock -n 200; then
    log "다른 백업이 이미 진행 중이다. 이번 실행은 그대로 종료한다"
    exit 1
fi

# 지난 실행이 잠금 없이(수동 kill 등) 남긴 임시 디렉터리를 먼저 치운다.
STAMP=$(date +%Y%m%d-%H%M%S)
TMP_DEST="$BACKUP_DIR/.tmp-$STAMP"
DEST="$BACKUP_DIR/$STAMP"
find "$BACKUP_DIR" -mindepth 1 -maxdepth 1 -type d -name '.tmp-*' -mtime +1 -print -exec rm -rf {} \; \
    | while read -r stale; do log "지난 실행이 남긴 임시 디렉터리 삭제: $stale"; done
mkdir -p "$TMP_DEST"

log "───── 백업 시작 ($STAMP) ─────"

# 1. DB 덤프. db 이름·root 비밀번호는 호스트 .env를 다시 grep하지 않고 컨테이너에 이미 주입된 환경변수(MARIADB_DATABASE·MARIADB_ROOT_PASSWORD)를
#    쓴다 — 비밀번호가 -p 인자로 ps에 찍히지 않고(MYSQL_PWD는 환경변수일 뿐), grep|cut 파이프가 pipefail에서 조용히 죽는 경로도 없다.
if ! (cd "$APP_DIR" && docker compose --env-file .env exec -T mariadb \
        sh -c 'MYSQL_PWD="$MARIADB_ROOT_PASSWORD" exec mariadb-dump -uroot --single-transaction "$MARIADB_DATABASE"') \
        2>>"$LOG" | gzip > "$TMP_DEST/db.sql.gz"; then
    fail "DB 덤프 실패"
fi
[ -s "$TMP_DEST/db.sql.gz" ] || fail "덤프가 비어 있다"
# gzip 스트림이 중간에 잘리면(덤프 도중 컨테이너 재시작) -s 검사는 통과하므로 압축 무결성까지 검증한다.
gzip -t "$TMP_DEST/db.sql.gz" || fail "덤프 파일이 손상되었다(gzip 무결성 검사 실패) — 잘렸을 수 있다"
log "DB 덤프 완료: $(stat -c%s "$TMP_DEST/db.sql.gz") 바이트(gzip)"

# 2. 업로드 이미지. DB는 --single-transaction으로 일관되지만 tar는 별도 시점에 파일시스템을 훑으므로, 그 짧은 창(수 초)에 이미지가 올라오거나 지워지면
#    post_images와 정확히 같은 순간이 아닐 수 있다. 서비스를 멈추지 않는 한 남는 경합이라 감수한다(완전한 일관성은 쓰기 중지·파일시스템 스냅샷이 필요하다).
if [ -d "$UPLOAD_DIR" ]; then
    tar -czf "$TMP_DEST/uploads.tar.gz" -C "$(dirname "$UPLOAD_DIR")" "$(basename "$UPLOAD_DIR")"
    [ -s "$TMP_DEST/uploads.tar.gz" ] || fail "업로드 이미지 백업이 비어 있다"
    log "업로드 이미지 백업 완료: $(stat -c%s "$TMP_DEST/uploads.tar.gz") 바이트"
else
    log "경고: 업로드 디렉터리($UPLOAD_DIR)가 없다 — 아직 이미지가 없는 새 서버일 수 있다"
fi

# 3. 암호화 키는 이 백업에 복사하지 않는다(평문 사본을 늘리면 유출 표면만 넓어진다). 대신 "이 시점에 쓰인 키가 어느 것인지"를 키 식별자(.env 값의
#    SHA-256 앞 16자리, 원문 복원 불가)로 남긴다 — 복구 때 보관소의 후보 키로 같은 지문을 만들어 대조한다.
KEY_FINGERPRINT=$(key_fingerprint)
{
    echo "백업 시점: $(date -Is)"
    echo "EMAIL_ENCRYPTION_KEY는 이 백업에 포함하지 않는다."
    echo "키 식별자(SHA-256 앞 16자리): $KEY_FINGERPRINT"
    echo "복구 시 이 시점에 운영 중이던 키(위 식별자와 같은 것)를 별도 보관소에서 가져와야 한다."
    echo "DB·업로드·키 셋을 같은 시점(위 백업 시점)으로 맞출 것 — 하나만 최신본을 쓰면 복호화 실패."
} > "$TMP_DEST/KEY-NOTICE.txt"

# 4. manifest — 이 백업이 정확히 무엇을 담았는지 복구 시점에 대조할 근거. 해시는 전송·보관 중 손상을 잡는다.
{
    echo "backup_timestamp=$(date -Is)"
    echo "email_key_fingerprint=$KEY_FINGERPRINT"
    echo "db_sql_gz_bytes=$(stat -c%s "$TMP_DEST/db.sql.gz")"
    echo "db_sql_gz_sha256=$(sha256sum "$TMP_DEST/db.sql.gz" | cut -d' ' -f1)"
    if [ -f "$TMP_DEST/uploads.tar.gz" ]; then
        echo "uploads_tar_bytes=$(stat -c%s "$TMP_DEST/uploads.tar.gz")"
        echo "uploads_tar_sha256=$(sha256sum "$TMP_DEST/uploads.tar.gz" | cut -d' ' -f1)"
    else
        echo "uploads_tar_bytes=absent"
    fi
} > "$TMP_DEST/manifest.txt"

# 5. 임시 디렉터리에서 전부 성공한 뒤에만 최종 이름으로 옮긴다(같은 파일시스템이라 원자적 rename) — 중간에 실패하면 최종 경로에 아무것도 남지 않는다.
mv "$TMP_DEST" "$DEST"

log "───── 백업 완료: $DEST ─────"

# 오래된 백업 정리. 디렉터리 이름이 타임스탬프라 사전식 정렬이 시간순이다. 최신 KEEP_MIN개는 나이와 무관하게 건너뛰고 그보다 오래된 것 중 KEEP_DAYS를 넘긴 것만
# 지운다(ls는 .tmp-*를 나열하지 않는다).
ls -1 "$BACKUP_DIR" | sort -r | tail -n "+$((KEEP_MIN + 1))" | while read -r old; do
    old_dir="$BACKUP_DIR/$old"
    if find "$old_dir" -maxdepth 0 -mtime "+$KEEP_DAYS" 2>/dev/null | grep -q .; then
        rm -rf "$old_dir"
        log "오래된 백업 삭제: $old_dir"
    fi
done
