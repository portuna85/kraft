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
}
