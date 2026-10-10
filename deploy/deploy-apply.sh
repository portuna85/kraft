#!/bin/bash
#
# 운영 서버에서 새 jar를 받아 교체·재시작하는 스크립트.
#
# GitHub Actions의 deploy 잡이 SSH로 접속할 때 forced command로 실행된다(~/.ssh/authorized_keys의 command="..."). 배포 키로는 이
# 스크립트 외에 셸도 포트 포워딩도 얻지 못하므로, 키가 유출돼도 .env(DB 비밀번호·이메일 암호화 키·SMTP 비밀번호)를 읽을 수 없다.
#
# jar는 stdin으로 들어온다:  ssh -i 배포키 kraft@서버 "<커밋SHA>" < kraft.jar
#
# 이 파일은 배포로 갱신되지 않는다 — 사람이 직접 복사해 설치한다(배포가 자기 자신을 덮어쓸 수 있으면 저장소가 뚫릴 때 이 방어 장치까지
# 함께 갈아치워진다).
set -euo pipefail

# 배포 전 DB 스냅샷(아래 3번)에는 비밀번호 해시·암호화된 이메일이 들어 있다. 이 스크립트가 만드는 모든 파일을 소유자만 읽고 쓰게 강제한다(기본 권한 0644면 다른 로컬 계정도 읽는다).
umask 077

APP_DIR=/opt/kraft/app
BACKUP_DIR="$APP_DIR/backups"
JAR="$APP_DIR/kraft.jar"
INCOMING="$APP_DIR/kraft.jar.incoming"
PREVIOUS="$APP_DIR/kraft.jar.prev"
# 배포에 성공한 jar를 커밋 SHA 이름으로 최근 N개 보관한다(.prev는 직전 1세대뿐이다). 수동 롤백:
#   ls -1t /opt/kraft/app/releases/            # 골라서
#   cp -p /opt/kraft/app/releases/<파일> /opt/kraft/app/kraft.jar && sudo systemctl restart kraft
# DB 마이그레이션은 되돌아가지 않으므로 스키마를 바꾼 배포 이후라면 backups/pre-deploy-*.sql.gz를 먼저 확인한다.
RELEASES_DIR="$APP_DIR/releases"
KEEP_RELEASES=5
# OOM 때 JVM이 남기는 힙 덤프(kraft.service의 HeapDumpPath). 복호화된 이메일·비밀 값이 들어 있고 힙 크기만큼 커서 최근 몇 개만 남긴다.
HEAPDUMP_DIR=/opt/kraft/heapdumps
KEEP_HEAPDUMPS=3
# 빌드 타깃 JDK(build.gradle.kts의 toolchain). 운영은 OS의 /usr/bin/java로 실행하므로 서버의 java가 이보다 낮으면 새 jar가 기동하지 못한다.
REQUIRED_JAVA_MAJOR=25
LOCK_FILE="$APP_DIR/deploy.lock"
LOG=/opt/kraft/deploy.log
# /readyz는 컨텍스트 기동과 DB 커넥션 검증(제한 시간 2초)만 본다 — DB에 붙지 못하면 503이다. 이 루프가 재시작마다 최대 30회 반복되므로 템플릿을 렌더하지 않는 가벼운 엔드포인트를 쓴다.
HEALTH_URL=http://127.0.0.1:8080/readyz
LIVENESS_URL=http://127.0.0.1:8080/healthz
# 정상 jar보다 훨씬 넉넉한 수신 상한(아래 1번) — 손상되었거나 다른 목적의 대용량 전송이 디스크를 채우지 않게 한다.
MAX_JAR_SIZE=524288000  # 500MB
# 수신 jar + 롤백용 .prev + 보관본 + DB 스냅샷이 한 번에 디스크에 있게 된다. 최악의 jar 크기 3배(약 1.5GB)가 비어 있지 않으면 쓰다 가득 차기 전에 멈춘다.
MIN_FREE_KB=$(( MAX_JAR_SIZE * 3 / 1024 ))

log() {
    printf '[%s] %s\n' "$(date -Is)" "$*" | tee -a "$LOG"
}

fail() {
    log "실패: $*"
    rm -f "$INCOMING"
    exit 1
}

# GitHub Actions의 concurrency는 그 워크플로 안의 동시 실행만 막는다. 이 스크립트를 다른 경로(수동 SSH 등)로 동시에 부르면 같은 $INCOMING·$JAR를 밟으므로,
# 잠금을 못 얻으면(-n, 기다리지 않고) 뒤의 배포를 포기시킨다.
exec 200>"$LOCK_FILE"
if ! flock -n 200; then
    log "다른 배포가 이미 진행 중이다. 이번 요청은 그대로 종료한다"
    exit 1
fi

