package com.kraft.common.web;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.kraft.common.config.SecurityProperties;
import java.time.Duration;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 기본 레이트리밋 백엔드 — Caffeine 인메모리 카운터, 단일 인스턴스 전용.
 * expireAfterWrite로 고정 윈도를 흉내내므로(텀블링), 윈도 경계에서 한도의 최대
 * ~2배 버스트가 가능하다(PublicRateLimitFilter의 기존 주석과 동일한 특성).
 * 모든 key가 동일한 윈도 길이를 쓰는 전제라 windowSeconds는 최초 캐시 생성 시
 * 한 번만 반영된다 — 호출마다 다른 windowSeconds를 넘겨도 무시된다.
 */
@Component
@ConditionalOnProperty(name = "kraft.security.rate-limit-backend", havingValue = "in-memory", matchIfMissing = true)
public class InMemoryRateLimitCounter implements RateLimitCounter {

    private final Cache<String, AtomicInteger> counters;

    public InMemoryRateLimitCounter(SecurityProperties securityProperties) {
        this.counters = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(1))
                .maximumSize(securityProperties.rateLimitMaxKeys())
                .build();
    }

    @Override
    public OptionalInt incrementAndGet(String key, int windowSeconds) {
        return OptionalInt.of(counters.get(key, k -> new AtomicInteger(0)).incrementAndGet());
    }
}
