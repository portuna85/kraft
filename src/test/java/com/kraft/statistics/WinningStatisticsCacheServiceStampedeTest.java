package com.kraft.statistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * BE-STAT-02(docs/improvement.md): {@code @Cacheable} 5개 전부 {@code sync = true}가 없어
 * TTL 만료 순간 도착한 N개 요청이 전부 미스로 판정되던 캐시 스탬피드를 재현한다. 캐시를 비운
 * 상태에서 16개 스레드를 {@link WinningStatisticsCacheService#getPatternStats()}에 동시
 * 투입해, {@code sync = true} 덕에 실제 리포지토리 조회는 1스레드 분량(TYPE 3종 × 1 = 3회)만
 * 일어나는지 확인한다 — 이전(sync 없음) 구현이었다면 최대 16 × 3 = 48회가 났을 것이다.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("당첨 통계 캐시 서비스 — 캐시 스탬피드 방지 테스트 (BE-STAT-02)")
class WinningStatisticsCacheServiceStampedeTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final OffsetDateTime NOW = OffsetDateTime.now(Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), KST));
    private static final int CONCURRENT_REQUESTS = 16;

    @Autowired
    private WinningStatisticsCacheService service;

    @Autowired
    private CacheManager cacheManager;

    @MockitoSpyBean
    private PatternStatsSummaryRepository patternStatsSummaryRepository;

    @BeforeEach
    void setUp() {
        cacheManager.getCacheNames().forEach(name -> {
            var cache = cacheManager.getCache(name);
            if (cache != null) {
                cache.clear();
            }
        });
        patternStatsSummaryRepository.deleteAll();
        insertFullPatternRows();
    }

    @Test
    @DisplayName("TTL 만료 직후 16개 동시 요청이 스탬피드 없이 1스레드 분량(3회)만 리포지토리를 탄다")
    void concurrentCacheMisses_onlyOneThreadHitsRepository() throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
        CountDownLatch ready = new CountDownLatch(CONCURRENT_REQUESTS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(CONCURRENT_REQUESTS);

        for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    service.getPatternStats();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        start.countDown();
        boolean completed = done.await(10, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(completed).as("모든 스레드가 시간 내 완료해야 한다").isTrue();
        // sync = true가 없었다면 최대 CONCURRENT_REQUESTS × 3(odd/high/sum)회까지 났을 것이다.
        verify(patternStatsSummaryRepository, times(1)).findByStatTypeOrderByBucketKeyAsc(WinningStatisticsCacheService.TYPE_ODD_COUNT);
        verify(patternStatsSummaryRepository, times(1)).findByStatTypeOrderByBucketKeyAsc(WinningStatisticsCacheService.TYPE_HIGH_COUNT);
        verify(patternStatsSummaryRepository, times(1)).findByStatTypeOrderByBucketKeyAsc(WinningStatisticsCacheService.TYPE_SUM_BUCKET);
        verify(patternStatsSummaryRepository, times(3)).findByStatTypeOrderByBucketKeyAsc(any());
    }

    private void insertFullPatternRows() {
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
}
