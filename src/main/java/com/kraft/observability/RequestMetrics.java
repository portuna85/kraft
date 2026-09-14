package com.kraft.observability;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * 한 관측 주기 동안의 HTTP 요청 통계를 모은다.
 * <p>
 * 값을 읽어 갈 때 초기화하므로 각 보고 한 줄은 <b>그 주기만의 이야기</b>가 된다. 누적값을
 * 남기면 "지금 느려졌는지"를 눈으로 읽을 수 없다 — 오래 켜져 있을수록 평균이 둔해지기 때문이다.
 * <p>
 * 모든 요청 경로에서 불리므로 잠금을 쓰지 않는다. {@link LongAdder}는 경합이 있을 때
 * {@code AtomicLong}보다 훨씬 덜 부딪힌다.
 */
public class RequestMetrics {

    private final LongAdder count = new LongAdder();
    private final LongAdder errors = new LongAdder();
    private final LongAdder serverErrors = new LongAdder();
    private final LongAdder totalMillis = new LongAdder();
    private final AtomicLong maxMillis = new AtomicLong();

    public void record(int status, long millis) {
        count.increment();
        totalMillis.add(millis);
        maxMillis.accumulateAndGet(millis, Math::max);

        if (status >= 500) {
            serverErrors.increment();
            errors.increment();
        } else if (status >= 400) {
            errors.increment();
        }
    }

    /** 지금까지의 통계를 돌려주고 초기화한다. */
    public Snapshot drain() {
        long requests = count.sumThenReset();
        long failed = errors.sumThenReset();
        long server = serverErrors.sumThenReset();
        long sum = totalMillis.sumThenReset();
        long max = maxMillis.getAndSet(0);

        return new Snapshot(requests, failed, server, requests == 0 ? 0 : sum / requests, max);
    }

    /**
     * @param requests     이 주기에 처리한 요청 수
     * @param errors       4xx + 5xx
     * @param serverErrors 5xx만 — 사용자 잘못이 아닌 것
     * @param avgMillis    평균 응답 시간
     * @param maxMillis    가장 오래 걸린 요청
     */
    public record Snapshot(long requests, long errors, long serverErrors, long avgMillis, long maxMillis) {

        public double errorRate() {
            return requests == 0 ? 0 : (double) errors / requests;
        }
    }
}
