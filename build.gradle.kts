plugins {
    java
    id("org.springframework.boot") version "4.1.0"
    jacoco
    checkstyle
    id("com.github.spotbugs") version "6.5.10"
    id("info.solidsoft.pitest") version "1.19.0"
}

group = "com.kraft"
version = "1.0-SNAPSHOT"

tasks.bootJar {
    archiveFileName.set("kraft-backend.jar")
}
tasks.jar {
    enabled = false
}

// Lock runtime and compile classpaths for reproducible Dockerfile builds (blueprint §10.3)
dependencyLocking {
    lockAllConfigurations()
}

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.0"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.springframework.boot:spring-boot-flyway")
    // 아티팩트명은 "springsecurity6"이지만 Boot 4.1.0 BOM이 실제 Spring Security 7.1.0과
    // 함께 이 버전(3.1.5.RELEASE)을 관리·검증해 배포한다 — 별도 springsecurity7 아티팩트는
    // 존재하지 않으며 버전 스큐가 아니다(확인: dependencyInsight로 Security 7.1.0 확인 완료).
    implementation("org.thymeleaf.extras:thymeleaf-extras-springsecurity6")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-mysql")
    // TD-019: Boot 4.1은 기본적으로 Jackson 3(tools.jackson)을 쓰지만, 이 앱은 Jackson 2를
    // 직접 참조하는 지점이 3곳 있다(ApiErrorResponseWriter의 ObjectMapper/JavaTimeModule,
    // RecommendationStrategy의 @JsonValue) — 그래서 둘 다 락파일에 명시적으로 고정돼 있다.
    // 이 앱 코드에서 tools.jackson을 직접 import하는 곳은 없다. 제거를 시도하기 전에
    // 저 3곳을 전부 마이그레이션하고 springdoc/swagger의 Jackson 2 의존 여부까지
    // 확인해야 한다 — 무관한 리팩터링 중에 슬쩍 정리하지 않는다(§13).
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation("com.github.ben-manes.caffeine:caffeine")
    // 레이트리밋 Redis 백엔드(kraft.security.rate-limit-backend=redis, 다중 인스턴스
    // 스케일아웃 시에만 사용) — 기본값은 여전히 in-memory(Caffeine)라 이 스타터가 있어도
    // Redis 서버 없이 기동·테스트 전부 그대로 동작한다(RedisRateLimitCounter 참고).
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("net.javacrumbs.shedlock:shedlock-spring:7.7.0")
    implementation("net.javacrumbs.shedlock:shedlock-provider-jdbc-template:7.7.0")
    implementation("io.github.resilience4j:resilience4j-spring-boot4:2.4.0")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.0")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor:4.1.0")
    runtimeOnly("org.mariadb.jdbc:mariadb-java-client")

    developmentOnly(platform("org.springframework.boot:spring-boot-dependencies:4.1.0"))
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    // H2: 로컬 bootRun(IntelliJ, Docker 없는 환경)과 테스트에서만 사용. bootJar 제외.
    // compileOnly는 LocalSecurityConfig(JakartaWebServlet 임포트) 컴파일용; 런타임 jar 미포함.
    compileOnly("com.h2database:h2")
    developmentOnly("com.h2database:h2")
    testImplementation("com.h2database:h2")

    testImplementation("org.springframework.boot:spring-boot-starter-test:4.1.0")
    testImplementation("org.springframework.boot:spring-boot-webmvc-test:4.1.0")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("com.fasterxml.jackson.core:jackson-databind")
    testImplementation(platform("org.testcontainers:testcontainers-bom:2.0.5"))
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-mariadb")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.5.0")
    testImplementation("org.pitest:pitest-junit5-plugin:1.2.3")
}

val requestedTestWorkers = providers.gradleProperty("testWorkers").orNull?.toIntOrNull()

tasks.withType<Test> {
    useJUnitPlatform()
    // maxHeapSize가 없으면 Gradle 테스트 워커 JVM이 기본 힙(플랫폼 의존, 대체로 512MB
    // 안팎)으로 뜬다 — Testcontainers·다수 Spring context 로딩이 섞인 이 스위트는 전체를
    // 한 번에 돌리면(특히 리소스가 제한된 환경) 힙 부족으로 테스트 실행 도중 크래시한다
    // (2026-08-04 확인). 병렬 포크 수와 무관하게 항상 적용한다.
    maxHeapSize = "2g"
    if (requestedTestWorkers != null) {
        maxParallelForks = requestedTestWorkers
            .coerceAtLeast(1)
            .coerceAtMost(Runtime.getRuntime().availableProcessors())
    }
    // Mockito 5.x의 inline mock maker는 byte-buddy-agent를 self-attach하는데, 향후 JDK에서
    // 이 방식이 기본 차단될 예정이라 경고가 발생한다(JEP 451). Mockito 공식 권장대로 agent를
    // 명시적으로 등록해 self-attach를 피한다.
    val mockitoAgentJar = configurations.testRuntimeClasspath.get().files
        .firstOrNull { it.name.startsWith("mockito-core-") }
    if (mockitoAgentJar != null) {
        jvmArgs("-javaagent:${mockitoAgentJar.absolutePath}")
    }
}

