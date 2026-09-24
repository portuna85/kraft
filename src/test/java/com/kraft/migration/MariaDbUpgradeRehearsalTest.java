package com.kraft.migration;

import com.kraft.KraftApplication;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.mariadb.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.sql.Statement;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * <b>이미 데이터가 있는 기존 DB</b>를 운영 설정으로 넘기는 절차를 실제 MariaDB에서 리허설한다
 * (개선 보고서 "마이그레이션 전환" 공백).
 * <p>
 * {@link MariaDbMigrationTest}와 역할이 다르다. 그쪽은 <b>빈 DB</b>에서 V1~V7이 순서대로
 * 성공하는지를 본다. 하지만 실제 첫 배포에서 마주치는 DB는 비어 있지 않다 — {@code local}
 * 프로파일이 {@code ddl-auto: update}로 만들어 둔 스키마에 계정과 게시글이 들어 있다.
 * 그 DB에 운영 설정을 적용하는 경로는 지금까지 README가 "배포 전 같은 설정으로 1회
 * 리허설해야 합니다"라며 <b>사람에게 맡겨 둔</b> 유일한 배포 위험이었다.
 * <p>
 * 여기서 고정하는 것은 두 문장이다:
 * <ol>
 * <li>{@code application-prod.yml}의 {@code baseline-version: "1"}을 기존 DB에 그대로 쓰면
 * <b>실패한다</b> — V2가 이미 있는 컬럼을 다시 추가하려 하기 때문이다.</li>
 * <li>올바른 값은 <b>디스크에 있는 최신 마이그레이션 버전</b>이고, 그렇게 baseline한 뒤
 * {@code ddl-auto: validate}로 기동하면 통과한다.</li>
 * </ol>
 * 1번의 "실패를 기대한다"가 이 테스트의 이빨이다. 누가 운영 설정을 손대면 여기서 드러난다.
 * <p>
 * 각 단계는 {@link SpringApplicationBuilder}로 앱을 실제로 띄우고 닫는다. 컨텍스트를 두 번
 * 띄우는 것 자체가 검증 대상이라 {@code @SpringBootTest}에 맡길 수 없다.
 */
@Testcontainers(disabledWithoutDocker = true)
class MariaDbUpgradeRehearsalTest {

    /** docker-compose.yml·MariaDbMigrationTest와 같은 버전을 쓴다. */
    @Container
    static MariaDBContainer mariadb = new MariaDBContainer("mariadb:11.7.2");

    @Test
    @DisplayName("기존 개발 DB에 baseline-version 1을 그대로 쓰면 중복 컬럼으로 실패한다")
    void prodBaselineOfOneFailsOnAnExistingDevDatabase() {
        String url = existingDevDatabase();

        // application-prod.yml이 설정한 값 그대로다. 빈 DB에서는 아무 영향이 없지만,
        // 이미 V2의 컬럼이 있는 DB에서는 V2부터 다시 실행하려 든다.
        assertThatThrownBy(() -> migrateWithBaseline(url, "1"))
                .isInstanceOf(FlywayException.class)
                .as("무엇이 문제인지 메시지에 남아야 원인을 찾을 수 있다")
                .hasMessageContaining("V2")
                .as("V2가 이미 있는 컬럼을 다시 추가하려 한 것이 원인이다")
                .hasMessageContaining("Duplicate column name");
    }

    @Test
    @DisplayName("최신 버전으로 baseline하면 마이그레이션을 하나도 실행하지 않고 기록만 남긴다")
    void baseliningAtTheLatestVersionRecordsWithoutRunningAnything() {
        String url = existingDevDatabase();
        String latest = latestMigrationVersion();

        migrateWithBaseline(url, latest);

        List<Map<String, Object>> history = jdbc(url).queryForList(
                "SELECT version, type, success FROM flyway_schema_history ORDER BY installed_rank");

        // 이미 있는 스키마를 건드리지 않는 것이 핵심이다. baseline 한 줄만 남아야 한다.
        assertThat(history).singleElement().satisfies(row -> {
            assertThat(row.get("type")).isEqualTo("BASELINE");
            assertThat(row.get("version")).hasToString(latest);
            assertThat(row.get("success")).isEqualTo(true);
        });
    }

    /**
     * 전환 절차의 마지막 관문. 컨텍스트가 뜬다는 것은 {@code ddl-auto: validate}가 통과했다는
     * 뜻이고, 그것은 "{@code update}가 만든 스키마와 엔티티 매핑이 어긋나지 않는다"는 증거다.
     * <p>
     * 여기서 실패한다면 baseline은 기록만 할 뿐 스키마를 고치지 않으므로, 두 스키마의 차이를
     * 손으로 맞추는 SQL 단계가 전환 절차에 필요하다는 뜻이 된다.
     */
    @Test
    @DisplayName("전환한 DB를 운영과 같은 경로로 기동하면 스키마 검증을 통과한다")
    void migratedDatabaseStartsUnderProductionSettings() {
        String url = existingDevDatabase();
        migrateWithBaseline(url, latestMigrationVersion());

        try (ConfigurableApplicationContext context = boot(url,
                "--spring.jpa.hibernate.ddl-auto=validate",
                "--spring.flyway.enabled=false",
                "--spring.session.jdbc.initialize-schema=never")) {
            assertThat(context.isRunning()).isTrue();
        }
    }

