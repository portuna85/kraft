package com.kraft.recommend.web;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * {@code POST /api/v1/numbers/recommend} 전용 IP당 분당 요청 제한(02문서 6절). 단일 인스턴스
 * 메모리 카운터이며, 추천 도입을 이유로 Redis 등 공유 저장소를 선행 도입하지 않는다. 다중
 * 인스턴스에서 공유 제한이 필요해지면 그때 검토한다.
 * <p>
 * 한도는 {@code app.recommend.rate-limit.requests-per-minute}로 재배포 없이 조정할 수 있다
 * (운영 준비 — 실측 트래픽에 맞춰 값만 바꿀 수 있어야 한다). 다만 30/분은 여전히 실측 전
 * 초기값이다(02문서 6절) — {@link #reportAndCleanup()}이 남기는 허용/거부 집계가 그 실측
 * 근거가 된다. 클라이언트별 "첫 요청 시각"에 창을 고정하는 고정 윈도우라, 한 클라이언트가
 * 자기 창이 끝나기 직전·열리자마자 각각 한도만큼 보내면 사실상 최대 2배까지 통과할 수 있다
 * — 값과 무관하게 존재하는 특성이며, 이번 개정에서 알고리즘 자체는 바꾸지 않는다(운영
 * 런북 참고).
 */
@Slf4j
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
    private final LongAdder allowed = new LongAdder();
    private final LongAdder rejected = new LongAdder();

    /**
     * map 전체를 훑는 비상 청소({@link #evictExpired})가 요청량에 비례해 반복 실행되지 않도록
     * 최소 이 간격을 둔다(B13). {@code windows.size() > 10_000}인 동안에는 그 뒤에 오는 모든
     * 요청이 매번 O(n) 스캔을 유발할 수 있었다 — 쿨다운으로 한 번에 한 스레드만 실제로 훑게
     * 한다.
     */
    private static final long EMERGENCY_EVICT_COOLDOWN_MILLIS = 1_000L;
    private final AtomicLong lastEmergencyEvictAt = new AtomicLong(0);

    public boolean tryAcquire(String clientKey) {
        long now = Instant.now().toEpochMilli();

        // compute()의 재매핑 함수는 키별로 원자적이므로, "이 호출이 실제로 만든 값"을 그
        // 안에서 직접 담아 나온다(B13) — 바깥에서 공유 AtomicInteger를 따로 다시 읽으면, 그
        // 사이 다른 요청이 같은 키를 또 증가시켜 이 호출이 만든 값보다 큰 수를 보게 되고,
        // 한도 안에서 들어온 요청도 거절될 수 있었다.
        int[] countAfterThisCall = new int[1];
        windows.compute(clientKey, (key, existing) -> {
            if (existing == null || now - existing.windowStartMillis() >= WINDOW_MILLIS) {
                countAfterThisCall[0] = 1;
                return new Window(now, new AtomicInteger(1));
            }
            countAfterThisCall[0] = existing.count().incrementAndGet();
            return existing;
        });

        if (windows.size() > 10_000) {
            long last = lastEmergencyEvictAt.get();
            if (now - last >= EMERGENCY_EVICT_COOLDOWN_MILLIS && lastEmergencyEvictAt.compareAndSet(last, now)) {
                evictExpired(now);
            }
        }

        boolean withinLimit = countAfterThisCall[0] <= limitPerWindow;
        if (withinLimit) {
            allowed.increment();
        } else {
            rejected.increment();
        }
        return withinLimit;
    }

    /**
     * 실측 없이는 30/분이 맞는 값인지 영원히 알 수 없다 — 허용/거부 건수를 주기적으로 남겨
     * 나중에 조정 여부를 판단할 근거로 삼는다(원시 IP는 남기지 않는다). 같은 주기에 만료된
     * 항목을 무조건 청소해, {@code windows.size() > 10_000} 문턱 아래에서도 스쳐 지나간
     * 클라이언트의 항목이 무한히 쌓이지 않게 한다 — 그 문턱 기반 청소는 순간적인 급증 상황의
     * 안전판으로 별도로 남겨 둔다(대체가 아니라 보완).
     */
    @Scheduled(fixedDelayString = "${app.recommend.rate-limit.report-interval-ms:600000}")
    public void reportAndCleanup() {
        reportAndCleanup(Instant.now().toEpochMilli());
    }

    /** package-private: 테스트가 실제로 60초를 기다리지 않고도 만료 청소를 검증할 수 있게 한다. */
    void reportAndCleanup(long now) {
        long allowedCount = allowed.sumThenReset();
        long rejectedCount = rejected.sumThenReset();
        if (allowedCount > 0 || rejectedCount > 0) {
            log.info("최근 추천 요청 제한 집계: 허용 {}건, 거부 {}건.", allowedCount, rejectedCount);
        }
        evictExpired(now);
    }

    /** package-private: 테스트가 청소 후 남은 클라이언트 수를 확인할 수 있게 한다. */
    int trackedClientCount() {
        return windows.size();
    }

    private void evictExpired(long now) {
        windows.entrySet().removeIf(entry -> now - entry.getValue().windowStartMillis() >= WINDOW_MILLIS);
    }
}
