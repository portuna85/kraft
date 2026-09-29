package com.kraft.recommend.service;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 자동 수집({@link RecommendationAutoFetchScheduler})의 최근 결과를 메모리에 들고 있다가 헬스
 * 점검이 읽게 한다. 이력 나이(verifiedAt)만으로는 "이번 주 추첨이 아직 없어서 조용한 것"과
 * "매번 막히고 있는 것"을 구분할 수 없어, 연속 실패 횟수를 따로 센다. 재시작하면 0으로
 * 돌아가며, 그 경우에도 이력 나이 경보가 뒤를 받친다.
 */
@Component
public class RecommendationFetchStatus {

    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private volatile Instant lastSuccessAt;
    private volatile Instant lastFailureAt;
    private volatile String lastFailureReason;

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
