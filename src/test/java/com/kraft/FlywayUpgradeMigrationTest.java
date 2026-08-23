package com.kraft;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DisplayName("Flyway 배포 스냅숏 업그레이드 테스트")
class FlywayUpgradeMigrationTest {

    @Container
    static MariaDBContainer<?> mariadb = new MariaDBContainer<>("mariadb:11.7")
            .withDatabaseName("kraft_upgrade_test")
            .withUsername("test")
            .withPassword("test");

    @Test
    @DisplayName("V33 스키마와 기존 데이터를 V34로 올리고 쿼리 계획용 인덱스를 교체한다")
    void version33Snapshot_upgradesToLatestWithoutDataLoss() throws Exception {
        Flyway.configure()
                .dataSource(mariadb.getJdbcUrl(), mariadb.getUsername(), mariadb.getPassword())
                .target(MigrationVersion.fromVersion("33"))
                .load()
                .migrate();

        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO community_users (provider, provider_id, nickname, created_at)
                    VALUES ('google', 'upgrade-user', 'upgrade', NOW(6))
                    """);
            statement.executeUpdate("""
                    INSERT INTO community_reports (
                        reporter_user_id, target_type, target_id, reason, created_at
                    ) VALUES (1, 'POST', 42, 'SPAM', NOW(6))
                    """);
            statement.executeUpdate("""
                    INSERT INTO winning_number_operation_logs (
                        operation_type, execution_status, round_no, source_detail, message, created_at
                    ) VALUES ('EXTERNAL_COLLECT', 'FAILURE', 1201, 'upgrade', 'failure', NOW(6))
                    """);
        }

        Flyway.configure()
                .dataSource(mariadb.getJdbcUrl(), mariadb.getUsername(), mariadb.getPassword())
                .load()
                .migrate();

        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            assertThat(count(statement, "SELECT COUNT(*) FROM community_reports")).isEqualTo(1);
            assertThat(count(statement, "SELECT COUNT(*) FROM winning_number_operation_logs")).isEqualTo(1);
            assertThat(indexExists(statement, "community_reports", "idx_community_reports_target")).isTrue();
            assertThat(indexExists(statement, "winning_number_operation_logs",
                    "idx_operation_logs_type_status_created")).isTrue();
            assertThat(indexExists(statement, "winning_number_operation_logs",
                    "idx_operation_logs_type_status")).isFalse();
        }
    }

    @Test
    @DisplayName("DB-IDX-01/DB-IDX-02/DB-REC-01: V34 스키마와 기존 데이터를 V35로 올리고 커뮤니티·추천 쿼리 계획 인덱스를 추가한다")
    void version34Snapshot_upgradesToLatestAndAddsCommunityQueryPlanIndexes() throws Exception {
        Flyway.configure()
                .dataSource(mariadb.getJdbcUrl(), mariadb.getUsername(), mariadb.getPassword())
                .target(MigrationVersion.fromVersion("34"))
                .load()
                .migrate();

        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO community_users (provider, provider_id, nickname, created_at)
                    VALUES ('google', 'v35-upgrade-user', 'v35-upgrade', NOW(6))
                    """);
            statement.executeUpdate("""
                    INSERT INTO community_posts (
                        owner_id, author_name_snapshot, title, content, created_at, updated_at
                    ) VALUES (1, 'v35-upgrade', 'title', 'content', NOW(6), NOW(6))
                    """);
            statement.executeUpdate("""
                    INSERT INTO community_comments (
                        post_id, owner_id, author_name_snapshot, content, created_at
                    ) VALUES (1, 1, 'v35-upgrade', 'comment', NOW(6))
                    """);
            statement.executeUpdate("""
                    INSERT INTO recommendation_sets (
                        owner_user_id, strategy, algorithm_version, history_through_round, created_at
                    ) VALUES (1, 'RANDOM', 'v1', 1200, NOW(6))
                    """);
        }

        Flyway.configure()
                .dataSource(mariadb.getJdbcUrl(), mariadb.getUsername(), mariadb.getPassword())
                .load()
                .migrate();

        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            assertThat(count(statement, "SELECT COUNT(*) FROM community_posts")).isEqualTo(1);
            assertThat(count(statement, "SELECT COUNT(*) FROM community_comments")).isEqualTo(1);
            assertThat(count(statement, "SELECT COUNT(*) FROM recommendation_sets")).isEqualTo(1);
            assertThat(indexExists(statement, "community_posts", "idx_community_posts_status_created")).isTrue();
            assertThat(indexExists(statement, "community_comments",
                    "idx_community_comments_post_parent_created")).isTrue();
            assertThat(indexExists(statement, "recommendation_sets",
                    "idx_recommendation_sets_owner_created")).isTrue();
            assertThat(indexExists(statement, "recommendation_sets", "fk_recommendation_sets_owner")).isFalse();
        }
    }

    private static Connection connection() throws Exception {
        return DriverManager.getConnection(mariadb.getJdbcUrl(), mariadb.getUsername(), mariadb.getPassword());
    }

    private static long count(Statement statement, String sql) throws Exception {
        try (ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getLong(1);
        }
    }

    private static boolean indexExists(Statement statement, String tableName, String indexName) throws Exception {
        String sql = "SELECT COUNT(*) FROM information_schema.statistics "
                + "WHERE table_schema = DATABASE() AND table_name = '%s' AND index_name = '%s'"
                .formatted(tableName, indexName);
        return count(statement, sql) > 0;
    }
}
