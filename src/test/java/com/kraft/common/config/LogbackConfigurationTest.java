package com.kraft.common.config;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.Appender;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.logging.LoggingInitializationContext;
import org.springframework.boot.logging.logback.LogbackLoggingSystem;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Logback 파일 로그 설정 테스트")
@ResourceLock("logback")
class LogbackConfigurationTest {

    private final LogbackLoggingSystem loggingSystem =
            new LogbackLoggingSystem(LogbackConfigurationTest.class.getClassLoader());

    @TempDir
    Path logDirectory;

    @AfterEach
    void cleanUpLoggingSystem() {
        MDC.clear();
        loggingSystem.cleanUp();
    }

    @Test
    @DisplayName("local 프로필은 request ID와 예외를 단일 UTF-8 파일에 기록한다")
    void localProfile_writesSingleLogFile() throws IOException {
        initializeLogging("local");
        Logger logger = LoggerFactory.getLogger("com.kraft.logging.local");

        MDC.put("requestId", "local-request");
        logger.info("로컬 정보 로그");
        logger.error("로컬 오류 로그", new IllegalStateException("local failure"));
        stopAppenders();

        String localLog = readLog("kraft-local.log");
        assertThat(localLog)
                .contains("로컬 정보 로그")
                .contains("로컬 오류 로그")
                .contains("[local-request]")
                .contains("java.lang.IllegalStateException: local failure");
        assertThat(logDirectory.resolve("kraft.log")).doesNotExist();
        assertThat(logDirectory.resolve("kraft-error.log")).doesNotExist();
    }

    @Test
    @DisplayName("test 프로필은 예상 예외를 파일에 누적하지 않는다")
    void testProfile_writesConsoleOnly() {
        initializeLogging("test");
        Logger logger = LoggerFactory.getLogger("com.kraft.logging.test");

        logger.error("테스트 예상 오류", new IllegalStateException("expected failure"));
        stopAppenders();

        assertThat(logDirectory).isEmptyDirectory();
    }

    @Test
    @DisplayName("prod 프로필은 전체 로그와 ERROR 전용 로그를 분리해 기록한다")
    void prodProfile_writesCombinedAndErrorLogFiles() throws IOException {
        initializeLogging("prod");
        Logger logger = LoggerFactory.getLogger("com.kraft.logging.prod");

        MDC.put("requestId", "prod-request");
        logger.info("운영 정보 로그");
        logger.warn("운영 경고 로그");
        logger.error("운영 오류 로그", new IllegalArgumentException("prod failure"));
        stopAppenders();

        String applicationLog = readLog("kraft.log");
        assertThat(applicationLog)
                .contains("운영 정보 로그")
                .contains("운영 경고 로그")
                .contains("운영 오류 로그")
                .contains("[prod-request]")
                .contains("java.lang.IllegalArgumentException: prod failure");

        String errorLog = readLog("kraft-error.log");
        assertThat(errorLog)
                .contains("운영 오류 로그")
                .contains("[prod-request]")
                .contains("java.lang.IllegalArgumentException: prod failure")
                .doesNotContain("운영 정보 로그")
                .doesNotContain("운영 경고 로그");
    }

    private void initializeLogging(String activeProfile) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.setActiveProfiles(activeProfile);
        environment.getPropertySources().addFirst(new MapPropertySource("logback-test", Map.of(
                "logging.file.path", logDirectory.toAbsolutePath().toString(),
                "logging.level.root", "INFO"
        )));

        // The full suite may leave Logback initialized by a Spring context before this
        // class runs. Start every profile assertion from the same clean state as an
        // isolated execution so initialize() cannot reuse stale appenders.
        loggingSystem.cleanUp();
        loggingSystem.beforeInitialize();
        loggingSystem.initialize(
                new LoggingInitializationContext(environment),
                "classpath:logback-spring.xml",
                null
        );
    }

    private static void stopAppenders() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.getLogger(Logger.ROOT_LOGGER_NAME)
                .iteratorForAppenders()
                .forEachRemaining(Appender::stop);
    }

    private String readLog(String fileName) throws IOException {
        return Files.readString(logDirectory.resolve(fileName), StandardCharsets.UTF_8);
    }
}
