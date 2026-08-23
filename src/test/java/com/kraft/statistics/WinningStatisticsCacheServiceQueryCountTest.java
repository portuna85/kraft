package com.kraft.statistics;

import static org.assertj.core.api.Assertions.assertThat;

import com.kraft.QueryCount;
import jakarta.persistence.EntityManagerFactory;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;

/**
 * BE-STAT-03(docs/improvement.md): {@code getCompanionStats()}가 완전한 데이터 상태에서 캐시
 * 미스 1회에 쓰는 statement 수가 3 → 2로 줄었는지 확인한다 — 예전엔 완전성 판정을 위한 별도
 * {@code count()} 쿼리가 있었지만, 이미 페이지 상한(990, 이론적 최댓값)으로 조회한
 * {@code findAllByOrderBy...} 결과의 {@code size()}가 {@code count()}와 동치라 그 쿼리를
 * 아예 없앴다(findAll 1 + sampleRoundCount 1 = 2, count() 쿼리 없음).
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("당첨 통계 캐시 서비스 — 쿼리 수 characterization 테스트 (BE-STAT-03)")
class WinningStatisticsCacheServiceQueryCountTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final OffsetDateTime NOW = OffsetDateTime.now(Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), KST));
    private static final int COMPANION_TOP_LIMIT = 990;

    @Autowired
    private WinningStatisticsCacheService service;

    @Autowired
    private CompanionPairSummaryRepository companionPairSummaryRepository;

    @Autowired
    private StatisticsProjectionStateRepository statisticsProjectionStateRepository;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @BeforeEach
    void setUp() {
        cacheManager.getCacheNames().forEach(name -> {
            var cache = cacheManager.getCache(name);
            if (cache != null) {
                cache.clear();
            }
        });
        companionPairSummaryRepository.deleteAll();
        insertFullCompanionRows();

        // sampleRoundCount()가 findById 미스 → winningNumberRepository.count() 폴백으로 여분의
        // statement를 쓰지 않도록, 실제 rebuild 이후처럼 프로젝션 상태 행을 미리 채운다 —
        // 이 테스트가 재는 대상은 getCompanionStats() 자체의 쿼리 수(BE-STAT-03)지 sampleRoundCount()의
        // 폴백 경로가 아니다.
        StatisticsProjectionState state = new StatisticsProjectionState(StatisticsProjectionState.SINGLETON_ID);
        state.recordSuccess(1, COMPANION_TOP_LIMIT, NOW);
        statisticsProjectionStateRepository.save(state);
    }

    @Test
    @DisplayName("완전한 데이터에서 getCompanionStats() 캐시 미스 1회는 2개의 prepared statement만 실행한다")
    void getCompanionStats_completeData_executesTwoStatements() {
        long statementCount = QueryCount.measure(entityManagerFactory, () -> service.getCompanionStats());

        assertThat(statementCount).isEqualTo(2L);
    }

    private void insertFullCompanionRows() {
        List<CompanionPairSummary> rows = new ArrayList<>();
        for (int a = 1; a <= 44; a++) {
            for (int b = a + 1; b <= 45; b++) {
                rows.add(new CompanionPairSummary(a, b, 1, NOW));
            }
        }
        assertThat(rows).hasSize(COMPANION_TOP_LIMIT);
        companionPairSummaryRepository.saveAll(rows);
    }
}
