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

# 배포 전 DB 스냅샷(아래 3번)에는 비밀번호 해시·암호화된 이메일이 들어 있다. 기본 권한
# (보통 0644)이면 같은 서버의 다른 로컬 계정도 읽을 수 있다(개선 보고서 OPS-01) — 이
# 스크립트가 만드는 모든 파일을 소유자만 읽고 쓸 수 있게 강제한다.
umask 077

APP_DIR=/opt/kraft/app
BACKUP_DIR="$APP_DIR/backups"
JAR="$APP_DIR/kraft.jar"
INCOMING="$APP_DIR/kraft.jar.incoming"
PREVIOUS="$APP_DIR/kraft.jar.prev"
LOCK_FILE="$APP_DIR/deploy.lock"
LOG=/opt/kraft/deploy.log
HEALTH_URL=http://127.0.0.1:8080/
# 정상 jar보다 훨씬 넉넉한 수신 상한(아래 1번) — 손상되었거나 다른 목적의 대용량 전송이
# 디스크를 무한정 채우지 않게 한다.
MAX_JAR_SIZE=524288000  # 500MB

log() {
    printf '[%s] %s\n' "$(date -Is)" "$*" | tee -a "$LOG"
}

fail() {
    log "실패: $*"
    rm -f "$INCOMING"
    exit 1
}

# fail()을 거치지 않고 set -e로 곧바로 죽는 경로에도 수신 중이던 파일을 남기지 않는다 —
# jar 교체(mv "$INCOMING" "$JAR") 뒤에는 $INCOMING 자체가 더 이상 존재하지 않으므로 이
# 트랩은 그 이후 아무 것도 지우지 않는다. fail()의 rm -f와 겹쳐도 이미 지워진 경로를 다시
# 지우는 것뿐이라 안전하다.
trap 'rm -f "$INCOMING"' EXIT

# GitHub Actions의 concurrency 설정이 그 워크플로 안에서의 동시 실행은 막아 주지만, 이
# 스크립트 자체를 다른 경로(수동 SSH 재접속 등)로 동시에 두 번 부르는 것까지는 막지 못한다.
# 같은 $INCOMING·$JAR 경로에 두 배포가 동시에 쓰면 서로의 파일을 밟는다. 잠금을 얻지
# 못하면(-n, 기다리지 않고 즉시 실패) 뒤의 배포를 안전하게 포기시킨다.
exec 200>"$LOCK_FILE"
if ! flock -n 200; then
    log "다른 배포가 이미 진행 중이다. 이번 요청은 그대로 종료한다"
    exit 1
fi

# 클라이언트가 보낸 문자열(커밋 SHA)은 **절대 실행하지 않는다.** 로그에 남길 용도로만
# 쓰며, 안전한 문자만 남기고 길이도 자른다.
REF=$(printf '%s' "${SSH_ORIGINAL_COMMAND:-unknown}" | tr -cd 'a-zA-Z0-9._/-' | cut -c1-100)
[ -n "$REF" ] || REF=unknown

log "───── 배포 시작 (ref=$REF) ─────"

# 1. jar 수신. 상한을 넘는 바이트는 애초에 디스크에 받지 않는다 — 예전에는 cat으로 전부
#    받은 뒤에야 크기를 검사해서, 손상되었거나 악의적인 대용량 전송이 그 사이 디스크를
#    가득 채울 수 있었다(개선 보고서 OPS-01). head -c는 상한+1바이트만 받고 나머지는
#    버린다(+1은 "정확히 상한"과 "상한을 넘음"을 아래 크기 검사로 구분하기 위함).
head -c $((MAX_JAR_SIZE + 1)) > "$INCOMING"
SIZE=$(stat -c%s "$INCOMING")
log "수신 완료: ${SIZE} 바이트"

# 2. 검증. 여기서 걸러내지 못하면 깨진 파일로 운영을 재시작하게 된다.
#    unzip -l 출력을 grep -q로 바로 파이프하지 않는다 — grep -q는 첫 매치에서 즉시
#    종료하는데, 목록이 아직 남은 unzip이 닫힌 파이프에 쓰다 SIGPIPE로 죽으면(종료코드
#    141) set -o pipefail이 이를 파이프 전체의 실패로 본다. grep이 실제로는 찾았어도
#    실패 처리되는 것을 실제로 겪었다 — 출력을 변수로 먼저 다 받고 순수 bash 패턴
#    매칭으로 검사해 파이프 자체를 없앤다.
[ "$SIZE" -gt 1000000 ] || fail "받은 파일이 너무 작다(${SIZE} 바이트). jar가 아니다"
[ "$SIZE" -le "$MAX_JAR_SIZE" ] \
    || fail "받은 파일이 너무 크다(${SIZE} 바이트, 상한 ${MAX_JAR_SIZE}). 전송이 잘못되었을 수 있다"
