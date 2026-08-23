package com.kraft.statistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.kraft.common.error.ApiErrorCode;
import com.kraft.common.error.ApiException;
import io.micrometer.core.instrument.MeterRegistry;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * BE-STAT-01(docs/improvement.md): 완전성 검사 실패 후 재계산 폴백이 실제로 outcome 4가지
 * (rebuilt/skipped_complete/failed/still_incomplete)로 정확히 분류되는지 — 특히 예전에는
 * lock 경합/진짜 실패/재시도 후에도 불완전인 경우가 전부 "기존 데이터로 200"에 수렴했다는
 * 점을 각각 독립적으로 검증한다(문서 지침: "lock 경합 / DB 실패 / 부분 손상을 각각 독립
 * 테스트한다"). {@link WinningStatisticsCacheServicePartialCorruptionTest}는 정상적으로
 * 재계산이 성공하는 경로(rebuilt)를 이미 담당하므로 여기서는 다루지 않는다.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("당첨 통계 캐시 서비스 — 읽기 폴백 결과 분류 테스트 (BE-STAT-01)")
class WinningStatisticsCacheServiceReadFallbackTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final OffsetDateTime NOW = OffsetDateTime.now(Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), KST));
    private static final int COMPANION_TOP_LIMIT = 990;

    @Autowired
    private WinningStatisticsCacheService service;

    @Autowired
    private CompanionPairSummaryRepository companionPairSummaryRepository;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private StatisticsSummaryRebuilder summaryRebuilder;

    private TransactionTemplate requiresNewTransactionTemplate;

    @BeforeEach
    void setUp() {
        requiresNewTransactionTemplate = new TransactionTemplate(transactionManager);
        requiresNewTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        cacheManager.getCacheNames().forEach(name -> {
            var cache = cacheManager.getCache(name);
            if (cache != null) {
                cache.clear();
            }
        });
        // 의도적으로 불완전한 상태로 시작한다 — 모든 테스트가 완전성 검사 실패로 폴백
        // 경로를 타야 하므로.
        companionPairSummaryRepository.deleteAll();
    }

    @Test
    @DisplayName("lock 경합(SKIPPED) 후 다른 인스턴스가 짧은 재시도 예산 안에 완료하면 예외 없이 반환하고 skipped_complete를 기록한다")
    void skippedThenBecomesCompleteWithinRetryBudget_returnsWithoutException() {
        given(summaryRebuilder.rebuildAllSummaries())
                .willReturn(StatisticsSummaryRebuilder.RebuildOutcome.SKIPPED);
        double before = fallbackCount("skipped_complete");

        // 재시도 예산(최대 3회 × 200ms) 안에 "다른 인스턴스"가 완료한 것처럼 별도 트랜잭션에서
        // 완전한 데이터를 늦게 채운다.
        Thread writer = new Thread(() -> {
            sleepQuietly(80);
            requiresNewTransactionTemplate.executeWithoutResult(status -> insertFullCompanionRows());
        });
        writer.start();

        CompanionStatsResponse response = service.getCompanionStats();

        assertThat(response.topPairs()).hasSize(COMPANION_TOP_LIMIT);
        assertThat(fallbackCount("skipped_complete")).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("재계산이 실제로 실패하면 예외가 전파되고 failed를 기록한다 (예전엔 200으로 위장됐다)")
    void rebuildThrows_propagatesExceptionAndRecordsFailed() {
        given(summaryRebuilder.rebuildAllSummaries())
                .willThrow(new IllegalStateException("simulated DB write failure"));
        double before = fallbackCount("failed");

        assertThatThrownBy(() -> service.getCompanionStats())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("simulated DB write failure");

        assertThat(fallbackCount("failed")).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("SKIPPED 후 재시도 예산을 다 써도 여전히 불완전하면 STATISTICS_NOT_READY(503)를 던진다")
    void skippedAndNeverCompletes_throwsStatisticsNotReady() {
        given(summaryRebuilder.rebuildAllSummaries())
                .willReturn(StatisticsSummaryRebuilder.RebuildOutcome.SKIPPED);
        double before = fallbackCount("still_incomplete");

        assertThatThrownBy(() -> service.getCompanionStats())
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode())
                        .isEqualTo(ApiErrorCode.STATISTICS_NOT_READY.name()));

        assertThat(fallbackCount("still_incomplete")).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("재계산이 SUCCESS를 반환했는데도 데이터가 불완전하면 STATISTICS_NOT_READY(503)를 던진다")
    void rebuildSucceedsButDataStillIncomplete_throwsStatisticsNotReady() {
        // rebuilder 계약 위반(H-01 이후 있어선 안 되는 상태)을 인위적으로 재현 — SUCCESS를
        // 반환하지만 실제로는 아무 것도 쓰지 않는다.
        given(summaryRebuilder.rebuildAllSummaries())
                .willReturn(StatisticsSummaryRebuilder.RebuildOutcome.SUCCESS);
        double before = fallbackCount("still_incomplete");

        assertThatThrownBy(() -> service.getCompanionStats())
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode())
                        .isEqualTo(ApiErrorCode.STATISTICS_NOT_READY.name()));

        assertThat(fallbackCount("still_incomplete")).isEqualTo(before + 1);
    }

    private void insertFullCompanionRows() {
        companionPairSummaryRepository.deleteAll();
        List<CompanionPairSummary> rows = new ArrayList<>();
        for (int a = 1; a <= 44; a++) {
            for (int b = a + 1; b <= 45; b++) {
                rows.add(new CompanionPairSummary(a, b, 1, NOW));
            }
        }
        companionPairSummaryRepository.saveAll(rows);
    }

    private double fallbackCount(String outcome) {
        var counter = meterRegistry.find("statistics.read.fallback").tag("outcome", outcome).counter();
        return counter == null ? 0.0 : counter.count();
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
