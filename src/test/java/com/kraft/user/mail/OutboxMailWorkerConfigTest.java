package com.kraft.user.mail;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * O05: {@code app.mail.max-concurrent-sends=0}은 {@link java.util.concurrent.Semaphore}가
 * 영원히 획득되지 않는 조용한 정지로 이어지고, 음수 {@code batch-size}는 {@code claimBatch}의
 * SQL {@code LIMIT}에서 예외를 낸다. 둘 다 기동 시점에 막는지 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class OutboxMailWorkerConfigTest {

    @Mock
    private OutboxMailStore store;

    @Mock
    private EmailSender emailSender;

    @Test
    @DisplayName("동시 발송 수가 0 이하이면 기동을 거절한다")
    void zeroMaxConcurrentSendsFailsFast() {
        OutboxMailWorker worker = new OutboxMailWorker(store, emailSender);
        ReflectionTestUtils.setField(worker, "maxConcurrentSends", 0);
        ReflectionTestUtils.setField(worker, "batchSize", 20);

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(worker, "initSendPermits"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("max-concurrent-sends");
    }

    @Test
    @DisplayName("배치 크기가 음수이면 기동을 거절한다")
    void negativeBatchSizeFailsFast() {
        OutboxMailWorker worker = new OutboxMailWorker(store, emailSender);
        ReflectionTestUtils.setField(worker, "maxConcurrentSends", 5);
        ReflectionTestUtils.setField(worker, "batchSize", -1);

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(worker, "initSendPermits"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("batch-size");
    }

    @Test
    @DisplayName("정상 값이면 기동이 통과한다")
    void validValuesPassThrough() {
        OutboxMailWorker worker = new OutboxMailWorker(store, emailSender);
        ReflectionTestUtils.setField(worker, "maxConcurrentSends", 5);
        ReflectionTestUtils.setField(worker, "batchSize", 20);

        assertThatCode(() -> ReflectionTestUtils.invokeMethod(worker, "initSendPermits"))
                .doesNotThrowAnyException();
    }
}
