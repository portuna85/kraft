package com.kraft.common.concurrency;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.EnumMap;
import java.util.Map;
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

    /**
     * BE-SEC-01(docs/improvement.md) 계열 정리: 메트릭 태그를 호출부가 아무 문자열이나
     * 넘길 수 있는 {@code String}이 아니라 이 클래스가 아는 고정 집합으로 좁힌다. 그래야
     * 카운터를 요청마다 만들지 않고 생성자에서 한 번만 등록할 수 있다(TD-022). 새 재시도
     * 대상이 생기면 이 enum에 상수를 추가한다 — 호출부에서 임의 문자열을 넘기는 경로를
     * 만들지 않는다.
     */
    public enum Operation {
        COMMUNITY_BLOCK("community_block"),
        COMMUNITY_UNLIKE("community_unlike");

        private final String tagValue;

        Operation(String tagValue) {
            this.tagValue = tagValue;
        }
    }

    private final Map<Operation, Counter> retryCounters;
    private final Map<Operation, Counter> exhaustedCounters;

    public TransientWriteRetrier(MeterRegistry meterRegistry) {
        this.retryCounters = new EnumMap<>(Operation.class);
        this.exhaustedCounters = new EnumMap<>(Operation.class);
        for (Operation operation : Operation.values()) {
            retryCounters.put(operation, Counter.builder("kraft_transient_write_retry_total")
                    .description("REQUIRES_NEW 쓰기가 경합(DataAccessException)으로 재시도된 횟수")
                    .tag("operation", operation.tagValue)
                    .register(meterRegistry));
            exhaustedCounters.put(operation, Counter.builder("kraft_transient_write_retry_exhausted_total")
                    .description("재시도를 모두 소진하고 최종적으로 실패한 횟수")
                    .tag("operation", operation.tagValue)
                    .register(meterRegistry));
        }
    }

    /**
     * @param operation   메트릭 태그·로그에 쓰는 재시도 대상 식별자
     * @param maxAttempts 최대 시도 횟수(최초 시도 포함)
     * @param write       REQUIRES_NEW 등으로 스스로 원자적인 쓰기 동작
     */
    public void retry(Operation operation, int maxAttempts, Runnable write) {
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                write.run();
                return;
            } catch (DataAccessException raceLost) {
                retryCounters.get(operation).increment();
                if (attempt == maxAttempts) {
                    exhaustedCounters.get(operation).increment();
                    throw raceLost;
                }
                log.debug("{} 경합으로 재시도: attempt={}", operation, attempt);
                sleepBriefly(attempt);
            }
        }
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
