package com.kraft.common.concurrency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.TransientDataAccessResourceException;

@DisplayName("TransientWriteRetrier 단위 테스트")
class TransientWriteRetrierTest {

    private SimpleMeterRegistry meterRegistry;
    private TransientWriteRetrier retrier;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        retrier = new TransientWriteRetrier(meterRegistry);
    }

    @Test
    @DisplayName("첫 시도가 성공하면 재시도 카운터가 오르지 않는다")
    void succeedsOnFirstAttempt_doesNotIncrementRetryCounter() {
        AtomicInteger calls = new AtomicInteger();

        retrier.retry("op", 3, calls::incrementAndGet);

        assertThat(calls.get()).isEqualTo(1);
        assertThat(retryCount("op")).isZero();
        assertThat(exhaustedCount("op")).isZero();
    }

    @Test
    @DisplayName("경합 예외가 나면 재시도하고, 이후 성공하면 예외를 삼킨다")
    void retriesOnDataAccessException_thenSucceeds() {
        AtomicInteger calls = new AtomicInteger();

        retrier.retry("op", 3, () -> {
            if (calls.incrementAndGet() < 2) {
                throw new TransientDataAccessResourceException("경합");
            }
        });

        assertThat(calls.get()).isEqualTo(2);
        assertThat(retryCount("op")).isEqualTo(1);
        assertThat(exhaustedCount("op")).isZero();
    }

    @Test
    @DisplayName("최대 시도 횟수를 모두 소진하면 마지막 예외를 그대로 던지고 소진 카운터가 오른다")
    void exhaustsAllAttempts_throwsAndIncrementsExhaustedCounter() {
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> retrier.retry("op", 3, () -> {
            calls.incrementAndGet();
            throw new TransientDataAccessResourceException("계속 경합");
        })).isInstanceOf(TransientDataAccessResourceException.class);

        assertThat(calls.get()).isEqualTo(3);
        assertThat(retryCount("op")).isEqualTo(3);
        assertThat(exhaustedCount("op")).isEqualTo(1);
    }

    @Test
    @DisplayName("경합이 아닌 예외(RuntimeException)는 재시도 없이 즉시 전파한다")
    void nonDataAccessException_propagatesWithoutRetry() {
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> retrier.retry("op", 3, () -> {
            calls.incrementAndGet();
            throw new IllegalStateException("경합이 아님");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(calls.get()).isEqualTo(1);
    }

    private double retryCount(String operation) {
        var counter = meterRegistry.find("kraft_transient_write_retry_total").tag("operation", operation).counter();
        return counter == null ? 0 : counter.count();
    }

    private double exhaustedCount(String operation) {
        var counter =
                meterRegistry.find("kraft_transient_write_retry_exhausted_total").tag("operation", operation).counter();
        return counter == null ? 0 : counter.count();
    }
}
