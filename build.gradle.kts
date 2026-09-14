import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    java
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
