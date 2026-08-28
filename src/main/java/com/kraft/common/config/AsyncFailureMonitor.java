package com.kraft.common.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.stereotype.Component;

/**
 * {@code @Async} void 메서드에서 호출자에게 전달할 수 없는 예외를 로그와 메트릭으로 남긴다.
 * 메트릭의 listener 값은 코드로 고정된 집합에만 매핑해 라벨 카디널리티가 늘어나지 않게 한다.
 */
@Component
public class AsyncFailureMonitor implements AsyncUncaughtExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(AsyncFailureMonitor.class);
    private static final String UNKNOWN_LISTENER = "unknown";
    private static final Map<String, String> LISTENER_TAGS = Map.of(
            "LottoRecommendationService", "recommendation-history",
            "RevalidateWebhookListener", "revalidate-webhook",
            "RoundEtagProvider", "round-etag",
            "WinningNumberBackfillService", "winning-number-backfill"
    );

    private final Map<String, Counter> failureCounters;

    public AsyncFailureMonitor(MeterRegistry meterRegistry) {
        Map<String, Counter> counters = new HashMap<>();
        Set<String> listenerTags = new HashSet<>(LISTENER_TAGS.values());
        listenerTags.add(UNKNOWN_LISTENER);
        for (String listenerTag : listenerTags) {
            counters.put(listenerTag, Counter.builder("kraft_async_listener_failures_total")
                    .description("Uncaught asynchronous listener failures")
                    .tag("listener", listenerTag)
                    .tag("outcome", "failure")
                    .register(meterRegistry));
        }
        this.failureCounters = Map.copyOf(counters);
    }

    @Override
    public void handleUncaughtException(Throwable throwable, Method method, Object... parameters) {
        String listenerTag = LISTENER_TAGS.getOrDefault(method.getDeclaringClass().getSimpleName(), UNKNOWN_LISTENER);
        failureCounters.get(listenerTag).increment();
        log.error("Async listener failed: listener={} method={}", listenerTag, method.getName(), throwable);
    }
}