# 잠금을 얻은 뒤에만 정리 트랩을 건다 — 잠금 실패로 exit 1하는 경로에서 트랩이 걸려 있으면, 포기한 이 프로세스가 진행 중인 다른 배포의
# $INCOMING(같은 경로)을 지워 버린다. set -e로 곧바로 죽는 경로에도 수신 중이던 파일을 남기지 않는다(jar 교체 뒤에는 $INCOMING이 없어 아무것도 지우지 않는다).
trap 'rm -f "$INCOMING"' EXIT

# 클라이언트가 보낸 문자열(커밋 SHA)은 절대 실행하지 않는다 — 로그용으로만 쓰며 안전한 문자만 남기고 길이도 자른다.
RAW_ARG=$(printf '%s' "${SSH_ORIGINAL_COMMAND:-unknown}" | tr -cd 'a-zA-Z0-9._/-')
# CI는 "<커밋 SHA 40자>-<jar의 SHA-256 64자>"를 보낸다. 앞은 로그·보관 파일 이름용 REF, 뒤는 받은 jar와 대조할 값이다. 모양이 다르면
# (옛 CI·수동 실행) 문자열 전체를 REF로 쓰고 대조는 건너뛴다. 16진수만 받으므로 명령으로 실행될 일은 없다.
EXPECTED_SHA256=""
if [[ "$RAW_ARG" =~ ^([0-9a-f]{40})-([0-9a-f]{64})$ ]]; then
    REF="${BASH_REMATCH[1]}"
    EXPECTED_SHA256="${BASH_REMATCH[2]}"
else
    REF=$(printf '%s' "$RAW_ARG" | cut -c1-100)
fi
[ -n "$REF" ] || REF=unknown

log "───── 배포 시작 (ref=$REF) ─────"

# 1. jar 수신. 상한을 넘는 바이트는 디스크에 받지 않는다(head -c는 상한+1바이트만 받고 버린다 — +1은 "정확히 상한"과 "넘음"을 크기 검사로 구분하려는 것).
FREE_KB=$(df -Pk "$APP_DIR" | awk 'NR==2 {print $4}')
[ "${FREE_KB:-0}" -ge "$MIN_FREE_KB" ]     || fail "디스크 여유가 부족하다(${FREE_KB:-0}KB, 필요 ${MIN_FREE_KB}KB). 배포 전에 정리해야 한다"
head -c $((MAX_JAR_SIZE + 1)) > "$INCOMING"
SIZE=$(stat -c%s "$INCOMING")
log "수신 완료: ${SIZE} 바이트"

# 2. 검증. 여기서 못 거르면 깨진 파일로 운영을 재시작한다. unzip -l을 grep -q로 파이프하지 않는다 — grep -q가 첫 매치에서 끝나면 unzip이
#    SIGPIPE(141)로 죽고 pipefail이 찾았는데도 실패로 본다. 출력을 변수로 받아 bash 패턴 매칭으로 검사한다.
[ "$SIZE" -gt 1000000 ] || fail "받은 파일이 너무 작다(${SIZE} 바이트). jar가 아니다"
[ "$SIZE" -le "$MAX_JAR_SIZE" ] \
    || fail "받은 파일이 너무 크다(${SIZE} 바이트, 상한 ${MAX_JAR_SIZE}). 전송이 잘못되었을 수 있다"
[ "$(head -c2 "$INCOMING")" = "PK" ] || fail "ZIP 시그니처가 없다. jar가 아니다"
UNZIP_LISTING=$(unzip -l "$INCOMING" 2>/dev/null) || fail "압축이 깨졌다"
[[ "$UNZIP_LISTING" == *"BOOT-INF/"* ]] || fail "Spring Boot 실행 jar가 아니다"
# unzip -l은 목차만 읽는다 — 각 항목의 압축 데이터가 전송 중 깨졌을 수 있어 -t로 모든 항목을 풀어 CRC를 대조한다.
unzip -t "$INCOMING" >/dev/null 2>&1 || fail "CRC 무결성 검사 실패 — 항목이 손상되었다"
# 위 검사는 "jar로서 온전한가"만 본다. CI가 만든 바로 그 파일인지는 SHA-256으로 대조한다(전송 중 잘림·변경·다른 아티팩트). 값이 같은 SSH 채널로 오므로
# 배포 키 자체가 유출된 공격자까지 막지는 못한다 — 그건 forced command와 키 보관이 맡는다.
if [ -n "$EXPECTED_SHA256" ]; then
    ACTUAL_SHA256=$(sha256sum "$INCOMING" | cut -d' ' -f1)
    [ "$ACTUAL_SHA256" = "$EXPECTED_SHA256" ]         || fail "jar의 SHA-256이 CI가 알려 준 값과 다르다(받은 ${ACTUAL_SHA256:0:12}…, 기대 ${EXPECTED_SHA256:0:12}…)"
    log "SHA-256 확인: ${ACTUAL_SHA256:0:12}…"
