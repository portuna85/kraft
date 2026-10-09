package com.kraft.observability;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;

/**
 * 한 관측 주기 동안의 HTTP 요청 통계. 읽어 갈 때 초기화해 각 보고 한 줄이 그 주기만의 이야기가 되게 한다(누적값은
 * 오래 켜질수록 평균이 둔해진다). 모든 요청 경로에서 불리므로 잠금 없이 {@link LongAdder}를 쓴다.
 * <p>
 * 필드들을 하나의 불변 묶음({@link Counters})으로 만들어 {@link AtomicReference}로 통째로 교체하므로
 * {@code drain()}이 전체를 원자적으로 떼어 간다(필드를 따로 리셋하면 한 요청의 기여가 두 구간에 쪼개진다).
 * <p>
 * 허용 오차: {@code record()}가 묶음을 읽은 직후 {@code drain()}이 그 묶음을 떼어 가면 그 한 건은 옛 묶음에 더해져
 * 그 주기 집계에서 사라진다. 잠금 없는 대가로, 표본 수만 건 중 한 자릿수 수준이라({@code RequestMetricsConcurrencyTest})
 * 추세·임계 판정에는 지장이 없다. 한 기록이 두 번 잡히는 일은 없다.
 */
public class RequestMetrics {

    /** 평균·최댓값에 묻히는 소수의 느린 요청을, 이 절대 ms 이상인 건수로 따로 센다(퍼센타일 계산보다 가볍다). */
    private static final long DEFAULT_SLOW_THRESHOLD_MILLIS = 3000;

    private final long slowThresholdMillis;

    public RequestMetrics() {
        this(DEFAULT_SLOW_THRESHOLD_MILLIS);
    }

    public RequestMetrics(long slowThresholdMillis) {
        this.slowThresholdMillis = slowThresholdMillis;
    }

    private record Counters(LongAdder count, LongAdder errors, LongAdder serverErrors,
                             LongAdder totalMillis, AtomicLong maxMillis, LongAdder slowRequests) {

        static Counters fresh() {
            return new Counters(new LongAdder(), new LongAdder(), new LongAdder(), new LongAdder(),
                    new AtomicLong(), new LongAdder());
        }
    }

    private final AtomicReference<Counters> counters = new AtomicReference<>(Counters.fresh());

    public void record(int status, long millis) {
        Counters c = counters.get();
        c.count().increment();
        c.totalMillis().add(millis);
        c.maxMillis().accumulateAndGet(millis, Math::max);

        if (millis >= slowThresholdMillis) {
            c.slowRequests().increment();
        }

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
        long slow = c.slowRequests().sum();

        return new Snapshot(requests, failed, server, requests == 0 ? 0 : sum / requests, max, slow);
    }

    /**
     * @param errors       4xx + 5xx
     * @param serverErrors 5xx만 — 사용자 잘못이 아닌 것
     * @param slowRequests {@link #slowThresholdMillis} 이상 걸린 요청 수
     */
    public record Snapshot(long requests, long errors, long serverErrors, long avgMillis, long maxMillis,
                            long slowRequests) {

        public double errorRate() {
            return requests == 0 ? 0 : (double) errors / requests;
        }
    }
}
