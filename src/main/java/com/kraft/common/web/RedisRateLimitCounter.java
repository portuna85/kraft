package com.kraft.common.web;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

/**
 * 다중 인스턴스 스케일아웃용 레이트리밋 백엔드. INCR과 EXPIRE를 Lua 스크립트로
 * 원자 실행해, 첫 호출 직후 프로세스가 죽어 만료가 누락된 키도 다음 호출에서
 * PTTL &lt; 0을 감지해 자동 복구한다 — key가 IP별 고정 문자열이라(시간 버킷이
 * 아님) 만료가 한번 유실되면 다음 윈도에서 자연 정정되지 않고 count가 계속
 * 누적되어 해당 클라이언트를 무기한 차단할 수 있었다.
 *
 * Redis 연결 실패는 fail-open — 해당 요청의 한도 검사를 건너뛴다. 레이트리밋은
 * 남용 방지용 보조 방어선이지 핵심 가용성 요구사항이 아니므로, Redis 장애가
 * 전체 API 장애로 번지는 것보다 일시적으로 무제한 허용하는 편이 낫다.
 */
@Component
@ConditionalOnProperty(name = "kraft.security.rate-limit-backend", havingValue = "redis")
public class RedisRateLimitCounter implements RateLimitCounter {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimitCounter.class);
    private static final long FAILURE_LOG_THROTTLE_MILLIS = 30_000L;

    private static final RedisScript<Long> INCREMENT_SCRIPT =
            new DefaultRedisScript<>(loadScript("redis/rate-limit-increment.lua"), Long.class);

    private static String loadScript(String classpathLocation) {
        try (var input = new ClassPathResource(classpathLocation).getInputStream()) {
            return StreamUtils.copyToString(input, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("레이트리밋 Lua 스크립트를 읽지 못했다: " + classpathLocation, e);
        }
    }

    private final StringRedisTemplate redisTemplate;
    private final MeterRegistry meterRegistry;
    private final AtomicLong lastFailureLogMillis = new AtomicLong(0);

    public RedisRateLimitCounter(StringRedisTemplate redisTemplate, MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public OptionalInt incrementAndGet(String key, int windowSeconds) {
        try {
            Long count = redisTemplate.execute(INCREMENT_SCRIPT, List.of(key), String.valueOf(windowSeconds));
            if (count == null) {
                log.warn("Redis 레이트리밋 스크립트가 null을 반환했다 — fail-open으로 처리한다. key={}", key);
                return OptionalInt.empty();
            }
            return OptionalInt.of(count.intValue());
        } catch (DataAccessException redisUnavailable) {
            recordFailure(redisUnavailable);
            return OptionalInt.empty();
        }
    }

    private void recordFailure(DataAccessException cause) {
        Counter.builder("kraft.rate_limit.backend_failure")
                .tag("backend", "redis")
                .register(meterRegistry)
                .increment();

        long now = System.currentTimeMillis();
        long last = lastFailureLogMillis.get();
        if (now - last >= FAILURE_LOG_THROTTLE_MILLIS && lastFailureLogMillis.compareAndSet(last, now)) {
            log.warn("Redis 레이트리밋 백엔드 연결 실패 — 이번 요청은 한도 검사 없이 통과시킨다(fail-open). "
                    + "이 경고는 {}ms 간격으로 스로틀된다.", FAILURE_LOG_THROTTLE_MILLIS, cause);
        }
    }
}