else
    log "경고: CI가 jar의 SHA-256을 알려 주지 않아 대조하지 못했다"
fi
log "검증 통과"

# 2-1. 서버의 JVM이 이 jar를 돌릴 수 있는지(아직 바꾼 게 없어 여기서 멈추면 운영은 그대로다). 버전을 읽지 못하면 막지 않고 경고만 남긴다.
JAVA_SPEC=$(/usr/bin/java -XshowSettings:properties -version 2>&1     | awk -F'= ' '/java.specification.version/ {print $2; exit}' | tr -d ' ' || true)
if [[ "$JAVA_SPEC" =~ ^[0-9]+$ ]]; then
    [ "$JAVA_SPEC" -ge "$REQUIRED_JAVA_MAJOR" ]         || fail "서버의 java가 ${JAVA_SPEC}이다. 이 jar는 ${REQUIRED_JAVA_MAJOR} 이상이 필요하다 — 서버 JDK부터 올려야 한다"
    log "서버 JDK 확인: ${JAVA_SPEC}"
else
    log "경고: 서버 java 버전을 읽지 못했다(${JAVA_SPEC:-빈 값}). 확인 없이 진행한다"
fi

# 3. DB 스냅샷. 재시작이 곧 Flyway 마이그레이션(application-prod.yml)일 수 있고 jar를 되돌려도 스키마는 되돌아가지 않으므로, 되돌릴 수 없는 일 직전에
#    남긴다. 업로드 이미지는 배포로 바뀌지 않아 DB만 뜬다(전체 백업은 매일 도는 backup.sh).
STAMP=$(date +%Y%m%d-%H%M%S)
mkdir -p "$BACKUP_DIR"
# db 이름·root 비밀번호는 호스트 .env를 grep하지 않고 컨테이너에 이미 주입된 환경변수(MARIADB_DATABASE·MARIADB_ROOT_PASSWORD)를 쓴다 — backup.sh와
# 같은 이유로 비밀번호가 -p 인자로 ps에 노출되지 않고, grep|cut 파이프가 pipefail에서 조용히 실패할 일도 없다.
if (cd "$APP_DIR" && docker compose --env-file .env exec -T mariadb \
        sh -c 'MYSQL_PWD="$MARIADB_ROOT_PASSWORD" exec mariadb-dump -uroot --single-transaction "$MARIADB_DATABASE"') \
        2>>"$LOG" | gzip > "$BACKUP_DIR/pre-deploy-$STAMP.sql.gz" \
        && gzip -t "$BACKUP_DIR/pre-deploy-$STAMP.sql.gz"; then
    log "배포 전 DB 스냅샷: backups/pre-deploy-$STAMP.sql.gz"
    # 최근 10개만 남긴다.
    ls -1t "$BACKUP_DIR"/pre-deploy-*.sql.gz 2>/dev/null | tail -n +11 | xargs -r rm -f
else
    rm -f "$BACKUP_DIR/pre-deploy-$STAMP.sql.gz"
    # 백업 없는 배포보다 배포를 멈추는 쪽이 안전하다(마이그레이션이 포함되면 되돌릴 수단이 없다). jar는 아직 교체 전이라 기존 jar가 계속 돈다.
    fail "DB 스냅샷 실패. 마이그레이션이 포함된 배포라면 되돌릴 수단이 없으므로 jar 교체 전에 멈춘다"
fi

# 4. 교체 (이전 jar는 롤백용으로 남긴다)
if [ -f "$JAR" ]; then
    cp -p "$JAR" "$PREVIOUS"
fi
mv "$INCOMING" "$JAR"
log "jar 교체 완료"

# 4-1. AOT 캐시 학습(선택). 새 jar로 컨텍스트만 한 번 띄웠다 닫아 클래스 로딩·링크 결과를 kraft.aot에 남기면 kraft.service가 -XX:AOTCache로 읽어
#      재시작 시간이 줄어든다. 캐시는 jar와 JVM 옵션이 같을 때만 쓰이므로(다르면 JVM이 무시한다) 배포마다 새로 만들고, 실패해도 배포를 막지 않는다.
#      운영 상태를 건드리지 않게 포트는 임의(0), Flyway는 끄고 스키마 검증은 건너뛴다(마이그레이션은 아래 재시작에서 평소처럼 적용된다).
AOT_CACHE="$APP_DIR/kraft.aot"
rm -f "$AOT_CACHE"
if ( set -a; . "$APP_DIR/.env"; set +a
     timeout 180 /usr/bin/java -XX:MaxRAMPercentage=50.0 -XX:AOTCacheOutput="$AOT_CACHE" -Dspring.context.exit=onRefresh \
         -jar "$JAR" --spring.profiles.active=prod --server.port=0 --spring.flyway.enabled=false \
         --spring.jpa.hibernate.ddl-auto=none ) >>"$LOG" 2>&1; then
    log "AOT 캐시 생성: $(du -h "$AOT_CACHE" 2>/dev/null | cut -f1)"
