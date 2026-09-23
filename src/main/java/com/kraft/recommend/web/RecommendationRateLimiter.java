package com.kraft.recommend.web;

import com.kraft.shared.web.FixedWindowRateLimiter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * {@code POST /api/v1/numbers/recommend} 전용 IP당 분당 요청 제한(02문서 6절). 실제 카운팅
 * 로직은 {@link FixedWindowRateLimiter}로 옮겨 로그인·가입 등 다른 경로에서도 재사용한다
 * (개선 보고서 SEC-01, {@code com.kraft.config.security.AuthRateLimitFilter}). 단일 인스턴스
 * 메모리 카운터이며, 추천 도입을 이유로 Redis 등 공유 저장소를 선행 도입하지 않는다. 다중
 * 인스턴스에서 공유 제한이 필요해지면 그때 검토한다.
 * <p>
 * 한도는 {@code app.recommend.rate-limit.requests-per-minute}로 재배포 없이 조정할 수 있다
 * (운영 준비 — 실측 트래픽에 맞춰 값만 바꿀 수 있어야 한다). 다만 30/분은 여전히 실측 전
 * 초기값이다(02문서 6절) — {@link #reportAndCleanup()}이 남기는 허용/거부 집계가 그 실측
 * 근거가 된다.
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
