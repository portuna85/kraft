package com.kraft;

import com.kraft.recommend.RecommendationItem;
import com.kraft.recommend.RecommendationItemRepository;
import com.kraft.recommend.RecommendationSet;
import com.kraft.recommend.RecommendationSetRepository;
import com.kraft.winningnumber.WinningNumberRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Flyway 마이그레이션이 실제 MariaDB에서 오류 없이 적용되고,
 * Hibernate ddl-auto=validate 가 엔티티 매핑과 스키마가 일치함을 확인한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Testcontainers
@DisplayName("데이터베이스 마이그레이션 및 스키마 검증 테스트")
class FlywayMigrationTest {

    @Autowired
    private WinningNumberRepository winningNumberRepository;

    @Autowired
    private RecommendationSetRepository recommendationSetRepository;

    @Autowired
    private RecommendationItemRepository recommendationItemRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Container
    static MariaDBContainer<?> mariadb = new MariaDBContainer<>("mariadb:11.7")
            .withDatabaseName("kraft_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        Flyway.configure()
                .dataSource(mariadb.getJdbcUrl(), mariadb.getUsername(), mariadb.getPassword())
                .load()
                .migrate();
        registry.add("spring.datasource.url", mariadb::getJdbcUrl);
        registry.add("spring.datasource.username", mariadb::getUsername);
        registry.add("spring.datasource.password", mariadb::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.mariadb.jdbc.Driver");
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.defer-datasource-initialization", () -> "false");
        registry.add("spring.sql.init.mode", () -> "never");
    }

    @Test
    @DisplayName("마이그레이션이 성공적으로 적용되고 엔티티와 스키마가 일치한다")
    void migrationsApplySuccessfully_andSchemaMatchesEntities() {
        // 컨텍스트가 로드되면 테스트 통과:
        // 1. 모든 Flyway 마이그레이션이 MariaDB에서 오류 없이 적용됨
        // 2. Hibernate ddl-auto=validate 가 엔티티 매핑과 스키마 일치를 확인함
    }

    @Test
    @DisplayName("recommendation item trigger rejects historical combinations but permits a later winning combination")
    void historicalRecommendationTrigger_respectsHistoryThroughRound() {
        winningNumberRepository.deleteAll();
        recommendationItemRepository.deleteAll();
        recommendationSetRepository.deleteAll();

        winningNumberRepository.save(com.kraft.winningnumber.WinningNumberTestFactory.create(1, LocalDate.of(2026, 1, 3),
                1, 2, 3, 4, 5, 6, 7, 1L, 0L, 0, 0L, 0L, OffsetDateTime.now()));
        winningNumberRepository.save(com.kraft.winningnumber.WinningNumberTestFactory.create(2, LocalDate.of(2026, 1, 10),
                7, 8, 9, 10, 11, 12, 13, 1L, 0L, 0, 0L, 0L, OffsetDateTime.now()));

        RecommendationSet throughRoundOne = recommendationSetRepository.save(new RecommendationSet(
                "test-token-1", "random", "uniform-random-v1", 1,
                "historical-first-prize-v1", null, null, OffsetDateTime.now()));
        RecommendationItem laterWinner = recommendationItemRepository.saveAndFlush(new RecommendationItem(
                throughRoundOne.getId(), 1, "7,8,9,10,11,12", maskOf(7, 8, 9, 10, 11, 12), null, null));
        assertThat(laterWinner.getId()).isNotNull();

        RecommendationSet throughRoundTwo = recommendationSetRepository.save(new RecommendationSet(
                "test-token-2", "random", "uniform-random-v1", 2,
                "historical-first-prize-v1", null, null, OffsetDateTime.now()));
        assertThatThrownBy(() -> recommendationItemRepository.saveAndFlush(new RecommendationItem(
                throughRoundTwo.getId(), 1, "1,2,3,4,5,6", maskOf(1, 2, 3, 4, 5, 6), null, null)))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("historical first-prize combination cannot be persisted");
    }

    @Test
    @DisplayName("V29 이전 INSERT도 마스크를 채우고 역사 조합 방어선을 유지한다")
    void legacyInsertWithoutCombinationMask_remainsCompatibleAndProtected() {
        recommendationItemRepository.deleteAll();
        recommendationSetRepository.deleteAll();
        winningNumberRepository.deleteAll();

        jdbcTemplate.update("""
                INSERT INTO winning_numbers (
                    round_no, draw_date, n1, n2, n3, n4, n5, n6, bonus_number,
                    first_prize_amount, second_prize, second_winners, total_sales,
                    first_accum_amount, version, created_at
                ) VALUES (1, '2026-01-03', 1, 2, 3, 4, 5, 6, 7, 1, 0, 0, 0, 0, 0, NOW(6))
                """);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT combination_mask FROM winning_numbers WHERE round_no = 1", Long.class))
                .isEqualTo(maskOf(1, 2, 3, 4, 5, 6));

        jdbcTemplate.update("""
                INSERT INTO recommendation_sets (
                    client_token_hash, strategy, algorithm_version, history_through_round,
                    locked_numbers, excluded_numbers, created_at
                ) VALUES (?, 'random', 'uniform-random-v1', 1, NULL, NULL, NOW(6))
                """, "legacy-token-hash");
        Long setId = jdbcTemplate.queryForObject(
                "SELECT id FROM recommendation_sets WHERE client_token_hash = ?", Long.class,
                "legacy-token-hash");

        jdbcTemplate.update("""
                INSERT INTO recommendation_items (set_id, position, numbers, score, explanation_codes)
                VALUES (?, 1, '7,8,9,10,11,12', NULL, NULL)
                """, setId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT combination_mask FROM recommendation_items WHERE set_id = ? AND position = 1",
                Long.class, setId)).isEqualTo(maskOf(7, 8, 9, 10, 11, 12));

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO recommendation_items (set_id, position, numbers, score, explanation_codes)
                VALUES (?, 2, '1,2,3,4,5,6', NULL, NULL)
                """, setId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("historical first-prize combination cannot be persisted");
    }

    private static long maskOf(int... numbers) {
        long mask = 0L;
        for (int number : numbers) {
            mask |= 1L << (number - 1);
        }
        return mask;
    }
}