[ "$(head -c2 "$INCOMING")" = "PK" ] || fail "ZIP 시그니처가 없다. jar가 아니다"
UNZIP_LISTING=$(unzip -l "$INCOMING" 2>/dev/null) || fail "압축이 깨졌다"
[[ "$UNZIP_LISTING" == *"BOOT-INF/"* ]] || fail "Spring Boot 실행 jar가 아니다"
# unzip -l은 중앙 디렉터리 목차만 읽는다 — 항목 이름은 멀쩡해 보여도 각 파일의 압축 데이터
# 자체가 전송 중 깨졌을 수 있다(개선 보고서 "배포 스크립트의 검증·재시도 공백"). -t는 모든
# 항목을 실제로 풀어 CRC를 대조하므로 그런 손상까지 여기서 걸러낸다.
unzip -t "$INCOMING" >/dev/null 2>&1 || fail "CRC 무결성 검사 실패 — 항목이 손상되었다"
log "검증 통과"

# 3. DB 스냅샷.
#    운영 프로파일은 Flyway가 앱 기동 시 마이그레이션을 자동 적용한다
#    (application-prod.yml). 즉 재시작이 곧 스키마 변경일 수 있고, 그것은
#    jar를 되돌려도 **되돌아가지 않는다.** 되돌릴 수 없는 일 직전에 스냅샷을 남긴다.
#    업로드 이미지는 배포로 바뀌지 않으므로 여기서는 DB만 뜬다(전체 백업은 매일 도는
#    backup.sh가 맡는다).
STAMP=$(date +%Y%m%d-%H%M%S)
mkdir -p "$BACKUP_DIR"
# db 이름·root 비밀번호 모두 호스트 .env를 grep하지 않고, 컨테이너 자신에게 이미 주입되어
# 있는 환경변수(docker-compose.yml의 MARIADB_DATABASE·MARIADB_ROOT_PASSWORD)를 그대로
# 쓴다 — backup.sh와 같은 이유다(개선 보고서 OPS-01). 비밀번호가 이 스크립트의 -p 인자로
# 넘어가지 않아 ps에 노출되지 않고, .env를 다시 읽는 grep|cut 파이프가 pipefail 아래에서
# 조용히 실패하는 경로도 없앤다.
if (cd "$APP_DIR" && docker compose --env-file .env exec -T mariadb \
        sh -c 'MYSQL_PWD="$MARIADB_ROOT_PASSWORD" exec mariadb-dump -uroot --single-transaction "$MARIADB_DATABASE"') \
        2>>"$LOG" | gzip > "$BACKUP_DIR/pre-deploy-$STAMP.sql.gz" \
        && gzip -t "$BACKUP_DIR/pre-deploy-$STAMP.sql.gz"; then
    log "배포 전 DB 스냅샷: backups/pre-deploy-$STAMP.sql.gz"
    # 최근 10개만 남긴다.
    ls -1t "$BACKUP_DIR"/pre-deploy-*.sql.gz 2>/dev/null | tail -n +11 | xargs -r rm -f
else
    rm -f "$BACKUP_DIR/pre-deploy-$STAMP.sql.gz"
    # 예전에는 경고만 남기고 jar 교체·재시작을 그대로 진행했다 — 마이그레이션이 포함된
    # 배포에서 스키마가 바뀌었는데 되돌릴 백업이 없는 상태로 넘어갈 수 있었다. 백업 없는
    # 배포보다는 배포 자체를 멈추는 쪽이 안전하다. jar는 아직 교체 전이므로 여기서 멈춰도
    # 운영에는 영향이 없다 — 기존 jar가 계속 돈다.
    fail "DB 스냅샷 실패. 마이그레이션이 포함된 배포라면 되돌릴 수단이 없으므로 jar 교체 전에 멈춘다"
fi

# 4. 교체 (이전 jar는 롤백용으로 남긴다)
if [ -f "$JAR" ]; then
    cp -p "$JAR" "$PREVIOUS"
