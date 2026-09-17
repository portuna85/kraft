package com.kraft.observability;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code record()}·{@code drain()}이 동시에 돌아도 필드 다섯 개가 서로 다른 주기로 쪼개지지
 * 않는지 검증한다(개선 보고서 "지표 스냅숏의 비원자성과 평균 중심 관측"). 예전 구현(독립된
 * {@code LongAdder} 다섯 개를 하나씩 sumThenReset)은 이 시나리오에서 {@code errors}가
 * {@code requests}보다 큰 스냅숏을 만들어낼 수 있었다.
 */
class RequestMetricsConcurrencyTest {

    @Test
    @DisplayName("record와 drain이 동시에 돌아도 어떤 스냅숏도 errors가 requests보다 클 수 없다")
    void concurrentRecordAndDrain_neverProducesInconsistentSnapshot() throws InterruptedException {
        RequestMetrics metrics = new RequestMetrics();
        int writerThreads = 8;
        int recordsPerThread = 5_000;

        ExecutorService writers = Executors.newFixedThreadPool(writerThreads);
        List<RequestMetrics.Snapshot> drained = new ArrayList<>();
        CountDownLatch finished = new CountDownLatch(writerThreads);

        for (int i = 0; i < writerThreads; i++) {
            writers.submit(() -> {
                Random random = new Random();
                try {
                    for (int j = 0; j < recordsPerThread; j++) {
                        int status = random.nextInt(10) == 0 ? 500 : (random.nextInt(10) == 0 ? 404 : 200);
                        metrics.record(status, 1);
                    }
                } finally {
                    finished.countDown();
                }
            });
        }

        // 쓰기가 진행되는 동안 반복해서 드레인해 record()·drain()이 겹치게 만든다.
        while (finished.getCount() > 0) {
            drained.add(metrics.drain());
            Thread.sleep(1);
        }
        writers.shutdown();
        assertThat(writers.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        // 마지막에 남은 것까지 마저 비운다.
        drained.add(metrics.drain());

        assertThat(drained).isNotEmpty();
        for (RequestMetrics.Snapshot snapshot : drained) {
            assertThat(snapshot.errors())
                    .as("errors는 requests를 넘을 수 없다: %s", snapshot)
                    .isLessThanOrEqualTo(snapshot.requests());
            assertThat(snapshot.serverErrors())
                    .as("serverErrors는 errors를 넘을 수 없다: %s", snapshot)
                    .isLessThanOrEqualTo(snapshot.errors());
        }
    }

    @Test
    @DisplayName("동시 드레인 없이 모두 기록한 뒤 한 번에 비우면 개수가 정확히 들어맞는다")
    void recordThenDrainOnce_countsExactly() throws InterruptedException {
        RequestMetrics metrics = new RequestMetrics();
        int writerThreads = 8;
        int recordsPerThread = 2_000;
        ExecutorService writers = Executors.newFixedThreadPool(writerThreads);

        for (int i = 0; i < writerThreads; i++) {
            writers.submit(() -> {
                for (int j = 0; j < recordsPerThread; j++) {
                    metrics.record(200, 1);
                }
            });
        }
        writers.shutdown();
        assertThat(writers.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        RequestMetrics.Snapshot snapshot = metrics.drain();

        assertThat(snapshot.requests()).isEqualTo((long) writerThreads * recordsPerThread);
        assertThat(snapshot.errors()).isZero();
    }

    /**
     * O04: HealthReporter.collect()가 주기마다 drain()으로 비우므로, "동시 기록이 도는 동안
     * 여러 번 드레인한 스냅숏을 모두 더한 값"이 "그동안 실제로 기록을 시도한 건수"와 거의
     * 같아야 관측치를 믿을 수 있다 — 위쪽 테스트들은 개별 스냅숏의 내부 일관성
     * (errors&lt;=requests)만 보고, 드레인 여러 번에 걸쳐 건수 자체가 새거나 겹치지 않는지는
     * 보지 않았다.
     * <p>
     * 정확히 같지는 않다 — {@link RequestMetrics} 클래스 주석의 "남아 있는 허용 오차"에 적은
     * 대로, record()가 counters.get()으로 묶음을 읽은 직후 drain()이 그 묶음을 떼어 가면 그
     * 한 건은 조용히 사라진다. 여기서 직접 이 손실 폭을 측정해 두 가지를 함께 고정한다:
     * 합계가 실제 기록 수를 <b>넘는 일은 없어야 하고</b>(그러면 이중 집계다 — 있어서는 안
     * 된다), 손실은 표본 대비 아주 작은 비율 안에 머물러야 한다(그러지 않으면 더는 "드문"
     * 손실이 아니라 관측치를 믿을 수 없다).
     */
    @Test
    @DisplayName("record가 도는 동안 여러 번 드레인해도 합계는 실제로 기록한 건수와 거의 같고 절대 넘지 않는다")
    void sumOfMultipleDrains_isCloseToTotalRecordedCount() throws InterruptedException {
        RequestMetrics metrics = new RequestMetrics();
        int writerThreads = 8;
        int recordsPerThread = 5_000;
        long expectedTotal = (long) writerThreads * recordsPerThread;

        ExecutorService writers = Executors.newFixedThreadPool(writerThreads);
        List<RequestMetrics.Snapshot> drained = new ArrayList<>();
        CountDownLatch finished = new CountDownLatch(writerThreads);

        for (int i = 0; i < writerThreads; i++) {
            writers.submit(() -> {
                try {
                    for (int j = 0; j < recordsPerThread; j++) {
                        metrics.record(200, 1);
                    }
                } finally {
                    finished.countDown();
                }
            });
        }

        while (finished.getCount() > 0) {
            drained.add(metrics.drain());
            Thread.sleep(1);
        }
        writers.shutdown();
        assertThat(writers.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        drained.add(metrics.drain());

        long summedRequests = drained.stream().mapToLong(RequestMetrics.Snapshot::requests).sum();
        assertThat(summedRequests)
                .as("이중 집계는 설계상 있을 수 없다 — 늘 정확히 하나의 묶음에만 더해진다")
                .isLessThanOrEqualTo(expectedTotal);
        // 넉넉히 0.1%까지 허용한다. 실제로 관측된 손실은 이보다 훨씬 작다(수만 건 중 한 자릿수).
        long maxTolerableLoss = expectedTotal / 1000;
        assertThat(expectedTotal - summedRequests)
                .as("손실이 %d건을 넘으면 더는 '드문' 손실이 아니다(기록 %d건 중 %d건 손실)",
                        maxTolerableLoss, expectedTotal, expectedTotal - summedRequests)
                .isLessThanOrEqualTo(maxTolerableLoss);
    }
}
