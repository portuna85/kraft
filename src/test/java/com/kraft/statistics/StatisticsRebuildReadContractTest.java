package com.kraft.statistics;

import static org.assertj.core.api.Assertions.assertThat;

import com.kraft.winningnumber.WinningNumber;
import com.kraft.winningnumber.WinningNumberRepository;
import com.kraft.winningnumber.WinningNumberTestFactory;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;

/**
 * H-01 회귀 테스트: 실제 {@link StatisticsSummaryRebuilder}와 실제
 * {@link WinningStatisticsCacheService}를 함께 사용해, 재계산과 완전성 판정이 정말로
 * 서로 맞물리는지 확인한다. 예전에는 rebuilder가 관측된 키만 남기고 나머지를 지웠는데,
 * reader는 고정된 완전 도메인(45/7/7/5/990)을 요구해서 서로 계약이 어긋났다 — 그
 * 결과 데이터가 비었거나 희소할 때는 재계산을 아무리 해도 완전성 검사를 통과하지
 * 못해, 캐시가 비워질 때마다(운영에서는 TTL/evict) 매번 다시 재계산이 트리거되는
 * 루프가 생겼다. 이 테스트는 "첫 재계산 이후에는 반복 읽기가 추가 재계산을 유발하지
 * 않는다"를 rebuild 카운터(메트릭)로 직접 단언해 그 루프의 재발을 막는다.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("통계 재구축-읽기 계약 테스트 (H-01 회귀)")
class StatisticsRebuildReadContractTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Autowired
    private WinningStatisticsCacheService service;

    @Autowired
    private WinningNumberRepository winningNumberRepository;

    @Autowired
    private FrequencySummaryRepository frequencySummaryRepository;

    @Autowired
    private PatternStatsSummaryRepository patternStatsSummaryRepository;

    @Autowired
    private CompanionPairSummaryRepository companionPairSummaryRepository;

    @Autowired
    private StatisticsProjectionStateRepository statisticsProjectionStateRepository;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        clearCaches();
        frequencySummaryRepository.deleteAll();
        patternStatsSummaryRepository.deleteAll();
        companionPairSummaryRepository.deleteAll();
        winningNumberRepository.deleteAll();
        statisticsProjectionStateRepository.deleteAll();
    }

    @Test
    @DisplayName("0라운드: 첫 읽기 이후 반복 읽기는 추가 재계산을 유발하지 않는다")
    void zeroRounds_doesNotRepeatedlyRebuildOnEachRead() {
        assertNoRebuildLoopAcrossRepeatedReads();
    }

    @Test
    @DisplayName("1라운드: 첫 읽기 이후 반복 읽기는 추가 재계산을 유발하지 않는다")
    void singleRound_doesNotRepeatedlyRebuildOnEachRead() {
        winningNumberRepository.save(round(1, 1, 2, 3, 4, 5, 6, 7));
        assertNoRebuildLoopAcrossRepeatedReads();
    }

    @Test
    @DisplayName("희소 데이터(5라운드): 첫 읽기 이후 반복 읽기는 추가 재계산을 유발하지 않는다")
    void sparseRounds_doesNotRepeatedlyRebuildOnEachRead() {
        for (int r = 1; r <= 5; r++) {
            winningNumberRepository.save(round(r, r, r + 1, r + 2, r + 10, r + 20, r + 30, r + 5));
        }
        assertNoRebuildLoopAcrossRepeatedReads();
    }

    @Test
    @DisplayName("부분 손상(행 하나 삭제) 복구 이후에는 반복 읽기가 추가 재계산을 유발하지 않는다")
    void partialCorruption_doesNotRepeatedlyRebuildAfterRecovery() {
        winningNumberRepository.save(round(1, 1, 2, 3, 4, 5, 6, 7));
        clearCaches();
        service.getFrequencyStats();
        service.getPatternStats();
        service.getCompanionStats();

        frequencySummaryRepository.findAll().stream().findFirst()
                .ifPresent(frequencySummaryRepository::delete);

        assertNoRebuildLoopAcrossRepeatedReads();
    }

    private void assertNoRebuildLoopAcrossRepeatedReads() {
        clearCaches();
        service.getFrequencyStats();
        service.getPatternStats();
        service.getCompanionStats();
        double rebuildsAfterFirstRead = totalRebuildCount();

        for (int i = 0; i < 3; i++) {
            clearCaches();
            service.getFrequencyStats();
            service.getPatternStats();
            service.getCompanionStats();
        }

        assertThat(totalRebuildCount())
                .as("첫 읽기 이후 반복 읽기가 추가 재계산을 유발하면 안 된다")
                .isEqualTo(rebuildsAfterFirstRead);
    }

    private void clearCaches() {
        cacheManager.getCacheNames().forEach(name -> {
            var cache = cacheManager.getCache(name);
            if (cache != null) {
                cache.clear();
            }
        });
    }

    private double totalRebuildCount() {
        return meterRegistry.find("statistics.summary.rebuild").counters().stream()
                .mapToDouble(Counter::count)
                .sum();
    }

    private WinningNumber round(int r, int n1, int n2, int n3, int n4, int n5, int n6, int bonus) {
        return WinningNumberTestFactory.create(r, LocalDate.of(2026, 1, r),
                n1, n2, n3, n4, n5, n6, bonus,
                1_000_000_000L, 0L, 0, 0L, 0L,
                OffsetDateTime.now(Clock.system(KST)));
    }
}
