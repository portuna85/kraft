package com.kraft.observability;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;

/**
 * 한 관측 주기 동안의 HTTP 요청 통계를 모은다.
 * <p>
 * 값을 읽어 갈 때 초기화하므로 각 보고 한 줄은 <b>그 주기만의 이야기</b>가 된다. 누적값을
 * 남기면 "지금 느려졌는지"를 눈으로 읽을 수 없다 — 오래 켜져 있을수록 평균이 둔해지기 때문이다.
 * <p>
 * 모든 요청 경로에서 불리므로 잠금을 쓰지 않는다. {@link LongAdder}는 경합이 있을 때
 * {@code AtomicLong}보다 훨씬 덜 부딪힌다.
 * <p>
 * {@code count}·{@code errors}·{@code serverErrors}·{@code totalMillis}·{@code maxMillis}를
 * 예전에는 필드 다섯 개로 따로 두고 {@code drain()}에서 하나씩 {@code sumThenReset()}했다.
 * 그 사이에 {@code record()}가 끼어들면 한 요청의 기여가 두 드레인 구간에 걸쳐 쪼개질 수
 * 있었다(개선 보고서 "지표 스냅숏의 비원자성과 평균 중심 관측") — 예를 들어 count는 이번
 * 구간에 반영됐는데 errors는 다음 구간으로 넘어가는 식이다. 다섯 필드를 하나의 불변 묶음
 * ({@link Counters})으로 만들고 {@link AtomicReference}로 통째로 교체하면, {@code drain()}이
 * "이 시점까지의 전체 묶음"을 원자적으로 떼어 갈 수 있다. {@code record()}는 그 순간 잡은
 * 묶음의 LongAdder에 그대로 더하므로 여전히 잠금이 없다.
 */
public class RequestMetrics {

    private record Counters(LongAdder count, LongAdder errors, LongAdder serverErrors,
                             LongAdder totalMillis, AtomicLong maxMillis) {

        static Counters fresh() {
            return new Counters(new LongAdder(), new LongAdder(), new LongAdder(), new LongAdder(), new AtomicLong());
        }
    }

    private final AtomicReference<Counters> counters = new AtomicReference<>(Counters.fresh());

    public void record(int status, long millis) {
        Counters c = counters.get();
        c.count().increment();
        c.totalMillis().add(millis);
        c.maxMillis().accumulateAndGet(millis, Math::max);

        if (status >= 500) {
            c.serverErrors().increment();
            c.errors().increment();
        } else if (status >= 400) {
            c.errors().increment();
        }
    }

    /** 지금까지의 통계를 돌려주고 초기화한다. */
    public Snapshot drain() {
        Counters c = counters.getAndSet(Counters.fresh());

        long requests = c.count().sum();
        long failed = c.errors().sum();
        long server = c.serverErrors().sum();
        long sum = c.totalMillis().sum();
        long max = c.maxMillis().get();

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
