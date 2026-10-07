#!/usr/bin/env bash
# 시각 회귀 기준선을 CI와 같은 환경(공식 Playwright Docker 이미지, Linux)에서 만들거나 검사한다(OPS-05).
#
#   scripts/visual-docker.sh update   # 기준선(-chromium-linux.png)을 새로 만들어 e2e/*-snapshots에 복사
#   scripts/visual-docker.sh check    # 현재 기준선과 비교만 한다(CI의 visual 잡과 같은 조건)
#
# 사전 조건: Docker, 그리고 ./gradlew bootE2eJar로 만든 build/libs/kraft-e2e.jar.
#
# 작업 폴더를 마운트하지 않고 컨테이너로 복사한다 — 컨테이너 안의 `npm ci`가 호스트(예: Windows)의
# node_modules를 리눅스 바이너리로 덮어쓰지 않게 하기 위해서다. JDK는 이미지에 없어 Temurin 25를
# 내려받는다(CI의 setup-java와 같은 배포판).
set -euo pipefail

mode="${1:-}"
case "$mode" in
    update) flags="--update-snapshots" ;;
    check) flags="" ;;
    *) echo "사용법: $0 update|check" >&2; exit 2 ;;
esac

cd "$(dirname "$0")/.."
[ -f build/libs/kraft-e2e.jar ] || { echo "build/libs/kraft-e2e.jar가 없다. ./gradlew bootE2eJar 먼저" >&2; exit 1; }

version=$(node -e "console.log(require('./package-lock.json').packages['node_modules/playwright-core'].version)")
image="mcr.microsoft.com/playwright:v${version}-noble"
name="kraft-visual-$$"

cleanup() { docker rm -f "$name" >/dev/null 2>&1 || true; }
trap cleanup EXIT

docker run -d --name "$name" --init --ipc=host "$image" sleep infinity >/dev/null
docker exec "$name" mkdir -p /work/build/libs

# 소스는 추적 파일과 아직 커밋하지 않은 새 파일(.gitignore 제외)만 보낸다. 산출물·의존성·이전 결과는 뺀다.
git ls-files -z --cached --others --exclude-standard | tar --null -T - -czf - | docker exec -i "$name" tar -xzf - -C /work
docker cp build/libs/kraft-e2e.jar "$name:/work/build/libs/kraft-e2e.jar"

docker exec -w /work "$name" bash -c '
    set -euo pipefail
    curl -fsSL "https://api.adoptium.net/v3/binary/latest/25/ga/linux/x64/jdk/hotspot/normal/eclipse" -o /tmp/jdk.tgz
    mkdir -p /opt/jdk && tar -xzf /tmp/jdk.tgz -C /opt/jdk --strip-components=1
    npm ci --no-audit --no-fund >/dev/null
'

status=0
docker exec -w /work -e CI=1 -e VISUAL=1 -e PATH="/opt/jdk/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin" \
    "$name" npx playwright test --project=chromium visual $flags || status=$?

if [ "$mode" = update ]; then
    # 새로 만든 리눅스 기준선만 가져온다. 폴더를 하나씩 적어 두면 새 시각 스펙(예: visual-recommend)의 기준선이
    # 조용히 빠진다 — 컨테이너 안의 *-snapshots 폴더를 전부 훑는다.
    for dir in $(docker exec "$name" bash -c 'cd /work && ls -d e2e/*-snapshots' | tr -d '\r'); do
        mkdir -p "$dir"
        docker exec "$name" bash -c "ls /work/$dir/*-linux.png" | tr -d '\r' | while read -r f; do
            docker cp "$name:$f" "$dir/"
        done
    done
fi
exit "$status"