    /**
     * O01: {@code ddl-auto: validate}가 통과한다고 해서(위 세 테스트) 기존 DB에 마이그레이션의
     * 모든 인덱스가 있다는 뜻은 아니다 — Hibernate의 스키마 검증은 테이블·컬럼·타입만 보고
     * <b>인덱스는 보지 않는다</b>. V6/V11/V13/V14/V15가 raw SQL로만 추가한 인덱스는 예전에
     * 엔티티에 선언이 없어, {@code ddl-auto: update}로 만든 "기존 DB"(이 테스트가 흉내 내는
     * 대상)에는 애초에 생기지 않았다. baseline은 상태만 기록할 뿐 누락된 DDL을 보충하지 않으므로,
     * 그런 DB를 그대로 baseline해 운영에 올리면 검색·정리 성능이 떨어지는 인덱스 없는 테이블로
     * 계속 남는다.
     * <p>
     * 지금은 엔티티에도 같은 인덱스를 {@code @Table(indexes=...)}로 선언해 두었으므로(개선
     * 작업), {@code ddl-auto: update}만으로 만든 DB에도 이 인덱스들이 전부 있어야 한다 — 그래야
     * "기존 DB를 baseline만 하고 끝내도 된다"는 전제가 실제로 성립한다.
     */
    @Test
    @DisplayName("O01: ddl-auto:update로 만든 기존 DB에도 raw SQL 마이그레이션이 선언한 인덱스가 전부 있다")
    void existingDevDatabaseHasAllIndexesThatRawMigrationsDeclare() {
        String url = existingDevDatabase();
        JdbcTemplate jdbc = jdbc(url);

        // V23(PERF-05)이 엔티티에서 IX_OUTBOX_MAILS_USER 선언을 지웠으므로(IX_OUTBOX_MAILS_USER_KIND가
        // 왼쪽 접두사로 이미 포함) ddl-auto: update로 만든 이 DB에도 더는 생기지 않는다 — 그래서
        // outbox_mails 기대 목록에서도 함께 뺐다. V25(BE-11)가 같은 이유로 IX_COMMENTS_POST(다른
        // 인덱스가 왼쪽 접두사로 포함), IX_OUTBOX_MAILS_STATUS_NEXT_ATTEMPT(IX_OUTBOX_MAILS_STATUS_ID가
        // claim 쿼리를 이미 커버), IX_USERS_WITHDRAWN_AT(단독으로 쓰는 쿼리가 없음)를 마저 지웠다.
        Map<String, List<String>> expectedIndexesByTable = Map.of(
                "users", List.of("IX_USERS_SUSPENDED_UNTIL", "IX_USERS_ROLE_CREATED_AT"),
                "posts", List.of("IX_POSTS_CATEGORY_ID", "IX_POSTS_VIEW_COUNT"),
                "comments", List.of("IX_COMMENTS_POST_PARENT_ID"),
                "post_images", List.of("IX_POST_IMAGES_OWNER", "IX_POST_IMAGES_STATUS_CREATED_AT_ID"),
                "email_verification_tokens", List.of("IX_EVT_EXPIRES_AT"),
                "password_reset_tokens", List.of("IX_PASSWORD_RESET_TOKENS_EXPIRES_AT", "IX_PASSWORD_RESET_TOKENS_USER"),
                "reports", List.of("IX_REPORTS_STATUS_ID", "IX_REPORTS_TARGET"),
                "outbox_mails", List.of(
                        "IX_OUTBOX_MAILS_STATUS_ID", "IX_OUTBOX_MAILS_STATUS_UPDATED",
                        "IX_OUTBOX_MAILS_USER_KIND"));

        expectedIndexesByTable.forEach((table, indexNames) -> indexNames.forEach(indexName ->
                assertThat(indexExists(jdbc, table, indexName))
                        .as("%s.%s", table, indexName)
                        .isTrue()));
    }

    /**
     * 한 번 전환한 DB는 그 뒤로 평범하게 재기동된다. Flyway가 이미 baseline된 DB에서 아무것도
     * 다시 실행하지 않아야 한다 — 재기동마다 스키마를 건드리면 그것이 곧 사고다.
     */
    @Test
    @DisplayName("전환 후 다시 기동해도 마이그레이션이 다시 실행되지 않는다")
    void restartingAfterTheUpgradeChangesNothing() {
        String url = existingDevDatabase();
        String latest = latestMigrationVersion();
        migrateWithBaseline(url, latest);
        Long before = historyCount(url);

        try (ConfigurableApplicationContext context = boot(url,
                "--spring.jpa.hibernate.ddl-auto=validate",
                "--spring.flyway.enabled=true",
                "--spring.flyway.baseline-on-migrate=true",
                "--spring.flyway.baseline-version=" + latest,
                "--spring.session.jdbc.initialize-schema=never")) {
            assertThat(context.isRunning()).isTrue();
        }

        assertThat(historyCount(url))
                .as("재기동이 이력에 새 줄을 남겼다면 무언가를 다시 실행한 것이다")
                .isEqualTo(before);
    }

