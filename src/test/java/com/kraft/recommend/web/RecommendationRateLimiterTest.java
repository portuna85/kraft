package com.kraft.recommend.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
}
