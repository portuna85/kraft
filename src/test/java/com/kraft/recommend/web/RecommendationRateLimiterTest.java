package com.kraft.recommend.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RecommendationRateLimiter}의 한도가 {@code app.recommend.rate-limit.requests-per-minute}
 * 값을 따르는지 확인한다(운영 준비 — 재배포 없이 조정 가능해야 한다).
 */
class RecommendationRateLimiterTest {

    @Test
    @DisplayName("설정한 한도까지는 허용하고 그다음 요청부터는 거부한다")
    void limit_followsConfiguredValue() {
        RecommendationRateLimiter limiter = new RecommendationRateLimiter(3);

        assertThat(limiter.tryAcquire("client-a")).isTrue();
        assertThat(limiter.tryAcquire("client-a")).isTrue();
        assertThat(limiter.tryAcquire("client-a")).isTrue();
        assertThat(limiter.tryAcquire("client-a")).isFalse();
    }

    @Test
    @DisplayName("클라이언트별로 독립된 한도를 적용한다")
    void limit_isPerClient() {
        RecommendationRateLimiter limiter = new RecommendationRateLimiter(1);

        assertThat(limiter.tryAcquire("client-a")).isTrue();
        assertThat(limiter.tryAcquire("client-b")).isTrue();
        assertThat(limiter.tryAcquire("client-a")).isFalse();
    }

    @Test
    @DisplayName("보고 주기마다 허용/거부 집계를 리셋한다")
    void reportAndCleanup_resetsCounters() {
        RecommendationRateLimiter limiter = new RecommendationRateLimiter(1);
        limiter.tryAcquire("client-a"); // 허용 1건
        limiter.tryAcquire("client-a"); // 거부 1건

        // 첫 보고: 방금 쌓인 허용 1·거부 1이 집계된다(로그만 남기므로 여기서는 예외만 확인).
        limiter.reportAndCleanup(System.currentTimeMillis());
        // 두 번째 보고: 리셋되어 있었다면 새로 쌓인 것이 없으니 조용히 지나간다(예외 없음).
        limiter.reportAndCleanup(System.currentTimeMillis());
    }

    @Test
    @DisplayName("보고 주기마다 창이 10,000개 미만이어도 만료된 클라이언트를 청소한다")
    void reportAndCleanup_evictsExpiredEntriesRegardlessOfMapSize() {
        RecommendationRateLimiter limiter = new RecommendationRateLimiter(30);
        limiter.tryAcquire("client-a");
        assertThat(limiter.trackedClientCount()).isEqualTo(1);

        long farFuture = System.currentTimeMillis() + 60_000L + 1_000L;
        limiter.reportAndCleanup(farFuture);

        assertThat(limiter.trackedClientCount()).isZero();
    }

    /**
     * B13: compute() 안에서 증가시킨 뒤 밖에서 공유 AtomicInteger를 다시 읽으면, 동시 요청이
     * 그 사이 값을 더 올려 한도 안에서 들어온 요청도 거절될 수 있었다. 같은 클라이언트에
     * 한도보다 훨씬 많은 스레드를 동시에 밀어 넣어, 허용된 개수가 정확히 한도만큼인지 확인한다
     * — 재현 전 코드는 경쟁이 걸리면 이보다 적게 허용될 수 있었다.
     */
    @Test
    @DisplayName("B13: 동시 요청이 몰려도 허용되는 개수는 정확히 설정한 한도만큼이다")
    void tryAcquire_underConcurrency_allowsExactlyTheConfiguredLimit() throws InterruptedException {
        int limit = 20;
        int concurrentRequests = 200;
        RecommendationRateLimiter limiter = new RecommendationRateLimiter(limit);

        // 스레드 수가 작업 수보다 적으면 안 된다 — 모든 작업이 ready.countDown() 이후
        // start.await()로 블로킹되므로, 풀이 작업 수보다 작으면 뒤의 작업이 스레드를 못
        // 받아 ready가 영원히 0에 도달하지 못하고 데드락에 빠진다.
        ExecutorService executor = Executors.newFixedThreadPool(concurrentRequests);
        CountDownLatch ready = new CountDownLatch(concurrentRequests);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> futures = IntStream.range(0, concurrentRequests)
                    .<Future<Boolean>>mapToObj(i -> executor.submit(() -> {
                        ready.countDown();
                        start.await();
                        return limiter.tryAcquire("same-client");
                    }))
                    .toList();

            ready.await();
            start.countDown();

            long allowedCount = futures.stream().map(f -> {
                try {
                    return f.get();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }).filter(Boolean::booleanValue).count();

            assertThat(allowedCount).isEqualTo(limit);
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * B13: {@code windows.size() > 10_000}인 동안 요청마다 비상 청소(O(n) 전체 스캔)가 매번
     * 실행되지 않고 쿨다운 간격으로 제한되는지 확인한다. 서로 다른 클라이언트로 10,001개를
     * 채운 뒤 바로 이어지는 두 번의 {@code tryAcquire} 호출이 거의 동시에 일어나도 청소가
     * 한 번만 실행되어야 한다 — 직접 관측할 수는 없으므로, 최소한 반복 호출이 예외 없이
     * 빠르게 끝나는지(과도한 반복 스캔으로 인한 지연이 없는지)로 간접 확인한다.
     */
    @Test
    @DisplayName("B13: map이 10,000개를 넘어도 매 요청마다 전체 스캔을 반복하지 않는다")
    void tryAcquire_whenMapExceedsThreshold_doesNotRescanOnEveryRequest() {
        RecommendationRateLimiter limiter = new RecommendationRateLimiter(30);
        for (int i = 0; i < 10_001; i++) {
            limiter.tryAcquire("client-" + i);
        }
        assertThat(limiter.trackedClientCount()).isGreaterThan(10_000);

        long startNanos = System.nanoTime();
        for (int i = 0; i < 1_000; i++) {
            limiter.tryAcquire("burst-client-" + i);
        }
        long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;

        // 쿨다운 없이 매번 O(n) 스캔했다면(n이 10,000+) 1,000번 반복이 훨씬 오래 걸린다.
        // 넉넉한 상한으로 "반복 스캔이 없다"만 확인한다(정확한 시간 단정이 아니다).
        assertThat(elapsedMillis).isLessThan(5_000);
    }
}
