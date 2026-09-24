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

# 덤프·매니페스트에는 비밀번호 해시·암호화된 이메일·세션 데이터가 들어 있다. 기본 권한
# (보통 0644)이면 같은 서버의 다른 로컬 계정도 읽을 수 있다(개선 보고서 OPS-01) — 이
# 스크립트가 만드는 모든 파일·디렉터리를 소유자만 읽고 쓸 수 있게 강제한다.
umask 077

APP_DIR=/opt/kraft/app
UPLOAD_DIR="$APP_DIR/uploads/images"
BACKUP_DIR="$APP_DIR/backups/full"
# BACKUP_DIR 밖에 둔다(OPS-G1) — 안에 두면 "오래된 백업 정리"의 ls -1 "$BACKUP_DIR" | sort -r
# 목록에 이 파일도 걸려(파일명이 숫자보다 사전식으로 앞선다) KEEP_MIN개 중 하나를 락 파일이
# 차지하게 되어, 실제로 보장되는 백업 개수가 하나 줄어든다.
LOCK_FILE="$APP_DIR/backup.lock"
LOG=/opt/kraft/backup.log
# 오래된 백업을 정리하는 두 기준(아래 "오래된 백업 정리" 참고). 매일 도는 작업이 무한히
# 쌓이면 디스크를 채우지만, 날짜 기준만으로 지우면 며칠 연속 실패하다 한 번 성공했을 때
# 그 사이의 모든 백업이 한꺼번에 지워질 수 있다(개선 보고서 OPS-02) — 그래서 나이와
# 무관하게 최신 KEEP_MIN개는 항상 남기고, 그보다 오래된 것 중에서만 KEEP_DAYS를 넘긴
# 것을 지운다.
KEEP_DAYS=14
KEEP_MIN=7
# fail()이 mkdir 이전(예: .env 누락)에 불릴 수도 있으므로 미리 빈 값을 준다 — set -u에서
# 정의되지 않은 변수를 참조하면 스크립트가 그 자리에서 다른 오류로 죽는다.
TMP_DEST=""

log() {
    printf '[%s] %s\n' "$(date -Is)" "$*" | tee -a "$LOG"
}

fail() {
    log "실패: $*"
    rm -rf "$TMP_DEST"
    exit 1
}

# fail()을 거치지 않고 set -e로 곧바로 죽는 경로(예상하지 못한 명령 실패)에도 임시
# 디렉터리를 남기지 않는다 — fail()의 rm -rf와 겹쳐도 이미 지워진 경로를 다시 지우는
# 것뿐이라 안전하다. mv로 최종 경로에 옮긴 뒤에는 TMP_DEST 자체가 더 이상 존재하지 않으므로
# 이 트랩이 완성된 백업을 건드리지 않는다.
trap '[ -n "$TMP_DEST" ] && rm -rf "$TMP_DEST"' EXIT

[ -f "$APP_DIR/.env" ] || fail "$APP_DIR/.env 를 찾을 수 없다"

mkdir -p "$BACKUP_DIR"

# deploy-apply.sh의 잠금과 같은 이유다 — 느린 실행이 아직 끝나지 않았는데 다음 예약 실행이
# 겹치면 서로 다른 백업이 같은 임시 디렉터리를 밟을 수 있다. 기다리지 않고(-n) 즉시 포기한다.
exec 200>"$LOCK_FILE"
if ! flock -n 200; then
    log "다른 백업이 이미 진행 중이다. 이번 실행은 그대로 종료한다"
    exit 1
fi

# 지난 실행이 이 잠금 없이(수동 kill 등으로) 중단되어 남긴 임시 디렉터리가 있으면 먼저
# 치운다 — 그렇지 않으면 아래 mkdir가 기존 내용과 섞인다.
STAMP=$(date +%Y%m%d-%H%M%S)
TMP_DEST="$BACKUP_DIR/.tmp-$STAMP"
DEST="$BACKUP_DIR/$STAMP"
find "$BACKUP_DIR" -mindepth 1 -maxdepth 1 -type d -name '.tmp-*' -mtime +1 -print -exec rm -rf {} \; \
    | while read -r stale; do log "지난 실행이 남긴 임시 디렉터리 삭제: $stale"; done
mkdir -p "$TMP_DEST"

log "───── 백업 시작 ($STAMP) ─────"

# 1. DB 덤프. db 이름·root 비밀번호 모두 호스트 .env를 다시 grep하지 않고, 이미 컨테이너
#    자신에게 주입되어 있는 환경변수(docker-compose.yml의 MARIADB_DATABASE·
#    MARIADB_ROOT_PASSWORD)를 그대로 쓴다. 두 가지를 함께 얻는다 — (1) 비밀번호를 이
#    스크립트의 -p 인자로 넘기지 않으므로 호스트·컨테이너 어느 쪽 ps에도 평문이 찍히지
#    않는다(MYSQL_PWD는 해당 프로세스의 환경변수일 뿐 인자가 아니다. 개선 보고서 OPS-01).
#    (2) .env를 grep|cut으로 다시 읽지 않으므로, 그 파이프가 set -o pipefail 아래에서
#    실패해 조용히 스크립트가 죽는 경로 자체가 없다(개선 보고서 OPS-01).
if ! (cd "$APP_DIR" && docker compose --env-file .env exec -T mariadb \
        sh -c 'MYSQL_PWD="$MARIADB_ROOT_PASSWORD" exec mariadb-dump -uroot --single-transaction "$MARIADB_DATABASE"') \
        2>>"$LOG" | gzip > "$TMP_DEST/db.sql.gz"; then
    fail "DB 덤프 실패"
