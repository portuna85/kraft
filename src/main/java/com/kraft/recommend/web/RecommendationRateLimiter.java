package com.kraft.recommend.web;

import com.kraft.shared.web.FixedWindowRateLimiter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * {@code POST /api/v1/numbers/recommend} 전용 IP당 분당 요청 제한. 실제 카운팅은 {@link FixedWindowRateLimiter}에 위임한다.
 * 단일 인스턴스 메모리 카운터이며 공유 저장소는 쓰지 않는다. 한도는 재배포 없이
 * {@code app.recommend.rate-limit.requests-per-minute}로 조정하고(30/분은 실측 전 초기값), {@link #reportAndCleanup()}의
 * 허용/거부 집계가 근거가 된다.
 */
@Component
public class RecommendationRateLimiter {

    private static final long WINDOW_MILLIS = 60_000L;

    private final FixedWindowRateLimiter delegate;

    public RecommendationRateLimiter(
            @Value("${app.recommend.rate-limit.requests-per-minute:30}") int limitPerWindow) {
        this.delegate = new FixedWindowRateLimiter("추천", limitPerWindow, WINDOW_MILLIS);
    }

    public boolean tryAcquire(String clientKey) {
        return delegate.tryAcquire(clientKey);
    }

    @Scheduled(fixedDelayString = "${app.recommend.rate-limit.report-interval-ms:600000}")
    public void reportAndCleanup() {
        reportAndCleanup(Instant.now().toEpochMilli());
    }

    /** package-private: 테스트가 실제로 60초를 기다리지 않고도 만료 청소를 검증할 수 있게 한다. */
    void reportAndCleanup(long now) {
        delegate.reportAndCleanup(now);
    }

    /** package-private: 테스트가 청소 후 남은 클라이언트 수를 확인할 수 있게 한다. */
    int trackedClientCount() {
        return delegate.trackedClientCount();
    }
}
