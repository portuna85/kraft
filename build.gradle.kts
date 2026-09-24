import org.apache.tools.ant.filters.ReplaceTokens
import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    java
    jacoco
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
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

// 브라우저 테스트용 서버 코드는 운영 클래스패스와 JAR에 포함하지 않는다.
val e2e = sourceSets.create("e2e") {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}

configurations[e2e.implementationConfigurationName].extendsFrom(configurations.implementation.get())
configurations[e2e.compileOnlyConfigurationName].extendsFrom(configurations.compileOnly.get())
configurations[e2e.annotationProcessorConfigurationName].extendsFrom(configurations.annotationProcessor.get())
configurations[e2e.runtimeOnlyConfigurationName].extendsFrom(configurations.runtimeOnly.get())

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
    // 운영과 같은 MariaDB에서 Flyway 마이그레이션·ddl-auto validate·JDBC 세션을 실제로 검증한다.
    // H2(create-drop)로 도는 나머지 테스트는 db/migration의 SQL을 한 번도 실행하지 않는다.
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-mariadb")
    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.register<BootJar>("bootE2eJar") {
    group = "build"
    description = "Builds the browser-test server with E2E fixtures and H2."
    archiveClassifier.set("e2e")
    mainClass.set("com.kraft.KraftApplication")
    targetJavaVersion.set(tasks.named<BootJar>("bootJar").flatMap { it.targetJavaVersion })
    classpath(e2e.runtimeClasspath)
}

// 이 프로젝트는 실행형 애플리케이션이므로 별도의 일반 라이브러리 JAR은 만들지 않는다.
tasks.jar {
    enabled = false
}

// 정적 자원의 고정 버전 문자열(FE-01). /js/**에 대한 Spring 리소스 체인의 FixedVersionStrategy가
// 이 값을 URL 접두사로 쓴다 — 배포마다 커밋이 바뀌면 값도 바뀌므로 장기 캐시(immutable)를 걸어도
// 새 배포의 자원이 항상 새 경로로 요청된다. git이 없는 환경(예: 소스 tarball 빌드)에서는
// project.version으로 폴백한다. application.yml의 "@buildVersion@" 토큰만 치환하며(Ant 스타일),
// Spring의 "${...}" 플레이스홀더 문법과 겹치지 않는다.
val buildVersion: String = try {
    providers.exec {
        commandLine("git", "rev-parse", "--short", "HEAD")
    }.standardOutput.asText.get().trim().ifBlank { version.toString() }
} catch (e: Exception) {
    version.toString()
}

tasks.processResources {
    // Ant의 ReplaceTokens 필터는 설정 캐시가 직렬화할 수 없는 스크립트 객체 참조를 만든다
    // (Gradle 9.7.1 실측 — "cannot serialize Gradle script object references"). 이 태스크
    // 하나만 설정 캐시 대상에서 빼고, 나머지 태스크는 계속 캐시 혜택을 받는다.
    notCompatibleWithConfigurationCache("ReplaceTokens 필터가 설정 캐시와 호환되지 않는다")
    filesMatching("application.yml") {
        filter(ReplaceTokens::class, "tokens" to mapOf("buildVersion" to buildVersion))
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    // local(기본값)은 2026-09-11부터 Docker MariaDB를 쓰므로, 테스트가 Docker 없이도 항상
    // 빠르고 격리되어 돌도록 test 프로파일(src/test/resources/application-test.yml, H2
    // 인메모리)을 강제한다. @DataJpaTest 슬라이스는 기본적으로 임베디드 DB로 자동 교체되어
    // 이 설정과 무관하지만, @SpringBootTest(KraftApplicationTests, SecurityConfigTest)는
    // 실제 데이터소스 설정을 그대로 쓰므로 이 프로파일이 없으면 Docker가 떠 있어야만 통과한다.
    systemProperty("spring.profiles.active", "test")
    systemProperty("logging.file.path", layout.buildDirectory.dir("test-logs").get().asFile.absolutePath)
}

/**
 * 커버리지 리포트.
 *
 * 개선 보고서가 "테스트 166개 통과"를 커버리지로 읽지 말라고 경고했던 지점이다 — 수치를 볼
 * 도구가 없어 어디가 비었는지 말할 수 없었다. 이제 test를 돌리면 리포트가 함께 나온다.
 *
 * 문턱값(jacocoTestCoverageVerification)은 걸지 않는다. 이 저장소의 실제 안전망은 단위
 * 테스트 수치가 아니라 실제 DB·브라우저까지 밟는 검증(Testcontainers, Playwright)이고,
 * JaCoCo는 그 밖에서 도는 E2E의 실행을 세지 못한다. 숫자를 맞추려고 의미 없는 테스트를
 * 늘리는 대신, 리포트를 보고 빈 곳을 사람이 판단한다.
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
            // 동작이 없는 생성 코드는 분모에서 뺀다. record DTO의 접근자·equals·hashCode가
            // 대표적이며, 이것들이 섞이면 수치가 실제 검증 범위를 과장한다.
            exclude("com/kraft/**/dto/**")
        }
    }))
}

// 리포트를 따로 기억해서 돌릴 필요가 없게 한다. test가 끝나면 항상 갱신된다.
tasks.test {
    finalizedBy(tasks.jacocoTestReport)
}
