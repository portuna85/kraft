package com.kraft.support;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mariadb.MariaDBContainer;

import java.sql.Statement;
import java.util.List;

/**
 * 실제 MariaDB로 도는 {@code @SpringBootTest}들의 공용 기반 클래스(OPS-06).
 * <p>
 * 예전에는 클래스마다 컨테이너를 따로 띄우고 컨텍스트도 따로 만들어, 같은 일(컨테이너 기동 +
 * Flyway 전체 마이그레이션 + 컨텍스트 기동)을 클래스 수만큼 반복했다(클래스당 약 8초). 이제
 * 컨테이너를 컨텍스트의 빈으로 두고(아래 {@link ContainerConfig}), 이 클래스를 상속하는
 * 테스트가 모두 같은 {@code @SpringBootTest} 설정을 쓰게 해 Spring의 컨텍스트 캐시로 한 번만
 * 만든다 — 컨테이너 생명주기도 그 컨텍스트가 소유하므로 한 번만 뜬다.
 * <p>
 * <b>하위 클래스는 {@code @SpringBootTest}·{@code @MockitoBean}·{@code @TestPropertySource}
 * 등 컨텍스트를 바꾸는 애너테이션을 더하지 않는다</b> — 더하면 캐시 키가 달라져 컨텍스트가
 * 따로 만들어지고 이 기반 클래스를 쓴 이유가 사라진다.
 * <p>
 * 컨테이너를 공유하므로 테스트 사이의 데이터가 섞이지 않게 {@link #resetData}가 매 테스트 전에
 * 비운다. 마이그레이션이 시드로 넣는 {@code recommendation_history_state}(id=1)는 지우지 않고
 * 초기값으로 되돌린다.
 * <p>
 * {@code BackupRestoreRehearsalTest}·{@code MariaDbUpgradeRehearsalTest}는 DB 전체를 덤프·복구하거나
 * 기존 DB를 만들어 전환하는 것 자체가 검증 대상이라 이 클래스를 쓰지 않고 자기 컨테이너를 둔다.
 * <p>
 * Docker가 없으면 클래스 전체를 건너뛴다({@code disabledWithoutDocker}) — README가 안내하는 대로
 * Docker 없이도 {@code gradlew test}가 그대로 돈다. {@code @Tag("docker")}는 Gradle이 이 테스트들을
 * 빠른 H2 테스트와 따로 돌릴 수 있게 한다(OPS-07).
 */
@Tag("docker")
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        // 운영(prod)과 같은 스키마 경로: Flyway가 만들고 Hibernate는 검증만 한다.
        "spring.flyway.enabled=true",
        "spring.flyway.baseline-on-migrate=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        // 세션 테이블도 V3이 만든다. Spring Session이 따로 만들지 않게 한다.
        "spring.session.jdbc.initialize-schema=never"
})
@AutoConfigureMockMvc
@Import(MariaDbIntegrationTest.ContainerConfig.class)
public abstract class MariaDbIntegrationTest {

    /** docker-compose.yml과 같은 버전을 쓴다. 운영에서 쓰는 것과 다른 DB를 검증하면 의미가 없다. */
    private static final String IMAGE = "mariadb:11.7.2";

    /** 마이그레이션 이력·시드를 보존할 테이블. 앞의 것은 건드리지 않고 뒤의 것은 값을 되돌린다. */
    private static final String FLYWAY_HISTORY = "flyway_schema_history";
    private static final String HISTORY_STATE = "recommendation_history_state";

    @TestConfiguration(proxyBeanMethods = false)
    static class ContainerConfig {

        @Bean
        @ServiceConnection
        MariaDBContainer mariadb() {
            return new MariaDBContainer(IMAGE);
        }
    }

    @Autowired
    private JdbcTemplate baseJdbcTemplate;

    @BeforeEach
    void resetData() {
        // FOREIGN_KEY_CHECKS는 연결(세션) 단위 설정이라, 풀에서 연결이 바뀌지 않도록 한 연결 안에서 끝낸다.
        baseJdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            List<String> tables = new java.util.ArrayList<>();
            try (var statement = connection.createStatement();
                 var rs = statement.executeQuery(
                         "SELECT TABLE_NAME FROM information_schema.TABLES "
                                 + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_TYPE = 'BASE TABLE'")) {
                while (rs.next()) {
                    tables.add(rs.getString(1));
                }
            }
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET FOREIGN_KEY_CHECKS = 0");
                for (String table : tables) {
                    if (!table.equalsIgnoreCase(FLYWAY_HISTORY) && !table.equalsIgnoreCase(HISTORY_STATE)) {
                        // TRUNCATE는 DELETE 트리거를 실행하지 않는다 — 이력 version이 따로 올라가지 않는다.
                        statement.execute("TRUNCATE TABLE `" + table + "`");
                    }
                }
                statement.execute("SET FOREIGN_KEY_CHECKS = 1");
                statement.execute("UPDATE " + HISTORY_STATE + " SET version = 0, verified_through_round = 0, "
                        + "source_reference = NULL, verified_at = NULL WHERE id = 1");
            }
            return null;
        });
    }
}
