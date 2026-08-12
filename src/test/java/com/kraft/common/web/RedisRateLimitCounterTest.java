package com.kraft.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

@ExtendWith(MockitoExtension.class)
@DisplayName("RedisRateLimitCounter 단위 테스트")
class RedisRateLimitCounterTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    private SimpleMeterRegistry meterRegistry;
    private RedisRateLimitCounter counter;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        counter = new RedisRateLimitCounter(redisTemplate, meterRegistry);
    }

    @Test
    @DisplayName("INCR과 EXPIRE를 하나의 Lua 스크립트 실행으로 원자 처리한다")
    void incrementAndGet_executesAtomicIncrementScript() {
        given(redisTemplate.execute(any(RedisScript.class), eq(List.of("ratelimit:public:1.2.3.4")),
                eq("60"))).willReturn(1L);

        OptionalInt result = counter.incrementAndGet("ratelimit:public:1.2.3.4", 60);

        assertThat(result).isEqualTo(OptionalInt.of(1));
    }

    @Test
    @DisplayName("스크립트가 반환한 카운트를 그대로 전달한다")
    void incrementAndGet_returnsScriptResultCount() {
        given(redisTemplate.execute(any(RedisScript.class), eq(List.of("k")), eq("60"))).willReturn(2L);

        OptionalInt result = counter.incrementAndGet("k", 60);

        assertThat(result).isEqualTo(OptionalInt.of(2));
    }

    @Test
    @DisplayName("Redis 연결 실패 시 예외를 던지지 않고 fail-open(empty)으로 처리하며 실패 카운터를 증가시킨다")
    void incrementAndGet_failsOpenOnRedisError() {
        given(redisTemplate.execute(any(RedisScript.class), eq(List.of("k")), eq("60")))
                .willThrow(new QueryTimeoutException("timeout"));

        OptionalInt result = counter.incrementAndGet("k", 60);

        assertThat(result).isEmpty();
        assertThat(meterRegistry.get("kraft.rate_limit.backend_failure").tag("backend", "redis").counter().count())
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("스크립트 실행이 null을 반환하면 fail-open(empty)으로 처리한다")
    void incrementAndGet_failsOpenWhenScriptReturnsNull() {
        given(redisTemplate.execute(any(RedisScript.class), eq(List.of("k")), eq("60"))).willReturn(null);

        OptionalInt result = counter.incrementAndGet("k", 60);

        assertThat(result).isEmpty();
    }
}
