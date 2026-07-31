package com.kraft.common.web;

import java.time.Duration;
import java.util.OptionalInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 다중 인스턴스 스케일아웃용 레이트리밋 백엔드 — INCR로 원자적으로 증가시키고,
 * 그 key로 처음 증가시킨 요청(count == 1)만 만료 시간을 건다. INCR과 EXPIRE
 * 사이에 이론상 아주 짧은 경합 창이 있어(두 명령이 하나의 트랜잭션이 아님)
 * 극단적으로 드문 경우 만료가 누락된 key가 남을 수 있으나, 다음 윈도에서
 * 같은 key가 재사용되면 자연히 정정된다 — Lua 스크립트로 원자화할 만큼
 * 이득이 크지 않다고 판단(기존 Caffeine 구현도 텀블링 윈도라 최대 ~2배
 * 버스트를 이미 허용하는 수준의 정밀도).
 *
 * Redis 연결 실패는 fail-open — 해당 요청의 한도 검사를 건너뛴다. 레이트리밋은
 * 남용 방지용 보조 방어선이지 핵심 가용성 요구사항이 아니므로, Redis 장애가
 * 전체 API 장애로 번지는 것보다 일시적으로 무제한 허용하는 편이 낫다.
 */
@Component
@ConditionalOnProperty(name = "kraft.security.rate-limit-backend", havingValue = "redis")
public class RedisRateLimitCounter implements RateLimitCounter {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimitCounter.class);

    private final StringRedisTemplate redisTemplate;

    public RedisRateLimitCounter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public OptionalInt incrementAndGet(String key, int windowSeconds) {
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count == null) {
                log.warn("Redis INCR이 null을 반환했다 — fail-open으로 처리한다. key={}", key);
                return OptionalInt.empty();
            }
            if (count == 1L) {
                redisTemplate.expire(key, Duration.ofSeconds(windowSeconds));
            }
            return OptionalInt.of(count.intValue());
        } catch (DataAccessException redisUnavailable) {
            log.warn("Redis 레이트리밋 백엔드 연결 실패 — 이번 요청은 한도 검사 없이 통과시킨다(fail-open).",
                    redisUnavailable);
            return OptionalInt.empty();
        }
    }
}
