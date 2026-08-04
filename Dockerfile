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
ENV JAVA_TOOL_OPTIONS="-XX:+UseZGC -XX:MaxRAMPercentage=55.0 -XX:+ExitOnOutOfMemoryError"

# M-15: apt-get upgrade -y를 뺐다 — digest로 고정한 베이스 이미지 위에서 패키지
# 버전을 임의 시점 기준으로 덮어써 같은 커밋의 두 번 빌드가 서로 다른 패키지 구성·CVE
# 프로파일을 가질 수 있었다(digest 고정의 재현성을 무효화). 베이스 이미지 자체의 보안
# 패치는 이제 dependabot(.github/dependabot.yml, docker ecosystem)이 정기적으로
# digest 갱신 PR을 올리는 경로로 받는다.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates \
    && rm -f /usr/bin/pebble \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --create-home --uid 10001 spring \
    && mkdir -p /app/logs \
    && chown -R spring:spring /app

USER 10001:10001
EXPOSE 8080

HEALTHCHECK --interval=5s --timeout=5s --start-period=30s --retries=30 \
    CMD curl -fsS http://127.0.0.1:8080/actuator/health/readiness | grep -q '"status":"UP"' || exit 1

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
