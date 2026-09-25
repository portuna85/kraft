#!/bin/bash
#
# backup.sh가 만든 백업 하나를 복구에 쓰기 전에(또는 정기 점검으로) 검증한다
# (평가 보고서 2026-09-25 F05).
#
#   verify-backup.sh [백업 디렉터리]
#
# 인자가 없으면 /opt/kraft/app/backups/full 아래 가장 최근 백업을 본다. 읽기만 한다 — DB·
# 업로드 디렉터리·서비스는 건드리지 않는다. 확인하는 것:
#   - manifest.txt의 크기·SHA-256이 실제 파일과 같은지(보관·전송 중 손상, 다른 백업과 섞임)
#   - db.sql.gz의 gzip 무결성, uploads.tar.gz의 tar 목록을 끝까지 읽을 수 있는지
#   - KEY-NOTICE.txt와 키 식별자가 있는지(어느 키로 복호화해야 하는지)
# 백업 파일이 "열리는지"까지만 본다. 실제로 복원되는지는 격리 환경 복구 리허설로 확인한다
# (deploy/RESTORE.md, BackupRestoreRehearsalTest).
set -euo pipefail

BACKUP_ROOT=/opt/kraft/app/backups/full

if [ $# -ge 1 ]; then
    DIR=$1
else
    # 디렉터리 이름이 YYYYmmdd-HHMMSS라 사전식 정렬이 곧 시간순이다(.tmp-*는 ls가 숨긴다).
    latest=$(ls -1 "$BACKUP_ROOT" 2>/dev/null | sort -r | head -n 1 || true)
    [ -n "$latest" ] || { echo "실패: $BACKUP_ROOT 에 백업이 없다" >&2; exit 1; }
    DIR="$BACKUP_ROOT/$latest"
fi

errors=0
ok() { printf '  ok   %s\n' "$*"; }
bad() { printf '  FAIL %s\n' "$*" >&2; errors=$((errors + 1)); }

# manifest.txt에서 key=value 하나를 읽는다. 없으면 빈 값.
manifest_value() {
    grep -m1 "^$1=" "$DIR/manifest.txt" | cut -d= -f2- || true
}

# 파일의 크기·해시를 manifest와 대조한다.
check_file() {
    local file=$1 key=$2
    local expected_bytes expected_sha actual_bytes actual_sha
    expected_bytes=$(manifest_value "${key}_bytes")
    expected_sha=$(manifest_value "${key}_sha256")
    if [ ! -f "$DIR/$file" ]; then
        bad "$file 이 없다"
        return
    fi
    actual_bytes=$(stat -c%s "$DIR/$file")
    actual_sha=$(sha256sum "$DIR/$file" | cut -d' ' -f1)
    if [ "$actual_bytes" != "$expected_bytes" ]; then
        bad "$file 크기 불일치 (manifest=$expected_bytes, 실제=$actual_bytes)"
    elif [ "$actual_sha" != "$expected_sha" ]; then
        bad "$file SHA-256 불일치 — 손상되었거나 다른 백업의 파일이다"
    else
        ok "$file 크기·SHA-256 일치"
    fi
}

echo "백업 검증: $DIR"
[ -d "$DIR" ] || { echo "실패: 디렉터리가 없다" >&2; exit 1; }
[ -f "$DIR/manifest.txt" ] || { echo "실패: manifest.txt 가 없다 — backup.sh가 끝까지 돌지 않은 백업이다" >&2; exit 1; }

check_file db.sql.gz db_sql_gz
if [ -f "$DIR/db.sql.gz" ]; then
    if gzip -t "$DIR/db.sql.gz" 2>/dev/null; then ok "db.sql.gz gzip 무결성"; else bad "db.sql.gz gzip 무결성 실패(잘렸을 수 있다)"; fi
fi

if [ "$(manifest_value uploads_tar_bytes)" = absent ]; then
    ok "업로드 이미지 없음(백업 당시 업로드 디렉터리가 없었다고 manifest에 기록됨)"
else
    check_file uploads.tar.gz uploads_tar
    if [ -f "$DIR/uploads.tar.gz" ]; then
        if entries=$(tar -tzf "$DIR/uploads.tar.gz" 2>/dev/null | wc -l); then
            ok "uploads.tar.gz 목록 읽기 ($entries 항목)"
        else
            bad "uploads.tar.gz 를 끝까지 읽지 못했다"
        fi
    fi
fi

fingerprint=$(manifest_value email_key_fingerprint)
if [ ! -f "$DIR/KEY-NOTICE.txt" ]; then
    bad "KEY-NOTICE.txt 가 없다"
elif [ -z "$fingerprint" ]; then
    # 키 식별자를 남기기 전 버전의 backup.sh가 만든 백업. 실패로 치지 않되 알린다.
    ok "KEY-NOTICE.txt 있음 (키 식별자 없음 — 이전 버전 백업. 백업 시점으로 키를 골라야 한다)"
elif [ "$fingerprint" = unknown ]; then
    bad "키 식별자가 unknown — 백업 당시 .env에서 EMAIL_ENCRYPTION_KEY를 찾지 못했다"
else
    ok "키 식별자: $fingerprint (보관소의 키와 대조: printf '%s' \"\$KEY\" | sha256sum | cut -c1-16)"
fi

if [ "$errors" -gt 0 ]; then
    echo "검증 실패: $errors 건" >&2
    exit 1
fi
echo "검증 통과"
