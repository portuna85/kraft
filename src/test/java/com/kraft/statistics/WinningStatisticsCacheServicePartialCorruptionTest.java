package com.kraft.statistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

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

/**
 * P1-07: 패턴(odd/high/sum)·동반 출현 summary가 "완전히 비지는 않았지만 일부만 누락된"
 * 상태를 감지해 재계산을 트리거하는지 검증한다. H-01 이후 StatisticsSummaryRebuilder는
 * 실제로 항상 완전 도메인(7+7+5/990)을 만들므로 이 전제(재계산 호출 시 완전한 정상
 * 상태로 복구된다)는 더 이상 가정이 아니라 실제 계약이지만, 여기서는 여전히
 * StatisticsSummaryRebuilder를 목으로 대체해 이 클래스(WinningStatisticsCacheService)의
 * 완전성 판정·트리거 로직만 독립적으로 검증한다 — 재계산 로직 자체의 정확성(빈/희소/부분
 * 손상 시나리오에서 실제로 완전 도메인을 만드는지)은 StatisticsSummaryRebuilderTest와
 * StatisticsRebuildReadContractTest가 실제 rebuilder로 담당한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("당첨 통계 캐시 서비스 — 패턴·동반 summary 부분 손상 복구 테스트")
class WinningStatisticsCacheServicePartialCorruptionTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final OffsetDateTime NOW = OffsetDateTime.now(Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), KST));
    private static final int COMPANION_TOP_LIMIT = 990;

    @Autowired
    private WinningStatisticsCacheService service;

    @Autowired
    private PatternStatsSummaryRepository patternStatsSummaryRepository;

    @Autowired
    private CompanionPairSummaryRepository companionPairSummaryRepository;

    @Autowired
    private CacheManager cacheManager;

    @MockitoBean
    private StatisticsSummaryRebuilder summaryRebuilder;

    @BeforeEach
    void setUp() {
        cacheManager.getCacheNames().forEach(name -> {
            var cache = cacheManager.getCache(name);
            if (cache != null) {
                cache.clear();
            }
        });
        patternStatsSummaryRepository.deleteAll();
        companionPairSummaryRepository.deleteAll();

        // 재계산이 호출되면(감지 성공) 완전한 정상 상태로 복구된다고 가정한다 — 이 목의
        // 목적은 재계산의 정확성이 아니라, 서비스가 불완전 상태를 감지해 재계산을
        // 호출하는지, 그리고 그 결과를 다시 읽어 응답에 반영하는지를 검증하는 것이다.
        doAnswer(inv -> {
            insertFullPatternRows();
            insertFullCompanionRows();
            return null;
        }).when(summaryRebuilder).rebuildAllSummaries();

        insertFullPatternRows();
        insertFullCompanionRows();
    }

    private void insertFullPatternRows() {
        patternStatsSummaryRepository.deleteAll();
        List<PatternStatsSummary> rows = new ArrayList<>();
        for (String key : List.of("0", "1", "2", "3", "4", "5", "6")) {
            rows.add(new PatternStatsSummary(WinningStatisticsCacheService.TYPE_ODD_COUNT, key, 1, NOW));
            rows.add(new PatternStatsSummary(WinningStatisticsCacheService.TYPE_HIGH_COUNT, key, 1, NOW));
        }
        for (String key : List.of("21-65", "66-110", "111-155", "156-200", "201-255")) {
            rows.add(new PatternStatsSummary(WinningStatisticsCacheService.TYPE_SUM_BUCKET, key, 1, NOW));
        }
        patternStatsSummaryRepository.saveAll(rows);
    }

    private void insertFullCompanionRows() {
        companionPairSummaryRepository.deleteAll();
        List<CompanionPairSummary> rows = new ArrayList<>();
        for (int a = 1; a <= 44; a++) {
            for (int b = a + 1; b <= 45; b++) {
                rows.add(new CompanionPairSummary(a, b, 1, NOW));
            }
        }
        assertThat(rows).hasSize(COMPANION_TOP_LIMIT);
        companionPairSummaryRepository.saveAll(rows);
    }

    @Test
    @DisplayName("ODD_COUNT 버킷이 하나만 빠져도 패턴 summary가 불완전하다고 감지해 재계산한다(T3)")
    void getPatternStats_missingOddCountBucket_triggersFullRebuild() {
        PatternStatsSummary toDelete = patternStatsSummaryRepository
                .findByStatTypeAndBucketKey(WinningStatisticsCacheService.TYPE_ODD_COUNT, "3").orElseThrow();
        patternStatsSummaryRepository.delete(toDelete);

        PatternStatsResponse response = service.getPatternStats();

        verify(summaryRebuilder).rebuildAllSummaries();
        assertThat(response.oddCounts()).hasSize(7);
        assertThat(response.highCounts()).hasSize(7);
        assertThat(response.sumBuckets()).hasSize(5);
    }

    @Test
    @DisplayName("HIGH_COUNT 버킷이 하나만 빠져도 패턴 summary가 불완전하다고 감지해 재계산한다(T3)")
    void getPatternStats_missingHighCountBucket_triggersFullRebuild() {
        PatternStatsSummary toDelete = patternStatsSummaryRepository
                .findByStatTypeAndBucketKey(WinningStatisticsCacheService.TYPE_HIGH_COUNT, "5").orElseThrow();
        patternStatsSummaryRepository.delete(toDelete);

        PatternStatsResponse response = service.getPatternStats();

        verify(summaryRebuilder).rebuildAllSummaries();
        assertThat(response.oddCounts()).hasSize(7);
        assertThat(response.highCounts()).hasSize(7);
        assertThat(response.sumBuckets()).hasSize(5);
    }

    @Test
    @DisplayName("SUM_BUCKET 버킷이 하나만 빠져도 패턴 summary가 불완전하다고 감지해 재계산한다(T3)")
    void getPatternStats_missingSumBucket_triggersFullRebuild() {
        PatternStatsSummary toDelete = patternStatsSummaryRepository
                .findByStatTypeAndBucketKey(WinningStatisticsCacheService.TYPE_SUM_BUCKET, "111-155").orElseThrow();
        patternStatsSummaryRepository.delete(toDelete);

        PatternStatsResponse response = service.getPatternStats();

        verify(summaryRebuilder).rebuildAllSummaries();
        assertThat(response.oddCounts()).hasSize(7);
        assertThat(response.highCounts()).hasSize(7);
        assertThat(response.sumBuckets()).hasSize(5);
    }

    @Test
    @DisplayName("패턴 summary가 완전하면 재계산을 호출하지 않는다")
    void getPatternStats_completeSummary_doesNotTriggerRebuild() {
        service.getPatternStats();

        verify(summaryRebuilder, org.mockito.Mockito.never()).rebuildAllSummaries();
    }

    @Test
    @DisplayName("동반 출현 990쌍 중 일부만 빠져도 불완전하다고 감지해 재계산한다(T4)")
    void getCompanionStats_partiallyCorruptedSummary_triggersFullRebuild() {
        List<CompanionPairSummary> all = companionPairSummaryRepository
                .findAllByOrderByCoCountDescBallAAscBallBAsc(
                        org.springframework.data.domain.PageRequest.of(0, COMPANION_TOP_LIMIT));
        companionPairSummaryRepository.deleteAllInBatch(all.subList(0, 20));
        assertThat(companionPairSummaryRepository.count()).isEqualTo(COMPANION_TOP_LIMIT - 20);

        CompanionStatsResponse response = service.getCompanionStats();

        verify(summaryRebuilder).rebuildAllSummaries();
        assertThat(response.topPairs()).hasSize(COMPANION_TOP_LIMIT);
        assertThat(companionPairSummaryRepository.count()).isEqualTo(COMPANION_TOP_LIMIT);
    }

    @Test
    @DisplayName("특정 번호 기준 조회도 전체 테이블 완전성으로 불완전을 감지해 재계산한다(T4)")
    void getCompanionStatsByBall_partiallyCorruptedSummary_triggersFullRebuild() {
        List<CompanionPairSummary> all = companionPairSummaryRepository
                .findAllByOrderByCoCountDescBallAAscBallBAsc(
                        org.springframework.data.domain.PageRequest.of(0, COMPANION_TOP_LIMIT));
        // 1번 볼과 무관한 쌍만 지워서, per-ball(1번) 조회 결과 자체는 원래도 완전해 보이는
        // 상태를 만든다 — 그래도 전체 테이블 기준으로는 불완전하므로 감지돼야 한다.
        List<CompanionPairSummary> unrelatedToBall1 = all.stream()
                .filter(p -> p.getBallA() != 1 && p.getBallB() != 1)
                .limit(20)
                .toList();
        companionPairSummaryRepository.deleteAllInBatch(unrelatedToBall1);
        assertThat(companionPairSummaryRepository.count()).isEqualTo(COMPANION_TOP_LIMIT - 20);

        CompanionStatsResponse response = service.getCompanionStatsByBall(1);

        verify(summaryRebuilder).rebuildAllSummaries();
        assertThat(response.topPairs()).hasSize(44);
        assertThat(companionPairSummaryRepository.count()).isEqualTo(COMPANION_TOP_LIMIT);
    }
}