    /**
     * 새 스키마에 {@code local} 프로파일과 같은 방식({@code ddl-auto: update}, Flyway 없음)으로
     * 앱을 띄웠다 내려, 개발자의 기존 DB와 같은 모양을 만든다.
     */
    private static String existingDevDatabase() {
        dropEverything();

        String url = mariadb.getJdbcUrl();
        try (ConfigurableApplicationContext context = boot(url,
                "--spring.jpa.hibernate.ddl-auto=update",
                "--spring.flyway.enabled=false",
                // local 프로파일이 재기동용 멱등 SQL로 만드는 세션 테이블까지 같은 모양으로 둔다.
                "--spring.session.jdbc.initialize-schema=always")) {
            assertThat(context.isRunning()).isTrue();
        }
        return url;
    }

    /**
     * 스키마를 통째로 비운다. 테스트마다 "아직 Flyway를 모르는 DB"에서 시작해야 하기 때문이다.
     * <p>
     * 컨테이너의 {@code test} 계정에는 {@code CREATE DATABASE} 권한이 없어 테스트마다 새 스키마를
     * 만들 수 없다. 대신 테이블을 전부 지운다 — FK가 얽혀 있으므로 검사를 잠시 끈다.
     */
    private static void dropEverything() {
        JdbcTemplate jdbc = jdbc(mariadb.getJdbcUrl());
        List<String> tables = jdbc.queryForList(
                "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE()",
                String.class);

        // 반드시 한 커넥션에서 처리한다. FOREIGN_KEY_CHECKS는 세션 변수인데
        // DriverManagerDataSource는 호출마다 새 커넥션을 열어, 따로 실행하면 곧바로 사라진다.
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET FOREIGN_KEY_CHECKS = 0");
                for (String table : tables) {
                    statement.execute("DROP TABLE IF EXISTS `" + table + "`");
                }
                statement.execute("SET FOREIGN_KEY_CHECKS = 1");
            }
            return null;
        });
    }

    private static void migrateWithBaseline(String url, String baselineVersion) {
        Flyway.configure()
                .dataSource(url, mariadb.getUsername(), mariadb.getPassword())
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion(baselineVersion)
                .load()
                .migrate();
    }

    /**
     * 앱을 실제로 띄운다.
     * <p>
     * 프로파일을 명시적으로 비우는 것이 중요하다. build.gradle.kts가 모든 Test 태스크에
     * {@code spring.profiles.active=test}를 시스템 프로퍼티로 걸어 두어, 그대로 두면 H2 설정이
     * 켜져 컨테이너가 아니라 인메모리 DB를 검증하게 된다.
     */
    private static ConfigurableApplicationContext boot(String url, String... extraArgs) {
        String[] args = new String[extraArgs.length + 6];
        args[0] = "--spring.profiles.active=upgrade-rehearsal";
        args[1] = "--spring.datasource.url=" + url;
        args[2] = "--spring.datasource.username=" + mariadb.getUsername();
        args[3] = "--spring.datasource.password=" + mariadb.getPassword();
        args[4] = "--server.port=0";
        args[5] = "--app.security.email-encryption-key=rehearsal-key";
        System.arraycopy(extraArgs, 0, args, 6, extraArgs.length);

        return new SpringApplicationBuilder(KraftApplication.class).run(args);
    }

    /** {@code V7__outbox_mails.sql} → {@code "7"}. 손으로 적으면 V8을 더할 때 방치된다. */
    private static String latestMigrationVersion() {
        try (var files = Files.list(Path.of("src/main/resources/db/migration"))) {
            return files.map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith("V") && name.endsWith(".sql"))
                    .map(name -> name.substring(1, name.indexOf("__")))
                    .max(Comparator.comparingInt(Integer::parseInt))
                    .orElseThrow();
        } catch (IOException e) {
            throw new IllegalStateException("마이그레이션 목록을 읽지 못했습니다.", e);
        }
    }

    private static boolean indexExists(JdbcTemplate jdbc, String table, String indexName) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.STATISTICS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ?",
                Long.class, table, indexName);
        return count != null && count > 0;
    }

    private static Long historyCount(String url) {
        return jdbc(url).queryForObject("SELECT COUNT(*) FROM flyway_schema_history", Long.class);
    }

    /**
     * MariaDB 드라이버는 {@code runtimeOnly} 의존이라 컴파일 시점에 클래스를 참조할 수 없다.
     * 이름으로 지정하는 {@link DriverManagerDataSource}를 쓴다.
     */
    private static JdbcTemplate jdbc(String url) {
        DriverManagerDataSource source = new DriverManagerDataSource(url, mariadb.getUsername(), mariadb.getPassword());
        source.setDriverClassName(mariadb.getDriverClassName());
        return new JdbcTemplate(source);
    }
}
