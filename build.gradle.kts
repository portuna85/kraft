import org.apache.tools.ant.filters.ReplaceTokens
import org.springframework.boot.gradle.tasks.bundling.BootJar
import java.security.MessageDigest
import java.util.zip.Deflater
import java.util.zip.GZIPOutputStream

plugins {
    java
    jacoco
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.diffplug.spotless") version "8.10.3"
}

group = "com.kraft"
version = "0.0.1-SNAPSHOT"
description = "kraft"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

// Java 포맷 게이트. 규칙은 일부러 최소로 둔다(미사용 import, 줄 끝 공백, 파일 끝 줄바꿈) — 전체를 포맷터로 다시 쓰면 15k줄이 바뀌어 blame·리뷰가 망가진다.
// 줄바꿈은 git autocrlf와 무관하게 LF로 고정한다(.editorconfig·.gitattributes와 같다). `./gradlew spotlessCheck`로 확인하고 `spotlessApply`로 고친다.
spotless {
    lineEndings = com.diffplug.spotless.LineEnding.UNIX
    java {
        target("src/**/*.java")
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

// 브라우저 테스트용 서버 코드는 운영 클래스패스와 JAR에 포함하지 않는다.
val e2e = sourceSets.create("e2e") {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}

configurations[e2e.implementationConfigurationName].extendsFrom(configurations.implementation.get())
configurations[e2e.compileOnlyConfigurationName].extendsFrom(configurations.compileOnly.get())
configurations[e2e.annotationProcessorConfigurationName].extendsFrom(configurations.annotationProcessor.get())
configurations[e2e.runtimeOnlyConfigurationName].extendsFrom(configurations.runtimeOnly.get())

// Mockito 인라인 모킹의 바이트코드 에이전트를 테스트 중 동적으로 붙이면 JDK가 경고한다(향후 JDK에서는 실패). 공식 안내대로 mockito-core만 받아
// -javaagent로 미리 붙인다.
val mockitoAgent = configurations.create("mockitoAgent")

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.thymeleaf.extras:thymeleaf-extras-springsecurity6")
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-session-jdbc")
    // 인기글처럼 요청마다 다시 계산할 필요가 없는 값을 짧게 캐시한다.
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("com.github.ben-manes.caffeine:caffeine")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("org.springframework.boot:spring-boot-flyway")
    implementation("org.flywaydb:flyway-mysql")
    runtimeOnly("org.mariadb.jdbc:mariadb-java-client")
    testRuntimeOnly("com.h2database:h2")
    add(e2e.runtimeOnlyConfigurationName, "com.h2database:h2")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-thymeleaf-test")
    testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    // 운영과 같은 MariaDB에서 Flyway 마이그레이션·ddl-auto validate·JDBC 세션을 검증한다(H2 테스트는 db/migration의 SQL을 실행하지 않는다).
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-mariadb")
    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // 버전은 Spring Boot BOM이 관리해 테스트 클래스패스의 mockito-core와 같아진다.
    mockitoAgent("org.mockito:mockito-core") { isTransitive = false }
}

// 두 산출물 모두 버전이 붙지 않는 고정 파일명을 쓴다(CI 워크플로·playwright.config.js가 이름을 참조하므로 버전을 올려도 고칠 곳이 없다).
tasks.named<BootJar>("bootJar") {
    archiveFileName.set("kraft.jar")
}

tasks.register<BootJar>("bootE2eJar") {
    group = "build"
    description = "Builds the browser-test server with E2E fixtures and H2."
    archiveFileName.set("kraft-e2e.jar")
    mainClass.set("com.kraft.KraftApplication")
    targetJavaVersion.set(tasks.named<BootJar>("bootJar").flatMap { it.targetJavaVersion })
    classpath(e2e.runtimeClasspath)
}

// 이 프로젝트는 실행형 애플리케이션이므로 별도의 일반 라이브러리 JAR은 만들지 않는다.
tasks.jar {
    enabled = false
}

// 정적 자원의 고정 버전 문자열. /js/**의 Spring 리소스 체인(FixedVersionStrategy)이 URL 접두사로 쓴다 — 배포마다 커밋이 바뀌어 장기 캐시(immutable)를 걸어도
// 새 배포의 자원이 새 경로로 요청된다. git이 없으면(소스 tarball 빌드) project.version으로 폴백한다. application.yml의 "@buildVersion@"만 치환한다(Ant 스타일 —
// Spring의 "${...}"와 겹치지 않는다).
val gitCommit: String = try {
    providers.exec {
        commandLine("git", "rev-parse", "--short", "HEAD")
    }.standardOutput.asText.get().trim().ifBlank { version.toString() }
} catch (e: Exception) {
    version.toString()
}

// 커밋하지 않은 변경이 있으면 변경 내용의 해시를 붙인다 — 커밋 SHA만 쓰면 같은 SHA에서 코드를 고쳐 가며 빌드해도 /js 경로 버전이 그대로라 브라우저가 옛 캐시를 쓴다.
// 같은 변경이면 같은 값이라 반복 빌드는 캐시를 쓴다. 추적 파일 변경만 보므로 깨끗한 CI 체크아웃은 영향이 없다(X-Kraft-Build도 SHA 그대로).
val buildVersion: String = try {
    val diff = providers.exec {
        commandLine("git", "diff", "HEAD")
        isIgnoreExitValue = true
    }.standardOutput.asBytes.get()
    if (diff.isEmpty()) {
        gitCommit
    } else {
        val digest = MessageDigest.getInstance("SHA-1").digest(diff)
        gitCommit + "-dirty" + digest.joinToString("") { "%02x".format(it) }.take(7)
    }
} catch (e: Exception) {
    gitCommit
}

tasks.processResources {
    // 실행 시점 콜백에는 로컬 값만 캡처하고, 커밋 변경도 태스크 입력으로 추적해 캐시가 이전 정적 자원 버전을 재사용하지 않게 한다.
    val resourceTokens = mapOf("buildVersion" to buildVersion)
    inputs.property("buildVersion", resourceTokens.getValue("buildVersion"))
    filesMatching("application.yml") {
        filter(ReplaceTokens::class, "tokens" to resourceTokens)
    }
    // static/js 안의 *.test.js는 node --test 전용 단위 테스트라 배포 정적 자원으로 나갈 이유가 없다.
    exclude("**/*.test.js")
    // static/js/app은 주석 달린 원본 소스다 — 배포되는 것은 Vite 번들(vue-dist)이고, 원본을 같이 넣으면 번들되지 않은 복사본이 /js/app/**로 서빙된다.
    exclude("static/js/app/**")

    // 정적 텍스트 자원(js·css·svg)을 빌드 때 한 번 gzip(레벨 9)으로 압축해 옆에 .gz로 둔다 — application.yml의 resources.chain.compressed가 gzip을 받는
    // 클라이언트에 그대로 내려줘 요청마다 CPU로 압축하지 않는다. 1KB 미만이거나 줄지 않으면 만들지 않는다. Brotli는 JDK 인코더가 없어 쓰지 않는다.
    doLast {
        val staticDir = destinationDir.resolve("static")
        if (staticDir.isDirectory) {
            val compressible = setOf("js", "css", "svg")
            staticDir.walkTopDown().filter { it.isFile && it.extension == "gz" }.forEach { it.delete() }
            staticDir.walkTopDown()
                .filter { it.isFile && it.extension in compressible && it.length() >= 1024 }
                .forEach { source ->
                    val target = File(source.path + ".gz")
                    object : GZIPOutputStream(target.outputStream()) {
                        init {
                            def.setLevel(Deflater.BEST_COMPRESSION)
                        }
                    }.use { out -> source.inputStream().use { it.copyTo(out) } }
                    if (target.length() >= source.length()) {
                        target.delete()
                    }
                }
        }
    }
}

tasks.withType<Test> {
    // -PdockerTests=exclude|only로 Docker(Testcontainers) 테스트를 나누거나 그것만 돌린다(기본 all). CI는 빠른 H2 테스트와 느린 Docker 테스트를
    // 다른 잡에서 병렬로 돌린다. 태그는 @Tag("docker")이며 MariaDbIntegrationTest 기반 클래스와 두 리허설 테스트가 가진다.
    useJUnitPlatform {
        when (providers.gradleProperty("dockerTests").getOrElse("all")) {
            "exclude" -> excludeTags("docker")
            "only" -> includeTags("docker")
        }
    }
    // -PtestForks=N으로 JVM 수를 조정한다. JVM 안의 JUnit 실행은 순차로 두어 Spring 컨텍스트·H2 정리 확장이 같은 DB를 동시에 건드리지 않게 한다.
    maxParallelForks = providers.gradleProperty("testForks").map(String::toInt).getOrElse(1)
    // local(기본)은 Docker MariaDB를 쓰므로, 테스트가 Docker 없이 빠르고 격리되게 test 프로파일(application-test.yml, H2 인메모리)을 강제한다.
    // @DataJpaTest는 임베디드 DB로 자동 교체되지만 @SpringBootTest는 실제 데이터소스 설정을 쓰므로 이 프로파일이 없으면 Docker가 떠 있어야 통과한다.
    systemProperty("spring.profiles.active", "test")
    // Spring이 각 테스트 JVM의 worker 번호를 해석한다. 롤링 로그 파일 충돌을 막는다.
    systemProperty("logging.file.path",
        layout.buildDirectory.dir("test-logs").get().asFile.absolutePath + "/\${org.gradle.test.worker:single}")
    // 시간은 KST로 고정한다(KraftApplication.ZONE_ID) — main()을 거치지 않는 테스트 JVM도 개발 PC 시간대와 무관하게 같은 결과를 낸다.
    jvmArgs("-javaagent:${mockitoAgent.asPath}", "-Duser.timezone=Asia/Seoul")
}

/**
 * 커버리지 리포트. 문턱값(jacocoTestCoverageVerification)은 걸지 않는다 — 이 저장소의 안전망은 단위 테스트 수치가 아니라 실제 DB·브라우저까지 밟는
 * 검증(Testcontainers, Playwright)이고 JaCoCo는 E2E 실행을 세지 못한다. 숫자를 맞추려 의미 없는 테스트를 늘리는 대신 리포트를 보고 빈 곳을 사람이 판단한다.
 */
tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        html.required.set(true)
        // CI가 기계적으로 읽을 수 있는 형식도 남긴다(리포트를 사람이 열지 않아도 되도록).
        xml.required.set(true)
    }
    classDirectories.setFrom(files(classDirectories.files.map {
        fileTree(it) {
            // 동작 없는 생성 코드(record DTO의 접근자·equals·hashCode)는 분모에서 빼 수치가 검증 범위를 과장하지 않게 한다.
            exclude("com/kraft/**/dto/**")
        }
    }))
}

// CI(환경변수 CI)이거나 -Pcoverage를 줄 때만 test 뒤에 리포트를 만든다(로컬에서 매번 만들면 느린데 보는 일은 드물다). 필요하면 ./gradlew test -Pcoverage 또는 jacocoTestReport.
tasks.test {
    if (System.getenv("CI") != null || providers.gradleProperty("coverage").isPresent) {
        finalizedBy(tasks.jacocoTestReport)
    }
}
