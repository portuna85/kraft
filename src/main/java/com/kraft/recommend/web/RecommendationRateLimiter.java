package com.kraft.recommend.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@code POST /api/v1/numbers/recommend} 전용 IP당 분당 요청 제한(02문서 6절). 단일 인스턴스
 * 메모리 카운터이며, 추천 도입을 이유로 Redis 등 공유 저장소를 선행 도입하지 않는다. 다중
 * 인스턴스에서 공유 제한이 필요해지면 그때 검토한다.
 * <p>
 * 한도는 {@code app.recommend.rate-limit.requests-per-minute}로 재배포 없이 조정할 수 있다
 * (운영 준비 — 실측 트래픽에 맞춰 값만 바꿀 수 있어야 한다).
 */
@Component
public class RecommendationRateLimiter {

    private static final long WINDOW_MILLIS = 60_000L;

    private final int limitPerWindow;

    public RecommendationRateLimiter(
            @Value("${app.recommend.rate-limit.requests-per-minute:30}") int limitPerWindow) {
        this.limitPerWindow = limitPerWindow;
    }

    private record Window(long windowStartMillis, AtomicInteger count) {
    }

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public boolean tryAcquire(String clientKey) {
        long now = Instant.now().toEpochMilli();
        Window window = windows.compute(clientKey, (key, existing) -> {
            if (existing == null || now - existing.windowStartMillis() >= WINDOW_MILLIS) {
                return new Window(now, new AtomicInteger(1));
            }
            existing.count().incrementAndGet();
            return existing;
        });

        if (windows.size() > 10_000) {
            evictExpired(now);
        }
        return window.count().get() <= limitPerWindow;
    }

    private void evictExpired(long now) {
        windows.entrySet().removeIf(entry -> now - entry.getValue().windowStartMillis() >= WINDOW_MILLIS);
    }
}