val isolatedLoggingTest = tasks.register<Test>("isolatedLoggingTest") {
    description = "Runs the process-global Logback configuration tests in isolation."
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    maxParallelForks = 1
    filter {
        includeTestsMatching("com.kraft.common.config.LogbackConfigurationTest")
    }
    // TD-025: onlyIf가 최상위 requestedTestWorkers를 직접 참조하면 Kotlin이 빌드 스크립트
    // 인스턴스 전체를 암묵적으로 캡처해 configuration cache 직렬화가 깨진다(확인:
    // "cannot serialize Gradle script object references"). 값을 로컬 변수로 한 번
    // 복사해 그 원시값만 캡처되게 한다.
    val runIsolated = (requestedTestWorkers ?: 1) > 1
    onlyIf { runIsolated }
    shouldRunAfter(tasks.test)
}

tasks.test {
    finalizedBy(tasks.jacocoTestReport)
    if ((requestedTestWorkers ?: 1) > 1) {
        filter {
            excludeTestsMatching("com.kraft.common.config.LogbackConfigurationTest")
        }
    }
}

tasks.check {
    dependsOn(isolatedLoggingTest)
}

// JaCoCo/Checkstyle는 Gradle 코어 API(gradleApi())에 포함된 타입이라 apply(from=)로
// 분리해도 타입-세이프 접근이 그대로 된다 — 파일만 옮겼고 태스크 이름·게이트 동작은
// 그대로다(TD-023).
apply(from = "gradle/jacoco.gradle.kts")
apply(from = "gradle/checkstyle.gradle.kts")

// SpotBugs/Pitest는 plugins{} 블록으로 끌어온 서드파티 플러그인 클래스라 apply(from=)
// 스크립트에는 그 클래스가 컴파일 클래스패스에 없다(buildscript classpath를 별도
// 선언해 중복 클래스로더로 끌어오면 런타임 ClassCastException 위험) — 그래서 이 둘은
// plugins{}가 선언된 이 루트 파일에 그대로 둔다.

// ── SpotBugs ────────────────────────────────────────────────────────────────

spotbugs {
    toolVersion = "4.10.2"
    // SpotBugs 4.10.x added Java 25 class file support (https://github.com/spotbugs/spotbugs/issues/3564).
    ignoreFailures = false
    excludeFilter = file("config/spotbugs/exclude.xml")
}

tasks.withType<com.github.spotbugs.snom.SpotBugsTask> {
    reports.create("html") { required = true }
    reports.create("xml") { required = false }
}

// ── Mutation testing (B-07) ─────────────────────────────────────────────────
// 라인 커버리지는 "실행됐다"만 보고 "검증됐다"는 못 본다 — 로또 검증·등수처럼 값이
// 하나만 틀려도 실제 돈(당첨 판정)에 영향을 주는 소형 순수 로직에 한해 뮤테이션
// 테스트를 붙인다. 전체 코드베이스에 걸면 느리고 signal 대비 비용이 크므로,
// 완료 조건이 예시로 든 "로또 검증·등수·추천 제약"에 해당하는 클래스만 좁혀서 돈다.
pitest {
    junit5PluginVersion = "1.2.3"
    targetClasses = listOf(
        "com.kraft.common.lotto.LottoNumbersValidator",
        "com.kraft.common.lotto.LottoRank",
        "com.kraft.common.lotto.LottoNumberCodec",
        "com.kraft.recommend.CombinationScorer"
    )
    targetTests = listOf(
        "com.kraft.common.lotto.*",
        "com.kraft.recommend.*"
    )
    outputFormats = listOf("HTML")
    timestampedReports = false
    threads = 4
    // M-13: 설정만 있고 어떤 워크플로도 pitest를 실행하지 않아 게이트 자체가 없었다.
    // 주간 cron(.github/workflows/pitest.yml)에서 돌리고 이 threshold로 실패시킨다.
    // 2026-08-03 로컬 측정 실제 점수 93%(63/68 killed) — 약간의 여유를 두되 유의미한
    // 하락은 잡도록 설정한다.
    mutationThreshold = 85
}
