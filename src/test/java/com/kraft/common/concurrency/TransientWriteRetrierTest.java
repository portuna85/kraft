package com.kraft.common.concurrency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kraft.common.concurrency.TransientWriteRetrier.Operation;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.TransientDataAccessResourceException;

@DisplayName("TransientWriteRetrier 단위 테스트")
class TransientWriteRetrierTest {

    private static final Operation OP = Operation.COMMUNITY_BLOCK;

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

        retrier.retry(OP, 3, calls::incrementAndGet);

        assertThat(calls.get()).isEqualTo(1);
        assertThat(retryCount(OP)).isZero();
        assertThat(exhaustedCount(OP)).isZero();
    }

    @Test
    @DisplayName("경합 예외가 나면 재시도하고, 이후 성공하면 예외를 삼킨다")
    void retriesOnDataAccessException_thenSucceeds() {
        AtomicInteger calls = new AtomicInteger();

        retrier.retry(OP, 3, () -> {
            if (calls.incrementAndGet() < 2) {
                throw new TransientDataAccessResourceException("경합");
            }
        });

        assertThat(calls.get()).isEqualTo(2);
        assertThat(retryCount(OP)).isEqualTo(1);
        assertThat(exhaustedCount(OP)).isZero();
    }

    @Test
    @DisplayName("최대 시도 횟수를 모두 소진하면 마지막 예외를 그대로 던지고 소진 카운터가 오른다")
    void exhaustsAllAttempts_throwsAndIncrementsExhaustedCounter() {
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> retrier.retry(OP, 3, () -> {
            calls.incrementAndGet();
            throw new TransientDataAccessResourceException("계속 경합");
        })).isInstanceOf(TransientDataAccessResourceException.class);

        assertThat(calls.get()).isEqualTo(3);
        assertThat(retryCount(OP)).isEqualTo(3);
        assertThat(exhaustedCount(OP)).isEqualTo(1);
    }

    @Test
    @DisplayName("경합이 아닌 예외(RuntimeException)는 재시도 없이 즉시 전파한다")
    void nonDataAccessException_propagatesWithoutRetry() {
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> retrier.retry(OP, 3, () -> {
            calls.incrementAndGet();
            throw new IllegalStateException("경합이 아님");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("서로 다른 Operation은 독립된 카운터를 쓴다")
    void differentOperations_useIndependentCounters() {
        AtomicInteger unlikeCalls = new AtomicInteger();
        retrier.retry(Operation.COMMUNITY_BLOCK, 3, () -> { });
        retrier.retry(Operation.COMMUNITY_UNLIKE, 3, () -> {
            if (unlikeCalls.incrementAndGet() < 2) {
                throw new TransientDataAccessResourceException("경합");
            }
        });

        assertThat(retryCount(Operation.COMMUNITY_BLOCK)).isZero();
        assertThat(retryCount(Operation.COMMUNITY_UNLIKE)).isEqualTo(1);
    }

    private double retryCount(Operation operation) {
        var counter = meterRegistry.find("kraft_transient_write_retry_total")
                .tag("operation", tagValueOf(operation)).counter();
        return counter == null ? 0 : counter.count();
    }

    private double exhaustedCount(Operation operation) {
        var counter = meterRegistry.find("kraft_transient_write_retry_exhausted_total")
                .tag("operation", tagValueOf(operation)).counter();
        return counter == null ? 0 : counter.count();
    }

    private static String tagValueOf(Operation operation) {
        return operation.name().toLowerCase(java.util.Locale.ROOT);
    }
}
