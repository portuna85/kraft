package com.kraft.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.ActiveProfiles;

/**
 * I-16: eventTaskExecutor(core 2/max 4/queue 50)를 실제로 포화시켜, 지금까지 관측되지
 * 않던 CallerRunsPolicy 발동을 직접 검증한다. AsyncMetricsIntegrationTest는 게이지
 * (executor.active/queued)의 "존재"만 확인할 뿐, 포화가 실제로 몇 번 일어났는지·발행
 * 스레드가 정말 대신 실행하는지는 아무 테스트도 확인하지 않았다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@DisplayName("이벤트 실행기 포화(CallerRunsPolicy) 테스트")
class AsyncConfigSaturationTest {

    @Autowired
    @Qualifier("eventTaskExecutor")
    private ThreadPoolTaskExecutor eventTaskExecutor;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    @DisplayName("풀+큐 용량을 넘겨 제출하면 유실 없이 전부 실행되고, 초과분은 발행 스레드에서 직접 실행되며 카운터가 오른다")
    void overflowingCapacity_runsAllTasksAndCountsCallerRuns() throws InterruptedException {
        // eventTaskExecutor는 max 4 + queue 50 = 54 용량이다 — 여유를 두고 확실히 넘긴다.
        int totalTasks = 70;
        String submittingThreadName = Thread.currentThread().getName();

        CountDownLatch completedLatch = new CountDownLatch(totalTasks);
        Set<String> executingThreadNames = ConcurrentHashMap.newKeySet();

        for (int i = 0; i < totalTasks; i++) {
            eventTaskExecutor.execute(() -> {
                executingThreadNames.add(Thread.currentThread().getName());
                try {
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    completedLatch.countDown();
                }
            });
        }

        boolean allCompleted = completedLatch.await(30, TimeUnit.SECONDS);

        assertThat(allCompleted).as("모든 작업이 유실 없이 완료돼야 한다").isTrue();
        assertThat(executingThreadNames)
                .as("초과분은 발행 스레드(%s)에서 직접 실행돼야 한다", submittingThreadName)
                .contains(submittingThreadName);

        var counter = meterRegistry.find("kraft_async_caller_runs_total")
                .tag("executor", "eventTaskExecutor")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).as("CallerRunsPolicy 발동 횟수가 기록돼야 한다").isGreaterThan(0);
    }
}
