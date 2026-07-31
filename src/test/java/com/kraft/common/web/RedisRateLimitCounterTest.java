package com.kraft.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.OptionalInt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
@DisplayName("RedisRateLimitCounter 단위 테스트")
class RedisRateLimitCounterTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RedisRateLimitCounter counter;

    @BeforeEach
    void setUp() {
        counter = new RedisRateLimitCounter(redisTemplate);
    }

    @Test
    @DisplayName("이 key로 처음 증가시킨 요청(count==1)에만 만료 시간을 건다")
    void incrementAndGet_setsExpiryOnlyOnFirstCall() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.increment("ratelimit:public:1.2.3.4")).willReturn(1L);

        OptionalInt result = counter.incrementAndGet("ratelimit:public:1.2.3.4", 60);

        assertThat(result).isEqualTo(OptionalInt.of(1));
        verify(redisTemplate).expire(eq("ratelimit:public:1.2.3.4"), eq(Duration.ofSeconds(60)));
    }

    @Test
    @DisplayName("두 번째 이후 호출은 만료 시간을 다시 걸지 않는다")
    void incrementAndGet_doesNotResetExpiryAfterFirstCall() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.increment("k")).willReturn(2L);

        OptionalInt result = counter.incrementAndGet("k", 60);

        assertThat(result).isEqualTo(OptionalInt.of(2));
        verify(redisTemplate, never()).expire(any(), any(Duration.class));
    }

    @Test
    @DisplayName("Redis 연결 실패 시 예외를 던지지 않고 fail-open(empty)으로 처리한다")
    void incrementAndGet_failsOpenOnRedisError() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.increment("k")).willThrow(new QueryTimeoutException("timeout"));

        OptionalInt result = counter.incrementAndGet("k", 60);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("INCR이 null을 반환하면 fail-open(empty)으로 처리한다")
    void incrementAndGet_failsOpenWhenIncrementReturnsNull() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.increment("k")).willReturn(null);

        OptionalInt result = counter.incrementAndGet("k", 60);

        assertThat(result).isEmpty();
    }
}
