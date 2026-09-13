# syntax=docker/dockerfile:1
FROM eclipse-temurin:25-jdk-noble AS build
WORKDIR /workspace

COPY --chmod=0755 gradlew ./gradlew
COPY gradle ./gradle
COPY build.gradle.kts settings.gradle.kts ./
COPY src ./src
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon test bootJar

FROM eclipse-temurin:25-jre-noble AS runtime
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 kraft \
    && useradd --uid 10001 --gid kraft --no-create-home kraft
WORKDIR /app
RUN mkdir -p /app/uploads/images /app/logs \
    && chown -R kraft:kraft /app
COPY --from=build --chown=kraft:kraft /workspace/build/libs/kraft.jar /app/kraft.jar

USER kraft:kraft
ENV SPRING_PROFILES_ACTIVE=docker \
    TZ=Asia/Seoul
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=5s --start-period=60s --retries=5 \
    CMD curl --fail --silent --show-error --max-time 4 http://127.0.0.1:8080/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "-jar", "/app/kraft.jar"]
