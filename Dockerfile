# syntax=docker/dockerfile:1.7
FROM eclipse-temurin:25-jdk@sha256:201fbb8886b2d273218aa3a192f0afbf7b5ff65ee8cc6ef47f5dce2171f013ea AS source-build
WORKDIR /workspace

# 의존성 레이어 분리 — build.gradle.kts 변경 시에만 재다운로드
COPY gradlew gradlew.bat settings.gradle.kts build.gradle.kts gradle.properties gradle.lockfile ./
COPY gradle ./gradle
RUN --mount=type=cache,target=/root/.gradle \
    chmod +x gradlew \
    && ./gradlew dependencies --no-daemon --quiet

COPY src ./src
COPY config ./config
# 소스 빌드
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew bootJar --no-daemon -x test \
    && java -Djarmode=tools -jar build/libs/kraft-backend.jar extract \
      --layers --launcher --destination /workspace/extracted

# OPS-IMG-01(docs/improvement.md): HEALTHCHECK용 프로브를 여기(JDK가 있는 스테이지)에서
# 미리 .class로 컴파일해 둔다 — runtime-base는 JRE 이미지라 jdk.compiler가 없어
# 즉석 컴파일(java HealthCheck.java, JEP 330)을 할 수 없다.
COPY docker/healthcheck/HealthCheck.java ./healthcheck/
RUN javac -d ./healthcheck ./healthcheck/HealthCheck.java

# 레이어 추출 — 의존성(dependencies)은 build.gradle.kts가 안 바뀌는 한 그대로라 별도
# 레이어로 캐시되고, 코드만 바뀐 빌드는 application 레이어만 재푸시하면 된다.
FROM eclipse-temurin:25-jre@sha256:681c543d6f36c50f45e9b5226930a46203dcfa351d3670e9d0bdf0dabae53539 AS prebuilt-extract
WORKDIR /workspace
COPY build/libs/kraft-backend.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract \
      --layers --launcher --destination /workspace/extracted \
    && rm app.jar

FROM eclipse-temurin:25-jre@sha256:681c543d6f36c50f45e9b5226930a46203dcfa351d3670e9d0bdf0dabae53539 AS runtime-base
WORKDIR /app

# 컨테이너 친화적 JVM 옵션
# ZGC는 대형 힙 저지연에 강점이 있는데, 컨테이너 메모리 제한(docker-compose의 1g×55%≈560MB)이
# 그 강점이 크게 발휘되기 어려운 크기라 현재 선택이 측정으로 뒷받침된 것은 아니다. 트래픽 규모상
# 문제가 될 가능성은 낮으나, 변경을 고려한다면 측정 없이 바꾸지 말고 Grafana의 GC pause/heap
# 지표로 G1 대비 A/B를 먼저 확인할 것.
#
# OPS-JVM-01(docs/improvement.md): ExitOnOutOfMemoryError는 OOM 시 프로세스를 죽이지만
# 왜 죽었는지는 남기지 않았다 — HeapDumpOnOutOfMemoryError를 더한다. 컨테이너가
# read_only: true(docker-compose.yml/.prod.yml)라 덤프 경로는 이미 있는 /tmp tmpfs
# 마운트 밑으로 지정한다(새 쓰기 가능 볼륨을 추가하지 않는다).
ENV JAVA_TOOL_OPTIONS="-XX:+UseZGC -XX:MaxRAMPercentage=55.0 -XX:+ExitOnOutOfMemoryError \
-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp/heapdump.hprof"

# M-15: apt-get upgrade -y를 뺐다 — digest로 고정한 베이스 이미지 위에서 패키지
# 버전을 임의 시점 기준으로 덮어써 같은 커밋의 두 번 빌드가 서로 다른 패키지 구성·CVE
# 프로파일을 가질 수 있었다(digest 고정의 재현성을 무효화). 베이스 이미지 자체의 보안
# 패치는 이제 dependabot(.github/dependabot.yml, docker ecosystem)이 정기적으로
# digest 갱신 PR을 올리는 경로로 받는다.
#
# OPS-IMG-01(docs/improvement.md): 예전에는 여기서 curl·ca-certificates를 런타임에
# apt-get install했다 — 같은 base digest라도 Debian 저장소 상태에 따라 패키지 구성이
# 달라질 수 있어 digest 고정의 재현성을 일부 무효화했고, healthcheck 하나 때문에
# curl+의존 라이브러리만큼 이미지 크기·CVE 표면이 늘었다. HealthCheck.class(아래
# COPY)가 JRE 내장 java.net.http.HttpClient만 쓰므로 이 apt-get 자체가 필요 없어졌다.
# apt-get은 없앴지만 rm -f /usr/bin/pebble은 그대로 남긴다 — base 이미지 자체에
# 이미 들어 있는 Go 바이너리(Canonical pebble)를 지우는 것이라 apt-get 유무와 무관하고,
# Trivy가 그 바이너리의 golang.org/x/net·stdlib 취약점을 그대로 스캔해 잡는다
# (Security Scan 잡에서 실측 확인 — 빠뜨렸다가 CVE 14건으로 게이트가 막혔었다).
RUN rm -f /usr/bin/pebble \
    && useradd --create-home --uid 10001 spring \
    && mkdir -p /app/logs \
    && chown -R spring:spring /app

COPY --from=source-build /workspace/healthcheck/HealthCheck.class ./healthcheck/

USER 10001:10001
EXPOSE 8080

# **이 값(interval/timeout/retries/start-period)은 `docker run`으로 이미지를 단독
# 실행할 때만 쓰인다** — docker-compose.yml/.prod.yml이 각자 healthcheck: 블록으로
# test 커맨드부터 재정의하므로, compose로 띄우면 이 아래 값이 아니라 compose 쪽 값이
# 실제 소스다(compose가 healthcheck:를 지정하면 Dockerfile의 HEALTHCHECK 전체를
# 덮어쓴다 — 부분 병합이 아니다). retries/start-period를 바꾸려면 compose 파일
# 쪽을 고쳐야 한다(dev/prod가 콜드스타트 예산이 달라 의도적으로 다른 값을 쓴다).
HEALTHCHECK --interval=5s --timeout=5s --start-period=30s --retries=30 \
    CMD ["java", "-cp", "/app/healthcheck", "HealthCheck"]

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]

# 변경 빈도가 낮은 레이어부터 복사 — 의존성 레이어는 build.gradle.kts가 그대로면 캐시 재사용된다.
FROM runtime-base AS prebuilt
COPY --from=prebuilt-extract /workspace/extracted/dependencies/ ./
COPY --from=prebuilt-extract /workspace/extracted/spring-boot-loader/ ./
COPY --from=prebuilt-extract /workspace/extracted/snapshot-dependencies/ ./
COPY --from=prebuilt-extract /workspace/extracted/application/ ./

FROM runtime-base AS production
COPY --from=source-build /workspace/extracted/dependencies/ ./
COPY --from=source-build /workspace/extracted/spring-boot-loader/ ./
COPY --from=source-build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=source-build /workspace/extracted/application/ ./
