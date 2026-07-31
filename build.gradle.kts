plugins {
    java
    id("org.springframework.boot") version "4.1.0"
    jacoco
    checkstyle
    id("com.github.spotbugs") version "6.5.6"
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
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3")
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
    testImplementation("com.tngtech.archunit:archunit-junit5:1.4.2")
    testImplementation("org.pitest:pitest-junit5-plugin:1.2.3")
}

val requestedTestWorkers = providers.gradleProperty("testWorkers").orNull?.toIntOrNull()

tasks.withType<Test> {
    useJUnitPlatform()
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
    onlyIf { (requestedTestWorkers ?: 1) > 1 }
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

// ── JaCoCo ─────────────────────────────────────────────────────────────────

jacoco {
    toolVersion = "0.8.13"
}

// Patterns excluded from coverage measurement (shared by report + verification).
// Excludes: Spring config/properties, DTO/record boilerplate, JPA entities
// (no logic beyond getters), enums, event records, and admin Thymeleaf layer
// (controller/handlers/filter — covered by smoke tests, not unit tests).
val jacocoExcludes = listOf(
    "**/Application.class",
    "**/config/**",
    "**/*Properties.class",
    "**/*Response.class",
    "**/*Request.class",
    "**/*Dto.class",
    "**/db/migration/**",
    // JPA entities — getters/constructors only, no business logic
    "**/winningnumber/WinningNumber.class",
    "**/saved/SavedNumber.class",
    "**/admin/AdminUser.class",
    "**/admin/AdminAuditLog.class",
    "**/statistics/FrequencySummary.class",
    "**/statistics/PatternStatsSummary.class",
    "**/statistics/CompanionPairSummary.class",
    "**/operationlog/WinningNumberOperationLog.class",
    // Enums
    "**/WinningNumberOperationType.class",
    "**/WinningNumberOperationStatus.class",
    // Event records
    "**/*Event.class",
    // Admin Thymeleaf layer — tested by smoke tests, not unit tests
    "**/admin/AdminController.class",
    "**/admin/AdminLoginHandler.class",
    "**/admin/AdminLockoutFilter.class"
)

fun jacocoClassDirs() = fileTree(layout.buildDirectory.dir("classes/java/main")) {
    exclude(jacocoExcludes)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test, isolatedLoggingTest)
    executionData(fileTree(layout.buildDirectory.dir("jacoco")) { include("*.exec") })
    reports {
        xml.required = true
        html.required = true
    }
    classDirectories.setFrom(jacocoClassDirs())
}

val strictCoverage = project.findProperty("strictCoverage") as String?
if (strictCoverage == "true") {
    tasks.jacocoTestCoverageVerification {
        dependsOn(tasks.jacocoTestReport)
        executionData(fileTree(layout.buildDirectory.dir("jacoco")) { include("*.exec") })
        classDirectories.setFrom(jacocoClassDirs())
        violationRules {
            // 전체 프로젝트 임계값
            rule {
                limit {
                    counter = "LINE"
                    value = "COVEREDRATIO"
                    minimum = "0.82".toBigDecimal()
                }
                limit {
                    counter = "BRANCH"
                    value = "COVEREDRATIO"
                    minimum = "0.65".toBigDecimal()
                }
                limit {
                    counter = "METHOD"
                    value = "COVEREDRATIO"
                    minimum = "0.88".toBigDecimal()
                }
                limit {
                    counter = "CLASS"
                    value = "COVEREDRATIO"
                    minimum = "0.97".toBigDecimal()
                }
            }
            // 핵심 도메인 패키지별 라인 커버리지 임계값
            rule {
                element = "PACKAGE"
                includes = listOf("com/kraft/saved")
                limit { counter = "LINE"; value = "COVEREDRATIO"; minimum = "0.85".toBigDecimal() }
            }
            rule {
                element = "PACKAGE"
                includes = listOf("com/kraft/recommend")
                limit { counter = "LINE"; value = "COVEREDRATIO"; minimum = "0.85".toBigDecimal() }
            }
            rule {
                element = "PACKAGE"
                includes = listOf("com/kraft/winningnumber")
                limit { counter = "LINE"; value = "COVEREDRATIO"; minimum = "0.80".toBigDecimal() }
            }
            rule {
                element = "PACKAGE"
                includes = listOf("com/kraft/statistics")
                limit { counter = "LINE"; value = "COVEREDRATIO"; minimum = "0.80".toBigDecimal() }
            }
            rule {
                element = "PACKAGE"
                includes = listOf("com/kraft/ops")
                limit { counter = "LINE"; value = "COVEREDRATIO"; minimum = "0.80".toBigDecimal() }
            }
            rule {
                element = "PACKAGE"
                includes = listOf("com/kraft/common/web")
                limit { counter = "LINE"; value = "COVEREDRATIO"; minimum = "0.75".toBigDecimal() }
            }
        }
    }
    tasks.check { dependsOn(tasks.jacocoTestCoverageVerification) }
}

// ── Checkstyle ──────────────────────────────────────────────────────────────

checkstyle {
    toolVersion = "10.23.0"
    configFile = file("config/checkstyle/checkstyle.xml")
    isIgnoreFailures = (project.findProperty("strictStatic") != "true")
}

tasks.withType<Checkstyle> {
    reports {
        xml.required = false
        html.required = true
    }
}

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
}
