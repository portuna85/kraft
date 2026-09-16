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
}