fi
mv "$INCOMING" "$JAR"
log "jar 교체 완료"

# 헬스체크 대기 루프. 롤백 뒤 복구를 확인할 때도 같은 기준으로 다시 쓴다 — 롤백이라고
# 기준을 낮추면 "복구됐다고 기록했지만 실제로는 응답하지 않는 jar"를 성공으로 잘못 남긴다.
# 최대 2초(sleep) + 3초(curl 타임아웃) = 5초 × 30회, 즉 이 루프 자체는 최악의 경우 약
# 150초를 쓴다(응답이 즉시 오면 그 회차에서 바로 끝난다 — "최대 60초"라던 예전 계산은
# curl 자체의 대기 시간을 빠뜨렸다).
wait_for_health() {
    local start
    start=$(date +%s)
    for _ in $(seq 1 30); do
        sleep 2
        if curl -fsS -o /dev/null --max-time 3 "$HEALTH_URL" 2>/dev/null; then
            log "헬스체크 통과 (경과 $(( $(date +%s) - start ))초)"
            return 0
        fi
    done
    log "헬스체크 실패 (경과 $(( $(date +%s) - start ))초, 응답 없음)"
    return 1
}

# 5. 재시작 (sudoers에서 이 명령 하나만 비밀번호 없이 허용되어 있다).
#    set -e 아래에서 이 명령 자체가 실패하면(예: sudoers 설정 문제) 바로 스크립트가
#    죽어 7번 롤백까지 못 가게 된다. if로 감싸 실패도 "재시작 실패"로 처리되게 한다.
healthy=0
if sudo /usr/bin/systemctl restart kraft; then
    log "재시작 요청됨. 기동을 기다린다"
    # 6. 헬스체크. 실제 최악 대기는 이 루프의 약 150초뿐 아니라 재시작 요청·jar 언패킹·
    #    JVM 기동 자체에 걸리는 시간까지 더해진다.
    if wait_for_health; then
        healthy=1
    fi
else
    log "systemctl restart 자체가 실패했다(sudoers 설정을 확인할 것)"
fi

if [ "$healthy" = 1 ]; then
    log "───── 배포 성공 (ref=$REF) ─────"
    exit 0
fi

# 7. 롤백. jar만 되돌아간다는 점을 분명히 남긴다.
log "헬스체크 실패. 이전 jar로 되돌린다"
if [ -f "$PREVIOUS" ]; then
    # 실패한 jar를 그냥 지우지 않고 남겨 둔다 — 왜 헬스체크에 실패했는지 나중에 jar 자체를
    # 들여다볼 수 있어야 한다(개선 보고서 OPS-01).
    mv "$JAR" "$APP_DIR/kraft.jar.failed-$STAMP"
    mv "$PREVIOUS" "$JAR"
    # 예전에는 이 재시작 명령 자체가 실패해도(예: sudoers 설정 문제) set -e로 스크립트가
    # 곧바로 죽어, 아래 "롤백 실패" 로그조차 남기지 못했다. if로 감싸 어떤 경우든 원인을
    # 알 수 있는 로그를 남긴다.
    if sudo /usr/bin/systemctl restart kraft; then
        # 예전에는 여기서 재시작 명령만 내리고 곧바로 "롤백 완료"로 기록했다 — 복원한 jar도
        # 응답하지 않으면(예: 그 jar 자체가 이미 문제였거나 인프라 장애) 실제로는 서비스가
        # 죽어 있는데 로그만 "완료"라고 거짓으로 안심시키는 셈이었다. 같은 헬스체크로 복원도
        # 검증한다.
        if wait_for_health; then
            log "롤백 완료 — 다만 **DB 마이그레이션은 되돌아가지 않았다.**"
            log "스키마를 바꾸는 배포였다면 backups/pre-deploy-$STAMP.sql 로 사람이 판단해 복구해야 한다"
        else
            log "롤백 실패 — 복원한 이전 jar도 응답하지 않는다. journalctl -u kraft -n 50 으로 원인을 확인할 것"
        fi
    else
        log "롤백 재시작 자체가 실패했다(sudoers 설정을 확인할 것). journalctl -u kraft -n 50 으로 원인을 확인할 것"
    fi
else
    log "되돌릴 이전 jar가 없다. journalctl -u kraft -n 50 으로 원인을 확인할 것"
fi
exit 1
