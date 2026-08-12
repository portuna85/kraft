package com.kraft.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.OptionalInt;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * H-04 회귀 테스트: INCR 이후 EXPIRE가 별도 호출로 분리되어 있던 이전 구현은, EXPIRE가
 * 유실되면(예: 그 사이 프로세스가 죽음) 그 IP의 카운터 key가 만료 없이 영구히 남아
 * count가 계속 누적되고 절대 정정되지 않았다(key가 IP별 고정 문자열이라 시간 버킷
 * 재사용으로도 복구되지 않음). 실제 Redis를 대상으로 Lua 스크립트가 그 상태를
 * 다음 호출에서 복구하는지 검증한다.
 */
@Testcontainers
@DisplayName("RedisRateLimitCounter 통합 테스트 (실 Redis)")
class RedisRateLimitCounterIntegrationTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    static LettuceConnectionFactory connectionFactory;
    static StringRedisTemplate redisTemplate;

    @BeforeAll
    static void startRedis() {
        connectionFactory = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
    }

    @AfterAll
    static void stopRedis() {
        connectionFactory.destroy();
    }

    private RedisRateLimitCounter counter;

    @BeforeEach
    void setUp() {
        counter = new RedisRateLimitCounter(redisTemplate, new SimpleMeterRegistry());
    }

    private static String freshKey() {
        return "ratelimit:test:" + UUID.randomUUID();
    }

    @Test
    @DisplayName("첫 호출은 1을 반환하고 만료 시간을 설정한다")
    void incrementAndGet_firstCallSetsExpiry() {
        String key = freshKey();

        OptionalInt result = counter.incrementAndGet(key, 60);

        assertThat(result).isEqualTo(OptionalInt.of(1));
        Long ttl = redisTemplate.getExpire(key);
        assertThat(ttl).isNotNull();
        assertThat(ttl).isGreaterThan(0L).isLessThanOrEqualTo(60L);
    }

    @Test
    @DisplayName("연속 호출은 카운트를 누적하면서 만료 시간을 유지한다")
    void incrementAndGet_accumulatesCountAndKeepsExpiry() {
        String key = freshKey();

        counter.incrementAndGet(key, 60);
        counter.incrementAndGet(key, 60);
        OptionalInt result = counter.incrementAndGet(key, 60);

        assertThat(result).isEqualTo(OptionalInt.of(3));
        assertThat(redisTemplate.getExpire(key)).isGreaterThan(0L);
    }

    @Test
    @DisplayName("만료 없이 남아있던 카운터는 다음 호출에서 만료 시간이 복구된다")
    void incrementAndGet_repairsMissingTtl() {
        String key = freshKey();
        // EXPIRE 유실을 재현: SET으로 만료 없는 카운터를 직접 만든다.
        redisTemplate.opsForValue().set(key, "5");
        assertThat(redisTemplate.getExpire(key)).isEqualTo(-1L);

        OptionalInt result = counter.incrementAndGet(key, 60);

        assertThat(result).isEqualTo(OptionalInt.of(6));
        Long ttl = redisTemplate.getExpire(key);
        assertThat(ttl).isNotNull();
        assertThat(ttl).isGreaterThan(0L).isLessThanOrEqualTo(60L);
    }

    @Test
    @DisplayName("윈도 만료 후에는 카운터가 1부터 재시작한다")
    void incrementAndGet_restartsAfterWindowExpires() throws InterruptedException {
        String key = freshKey();

        counter.incrementAndGet(key, 1);
        Thread.sleep(Duration.ofMillis(1_500).toMillis());
        OptionalInt result = counter.incrementAndGet(key, 1);

        assertThat(result).isEqualTo(OptionalInt.of(1));
    }
}
