#!/bin/bash
#
# 운영 서버에서 새 jar를 받아 교체·재시작하는 스크립트.
#
# GitHub Actions의 deploy 잡이 SSH로 접속할 때 **forced command**로 실행된다
# (~/.ssh/authorized_keys의 command="..." 항목). 배포 키로는 이 스크립트 외에
# 아무것도 실행할 수 없다 — 셸도, 포트 포워딩도 얻지 못한다. 키가 유출돼도
# .env(DB 비밀번호·이메일 암호화 키·SMTP 비밀번호)를 읽을 수 없다는 뜻이다.
#
# jar는 stdin으로 들어온다:  ssh -i 배포키 kraft@서버 "<커밋SHA>" < kraft.jar
#
# **이 파일은 배포로 갱신되지 않는다.** 저장소에서 사람이 직접 복사해 설치한다.
# 배포가 자기 자신을 덮어쓸 수 있으면, 저장소가 뚫렸을 때 이 방어 장치까지
# 함께 갈아치울 수 있기 때문이다.
set -euo pipefail

APP_DIR=/opt/kraft/app
BACKUP_DIR="$APP_DIR/backups"
JAR="$APP_DIR/kraft.jar"
INCOMING="$APP_DIR/kraft.jar.incoming"
PREVIOUS="$APP_DIR/kraft.jar.prev"
LOG=/opt/kraft/deploy.log
HEALTH_URL=http://127.0.0.1:8080/

log() {
    printf '[%s] %s\n' "$(date -Is)" "$*" | tee -a "$LOG"
}

fail() {
    log "실패: $*"
    rm -f "$INCOMING"
    exit 1
}

# 클라이언트가 보낸 문자열(커밋 SHA)은 **절대 실행하지 않는다.** 로그에 남길 용도로만
# 쓰며, 안전한 문자만 남기고 길이도 자른다.
REF=$(printf '%s' "${SSH_ORIGINAL_COMMAND:-unknown}" | tr -cd 'a-zA-Z0-9._/-' | cut -c1-100)
[ -n "$REF" ] || REF=unknown

log "───── 배포 시작 (ref=$REF) ─────"

# 1. jar 수신
cat > "$INCOMING"
SIZE=$(stat -c%s "$INCOMING")
log "수신 완료: ${SIZE} 바이트"

# 2. 검증. 여기서 걸러내지 못하면 깨진 파일로 운영을 재시작하게 된다.
#    unzip -l 출력을 grep -q로 바로 파이프하지 않는다 — grep -q는 첫 매치에서 즉시
#    종료하는데, 목록이 아직 남은 unzip이 닫힌 파이프에 쓰다 SIGPIPE로 죽으면(종료코드
#    141) set -o pipefail이 이를 파이프 전체의 실패로 본다. grep이 실제로는 찾았어도
#    실패 처리되는 것을 실제로 겪었다 — 출력을 변수로 먼저 다 받고 순수 bash 패턴
#    매칭으로 검사해 파이프 자체를 없앤다.
[ "$SIZE" -gt 1000000 ] || fail "받은 파일이 너무 작다(${SIZE} 바이트). jar가 아니다"
[ "$(head -c2 "$INCOMING")" = "PK" ] || fail "ZIP 시그니처가 없다. jar가 아니다"
UNZIP_LISTING=$(unzip -l "$INCOMING" 2>/dev/null) || fail "압축이 깨졌다"
[[ "$UNZIP_LISTING" == *"BOOT-INF/"* ]] || fail "Spring Boot 실행 jar가 아니다"
log "검증 통과"

# 3. DB 스냅샷.
#    운영 프로파일은 Flyway가 앱 기동 시 마이그레이션을 자동 적용한다
#    (application-prod.yml). 즉 재시작이 곧 스키마 변경일 수 있고, 그것은
#    jar를 되돌려도 **되돌아가지 않는다.** 되돌릴 수 없는 일 직전에 스냅샷을 남긴다.
#    업로드 이미지는 배포로 바뀌지 않으므로 여기서는 DB만 뜬다(전체 백업은 매일 도는
#    backup.sh가 맡는다).
STAMP=$(date +%Y%m%d-%H%M%S)
mkdir -p "$BACKUP_DIR"
if (cd "$APP_DIR" && docker compose --env-file .env exec -T mariadb \
        mariadb-dump -u root -p"$(grep '^MARIADB_ROOT_PASSWORD=' .env | cut -d= -f2-)" \
        --single-transaction kraft) > "$BACKUP_DIR/pre-deploy-$STAMP.sql" 2>>"$LOG"; then
    log "배포 전 DB 스냅샷: backups/pre-deploy-$STAMP.sql"
    # 최근 10개만 남긴다.
    ls -1t "$BACKUP_DIR"/pre-deploy-*.sql 2>/dev/null | tail -n +11 | xargs -r rm -f
else
    rm -f "$BACKUP_DIR/pre-deploy-$STAMP.sql"
    log "경고: DB 스냅샷 실패. 마이그레이션이 포함된 배포라면 되돌릴 수단이 없다"
fi

# 4. 교체 (이전 jar는 롤백용으로 남긴다)
if [ -f "$JAR" ]; then
    cp -p "$JAR" "$PREVIOUS"
fi
mv "$INCOMING" "$JAR"
log "jar 교체 완료"

# 5. 재시작 (sudoers에서 이 명령 하나만 비밀번호 없이 허용되어 있다).
#    set -e 아래에서 이 명령 자체가 실패하면(예: sudoers 설정 문제) 바로 스크립트가
#    죽어 7번 롤백까지 못 가게 된다. if로 감싸 실패도 "재시작 실패"로 처리되게 한다.
healthy=0
if sudo /usr/bin/systemctl restart kraft; then
    log "재시작 요청됨. 기동을 기다린다"
    # 6. 헬스체크. 최대 60초(2초 × 30회) 기다린다.
    for _ in $(seq 1 30); do
        sleep 2
        if curl -fsS -o /dev/null --max-time 3 "$HEALTH_URL" 2>/dev/null; then
            healthy=1
            break
        fi
    done
else
    log "systemctl restart 자체가 실패했다(sudoers 설정을 확인할 것)"
fi

if [ "$healthy" = 1 ]; then
    log "───── 배포 성공 (ref=$REF) ─────"
    exit 0
fi

# 7. 롤백. jar만 되돌아간다는 점을 분명히 남긴다.
log "헬스체크 실패(60초). 이전 jar로 되돌린다"
if [ -f "$PREVIOUS" ]; then
    mv "$PREVIOUS" "$JAR"
    sudo /usr/bin/systemctl restart kraft
    log "롤백 완료 — 다만 **DB 마이그레이션은 되돌아가지 않았다.**"
    log "스키마를 바꾸는 배포였다면 backups/pre-deploy-$STAMP.sql 로 사람이 판단해 복구해야 한다"
else
    log "되돌릴 이전 jar가 없다. journalctl -u kraft -n 50 으로 원인을 확인할 것"
fi
exit 1