else
    rm -f "$AOT_CACHE"
    log "경고: AOT 캐시를 만들지 못했다. 캐시 없이 기동한다(기동이 조금 느려질 뿐이다)"
fi

# 헬스체크 대기 루프. 롤백 뒤 복구 확인에도 같은 기준을 쓴다(기준을 낮추면 응답하지 않는 jar를 성공으로 남긴다). 최대 1초(sleep) + 3초(curl 타임아웃)
# = 4초 × 40회, 최악 약 160초다.
#
# /readyz가 404면 readiness가 없던 버전의 jar(롤백으로 되살린 경우)라 /healthz로 판정한다 — 아니면 정상 기동한 이전 jar를 롤백 실패로 기록한다. 503(DB 미준비)은 폴백하지 않는다.
wait_for_health() {
    local start code
    start=$(date +%s)
    for _ in $(seq 1 40); do
        sleep 1
        code=$(curl -sS -o /dev/null -w '%{http_code}' --max-time 3 "$HEALTH_URL" 2>/dev/null) || true
        if [ "$code" = 200 ]; then
            log "헬스체크 통과 (경과 $(( $(date +%s) - start ))초)"
            return 0
        fi
        if [ "$code" = 404 ] && curl -fsS -o /dev/null --max-time 3 "$LIVENESS_URL" 2>/dev/null; then
            log "헬스체크 통과 — readiness가 없는 이전 버전 jar라 liveness로 판정 (경과 $(( $(date +%s) - start ))초)"
            return 0
        fi
    done
    log "헬스체크 실패 (경과 $(( $(date +%s) - start ))초, 마지막 응답 코드 ${code:-없음})"
    return 1
}

# 5. 재시작(sudoers에서 이 명령 하나만 비밀번호 없이 허용). set -e 아래에서 이 명령이 실패하면 7번 롤백까지 못 가므로 if로 감싼다.
healthy=0
if sudo /usr/bin/systemctl restart kraft; then
    log "재시작 요청됨. 기동을 기다린다"
    # 6. 헬스체크. 실제 최악 대기는 이 루프(약 160초)에 재시작 요청·jar 언패킹·JVM 기동 시간이 더해진다.
    if wait_for_health; then
        healthy=1
    fi
else
    log "systemctl restart 자체가 실패했다(sudoers 설정을 확인할 것)"
fi

if [ "$healthy" = 1 ]; then
    # 정상 기동이 확인된 jar만 보관한다(롤백 후보에 실패한 jar가 섞이지 않게). 보관 실패는 배포 성공을 뒤집지 않는다.
    if mkdir -p "$RELEASES_DIR" && cp -p "$JAR" "$RELEASES_DIR/kraft-$STAMP-$REF.jar"; then
        ls -1t "$RELEASES_DIR"/kraft-*.jar 2>/dev/null | tail -n +$((KEEP_RELEASES + 1)) | xargs -r rm -f
        log "롤백용 보관: releases/kraft-$STAMP-$REF.jar (최근 ${KEEP_RELEASES}개 유지)"
    else
        log "경고: 롤백용 jar 보관에 실패했다(배포 자체는 성공)"
    fi
    # 오래된 힙 덤프를 지운다. 정리 실패가 배포 성공을 뒤집지 않는다.
    if [ -d "$HEAPDUMP_DIR" ]; then
        ls -1t "$HEAPDUMP_DIR"/*.hprof 2>/dev/null | tail -n +$((KEEP_HEAPDUMPS + 1)) | xargs -r rm -f || true
    fi
    log "───── 배포 성공 (ref=$REF) ─────"
    exit 0
fi

# 7. 롤백. jar만 되돌아간다는 점을 분명히 남긴다.
log "헬스체크 실패. 이전 jar로 되돌린다"
if [ -f "$PREVIOUS" ]; then
    # 실패한 jar는 원인 분석을 위해 남기되 최근 10개만 둔다(수십MB라 방치하면 디스크를 채운다).
    mv "$JAR" "$APP_DIR/kraft.jar.failed-$STAMP"
    ls -1t "$APP_DIR"/kraft.jar.failed-* 2>/dev/null | tail -n +11 | xargs -r rm -f
    mv "$PREVIOUS" "$JAR"
    # 재시작 명령 자체가 실패해도 "롤백 실패" 원인을 로그에 남기도록 if로 감싼다.
    if sudo /usr/bin/systemctl restart kraft; then
        # 복원한 jar도 응답하지 않으면 서비스가 죽어 있는데 "완료"로 거짓 안심시키므로 같은 헬스체크로 복원을 검증한다.
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
