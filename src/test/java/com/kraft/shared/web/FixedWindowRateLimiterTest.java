package com.kraft.shared.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * {@link FixedWindowRateLimiter}의 한도·창 만료·키 상한 동작(BE-13). 로그인·가입·글쓰기 속도
 * 제한이 모두 이 클래스 위에 있는데 전용 테스트가 없었다.
 */
class FixedWindowRateLimiterTest {

    private static final long LONG_WINDOW = Duration.ofMinutes(10).toMillis();

    @Test
    @DisplayName("창 안에서 한도까지만 허용하고 그 뒤는 거절한다")
    void allowsUpToLimitThenRejects() {
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter("test", 3, LONG_WINDOW);

        assertThat(limiter.tryAcquire("a")).isTrue();
        assertThat(limiter.tryAcquire("a")).isTrue();
        assertThat(limiter.tryAcquire("a")).isTrue();
        assertThat(limiter.tryAcquire("a")).isFalse();
    }

    @Test
    @DisplayName("키마다 따로 센다")
    void countsEachKeySeparately() {
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter("test", 1, LONG_WINDOW);

        assertThat(limiter.tryAcquire("a")).isTrue();
        assertThat(limiter.tryAcquire("a")).isFalse();
        assertThat(limiter.tryAcquire("b")).isTrue();
    }

    @Test
    @DisplayName("창이 끝나면 다시 허용한다")
    void allowsAgainAfterWindowEnds() {
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter("test", 1, 50);
        assertThat(limiter.tryAcquire("a")).isTrue();
        assertThat(limiter.tryAcquire("a")).isFalse();

        await().atMost(Duration.ofSeconds(3)).until(() -> limiter.tryAcquire("a"));
    }

    @Test
    @DisplayName("BE-13: 키가 가득 차도 한 번만 요청하고 지나간 키를 비워 새 사용자를 받는다")
    void whenFull_evictsSingleHitKeysAndAdmitsNewKey() {
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter("test", 5, LONG_WINDOW, 100);
        for (int i = 0; i < 100; i++) {
            assertThat(limiter.tryAcquire("flood-" + i)).isTrue();
        }
        assertThat(limiter.trackedClientCount()).isEqualTo(100);

        // 예전에는 가득 찬 동안 모든 새 키가 거절됐다.
        assertThat(limiter.tryAcquire("real-user")).isTrue();

        assertThat(limiter.trackedClientCount()).isLessThanOrEqualTo(100);
    }

    @Test
    @DisplayName("BE-13: 가득 차서 비울 때도 한도에 걸려 제한 중인 키의 카운터는 지우지 않는다")
    void whenFull_keepsCountersOfKeysBeingThrottled() {
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter("test", 3, LONG_WINDOW, 100);
        for (int i = 0; i < 4; i++) {
            limiter.tryAcquire("attacker");
        }
        assertThat(limiter.tryAcquire("attacker")).isFalse();
        for (int i = 0; i < 99; i++) {
            limiter.tryAcquire("flood-" + i);
        }

        assertThat(limiter.tryAcquire("real-user")).isTrue();

        // 자리를 만드느라 제한 중인 키까지 지웠다면 여기서 다시 허용되어 버린다.
        assertThat(limiter.tryAcquire("attacker")).isFalse();
    }

    @Test
    @DisplayName("BE-13: 모든 키가 활발히 쓰이는 중이면(비울 키가 없으면) 새 키는 거절하고 기존 키는 계속 센다")
    void whenFullOfActiveKeys_rejectsNewKeyAndKeepsExistingCounts() {
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter("test", 5, LONG_WINDOW, 10);
        for (int i = 0; i < 10; i++) {
            limiter.tryAcquire("active-" + i);
            limiter.tryAcquire("active-" + i);
        }

        assertThat(limiter.tryAcquire("newcomer")).isFalse();

        assertThat(limiter.trackedClientCount()).isEqualTo(10);
        assertThat(limiter.tryAcquire("active-0")).isTrue();
    }
}
