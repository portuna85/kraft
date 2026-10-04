package com.kraft.recommend.service;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 자동 수집({@link RecommendationAutoFetchScheduler})의 최근 결과를 메모리에 들고 있다가 헬스
 * 점검이 읽게 한다. 이력 나이(verifiedAt)만으로는 "이번 주 추첨이 아직 없어서 조용한 것"과
 * "매번 막히고 있는 것"을 구분할 수 없어, 연속 실패 횟수를 따로 센다. 재시작하면
 * {@link RecommendationFetchService}가 DB의 시도 기록으로 되돌린다.
 */
@Component
public class RecommendationFetchStatus {

    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private volatile Instant lastSuccessAt;
    private volatile Instant lastFailureAt;
    private volatile String lastFailureReason;

    /**
     * 재시작 직후 DB의 시도 기록(recommendation_fetch_attempts)으로 메모리 상태를 되돌린다. 메모리만
     * 쓰던 때는 재시작하면 연속 실패 횟수가 0이 되어 경보가 풀렸다.
     */
    public void restore(Instant lastSuccessAt, Instant lastFailureAt, String lastFailureReason, int consecutiveFailures) {
        this.lastSuccessAt = lastSuccessAt;
        this.lastFailureAt = lastFailureAt;
        this.lastFailureReason = lastFailureReason;
        this.consecutiveFailures.set(consecutiveFailures);
    }

    /** 응답을 신뢰해 처리했다(반영했거나, 아직 추첨 전이라는 정상 응답). */
    public void recordSuccess() {
        consecutiveFailures.set(0);
        lastSuccessAt = Instant.now();
    }

    /** 신뢰할 수 없는 응답이거나 검증에 실패해 반영하지 못했다. */
    public void recordFailure(String reason) {
        consecutiveFailures.incrementAndGet();
        lastFailureAt = Instant.now();
        lastFailureReason = reason;
    }

    public int consecutiveFailures() {
        return consecutiveFailures.get();
    }

    public Instant lastSuccessAt() {
        return lastSuccessAt;
    }

    public Instant lastFailureAt() {
        return lastFailureAt;
    }

    public String lastFailureReason() {
        return lastFailureReason;
    }
}
