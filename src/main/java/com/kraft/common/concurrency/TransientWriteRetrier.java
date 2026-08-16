package com.kraft.common.concurrency;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

/**
 * I-15: {@code REQUIRES_NEW}로 격리된 원자적 쓰기가 경합(드물게 발생하는
 * {@link DataAccessException}, 예: MariaDB REPEATABLE READ 아래 "Record has changed
 * since last read")을 만났을 때 짧게 재시도한다. {@code CommunityBlockService.block}과
 * {@code CommunityReactionService.unlike}에 거의 동일하게 중복돼 있던 재시도·지연 로직을
 * 한 곳으로 모았다.
 *
 * <p><b>이 클래스는 트랜잭션을 시작하지 않는다.</b> 호출부가 {@code @Transactional}
 * 없이(또는 {@code Propagation.NOT_SUPPORTED}로) 이 메서드를 불러야 한다 — 그렇지 않으면
 * {@link Thread#sleep}이 도는 동안 바깥 트랜잭션이 커넥션을 계속 붙잡는다. 재시도 대상인
 * {@code write}는 자신만의 {@code REQUIRES_NEW} 트랜잭션에서 원자적으로 실행되므로, 이
 * 메서드가 트랜잭션 밖에서 호출돼도 각 시도의 원자성은 그대로 보장된다.
 */
@Component
public class TransientWriteRetrier {

    private static final Logger log = LoggerFactory.getLogger(TransientWriteRetrier.class);

    private final MeterRegistry meterRegistry;

    public TransientWriteRetrier(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * @param operationName 메트릭 태그·로그에 쓰는 식별자(예: "community_block")
     * @param maxAttempts   최대 시도 횟수(최초 시도 포함)
     * @param write         REQUIRES_NEW 등으로 스스로 원자적인 쓰기 동작
     */
    public void retry(String operationName, int maxAttempts, Runnable write) {
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                write.run();
                return;
            } catch (DataAccessException raceLost) {
                retryCounter(operationName).increment();
                if (attempt == maxAttempts) {
                    exhaustedCounter(operationName).increment();
                    throw raceLost;
                }
                log.debug("{} 경합으로 재시도: attempt={}", operationName, attempt);
                sleepBriefly(attempt);
            }
        }
    }

    private Counter retryCounter(String operationName) {
        return Counter.builder("kraft_transient_write_retry_total")
                .description("REQUIRES_NEW 쓰기가 경합(DataAccessException)으로 재시도된 횟수")
                .tag("operation", operationName)
                .register(meterRegistry);
    }

    private Counter exhaustedCounter(String operationName) {
        return Counter.builder("kraft_transient_write_retry_exhausted_total")
                .description("재시도를 모두 소진하고 최종적으로 실패한 횟수")
                .tag("operation", operationName)
                .register(meterRegistry);
    }

    // 동시 재시도가 곧바로 다시 충돌하지 않도록 시도 횟수에 비례한 짧은 무작위 지연을 둔다.
    private static void sleepBriefly(int attempt) {
        try {
            Thread.sleep((long) (Math.random() * 10 * attempt));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
