package com.kraft.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.kraft.common.config.SecurityProperties;
import java.util.OptionalInt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("InMemoryRateLimitCounter 단위 테스트")
class InMemoryRateLimitCounterTest {

    private InMemoryRateLimitCounter counter;

    @BeforeEach
    void setUp() {
        counter = new InMemoryRateLimitCounter(new SecurityProperties("172.28.0.0/16", 120, 10_000, "in-memory"));
    }

    @Test
    @DisplayName("같은 key로 호출할 때마다 값이 1씩 증가한다")
    void incrementAndGet_incrementsSameKey() {
        assertThat(counter.incrementAndGet("k", 60)).isEqualTo(OptionalInt.of(1));
        assertThat(counter.incrementAndGet("k", 60)).isEqualTo(OptionalInt.of(2));
        assertThat(counter.incrementAndGet("k", 60)).isEqualTo(OptionalInt.of(3));
    }

    @Test
    @DisplayName("다른 key는 독립적으로 카운트된다(네임스페이스 접두어로 충돌 방지 전제)")
    void incrementAndGet_isolatesDifferentKeys() {
        assertThat(counter.incrementAndGet("ratelimit:public:1.2.3.4", 60)).isEqualTo(OptionalInt.of(1));
        assertThat(counter.incrementAndGet("ratelimit:community-write:1", 60)).isEqualTo(OptionalInt.of(1));
        assertThat(counter.incrementAndGet("ratelimit:public:1.2.3.4", 60)).isEqualTo(OptionalInt.of(2));
    }

    @Test
    @DisplayName("항상 값을 반환한다 — fail-open(empty)로 빠지지 않는다")
    void incrementAndGet_neverReturnsEmpty() {
        assertThat(counter.incrementAndGet("k", 60)).isNotEmpty();
    }
}