fi
[ -s "$TMP_DEST/db.sql.gz" ] || fail "덤프가 비어 있다"
# gzip 스트림이 중간에 잘리면(예: 덤프 도중 컨테이너 재시작) -s(비어 있지 않음) 검사는
# 통과하지만 압축 무결성은 깨져 있다(OPS-G4) — 압축까지 마친 뒤 그 자체를 검증한다.
gzip -t "$TMP_DEST/db.sql.gz" || fail "덤프 파일이 손상되었다(gzip 무결성 검사 실패) — 잘렸을 수 있다"
log "DB 덤프 완료: $(stat -c%s "$TMP_DEST/db.sql.gz") 바이트(gzip)"

# 2. 업로드 이미지. --single-transaction으로 DB 쪽은 일관된 스냅샷이지만, 이 tar는 별도
#    시점에 파일시스템을 그대로 훑는다 — 그 사이(짧은 창)에 이미지가 새로 올라오거나
#    지워지면 DB의 post_images 행과 완벽히 같은 순간의 파일 목록이라는 보장은 없다.
#    이 스크립트는 서비스를 멈추지 않으므로 이 경합은 구조적으로 남아 있다. 실무에서는
#    미미한 창(수 초 내 tar)이라 감수하고, 완전한 일관성이 필요하면 배포 스냅샷처럼
#    서비스를 멈추고 떠야 한다(쓰기 일시 중지·파일시스템 스냅샷 등 완전한 재설계는 이번
#    변경의 범위 밖이다 — 운영 다운타임 허용치를 정한 뒤 별도로 다룬다).
if [ -d "$UPLOAD_DIR" ]; then
    tar -czf "$TMP_DEST/uploads.tar.gz" -C "$(dirname "$UPLOAD_DIR")" "$(basename "$UPLOAD_DIR")"
    [ -s "$TMP_DEST/uploads.tar.gz" ] || fail "업로드 이미지 백업이 비어 있다"
    log "업로드 이미지 백업 완료: $(stat -c%s "$TMP_DEST/uploads.tar.gz") 바이트"
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
    echo "DB·업로드·키 셋을 같은 시점(위 백업 시점)으로 맞출 것 — 하나만 최신본을 쓰면 복호화 실패."
} > "$TMP_DEST/KEY-NOTICE.txt"

# 4. manifest — 이 백업이 정확히 무엇을 담고 있는지 나중에(복구 시점에) 대조할 근거를
#    남긴다. 해시는 전송·보관 중 손상을 나중에 잡아낼 수 있게 한다.
{
    echo "backup_timestamp=$(date -Is)"
    echo "db_sql_gz_bytes=$(stat -c%s "$TMP_DEST/db.sql.gz")"
    echo "db_sql_gz_sha256=$(sha256sum "$TMP_DEST/db.sql.gz" | cut -d' ' -f1)"
    if [ -f "$TMP_DEST/uploads.tar.gz" ]; then
        echo "uploads_tar_bytes=$(stat -c%s "$TMP_DEST/uploads.tar.gz")"
        echo "uploads_tar_sha256=$(sha256sum "$TMP_DEST/uploads.tar.gz" | cut -d' ' -f1)"
    else
        echo "uploads_tar_bytes=absent"
    fi
} > "$TMP_DEST/manifest.txt"

# 5. 임시 디렉터리에서 전부 성공한 뒤에만 최종 이름으로 옮긴다(원자적 rename, 같은
#    파일시스템 안이므로 mv가 디렉터리 항목 하나만 바꾼다) — 중간에 실패하면 최종 경로에는
#    아무 것도 남지 않는다. 지금까지는 db.sql 실패만 $DEST를 지웠고, 그 이후 단계(업로드
#    tar·manifest)가 실패하면 최종 경로에 불완전한 백업이 그대로 남았다.
mv "$TMP_DEST" "$DEST"

log "───── 백업 완료: $DEST ─────"

# 오래된 백업 정리. 디렉터리 이름이 타임스탬프(YYYYmmdd-HHMMSS)라 사전식 정렬이 곧
# 시간순 정렬이다. 최신 KEEP_MIN개는 나이와 무관하게 건너뛰고, 그보다 오래된 것 중
# KEEP_DAYS를 넘긴 것만 지운다 — 기본 ls는 .tmp-*(dotfile)를 나열하지 않으므로 진행 중인
# 임시 디렉터리는 애초에 이 목록에 섞이지 않는다.
ls -1 "$BACKUP_DIR" | sort -r | tail -n "+$((KEEP_MIN + 1))" | while read -r old; do
    old_dir="$BACKUP_DIR/$old"
    if find "$old_dir" -maxdepth 0 -mtime "+$KEEP_DAYS" 2>/dev/null | grep -q .; then
        rm -rf "$old_dir"
        log "오래된 백업 삭제: $old_dir"
    fi
done
