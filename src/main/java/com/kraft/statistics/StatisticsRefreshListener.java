package com.kraft.statistics;

import com.kraft.winningnumber.WinningNumbersCollectedEvent;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

@Component
public class StatisticsRefreshListener {

    private static final Logger log = LoggerFactory.getLogger(StatisticsRefreshListener.class);

    private final StatisticsSummaryRebuilder summaryRebuilder;

    public StatisticsRefreshListener(StatisticsSummaryRebuilder summaryRebuilder) {
        this.summaryRebuilder = summaryRebuilder;
    }

    /**
     * KB-16: catch-log만 하고 끝내던 이전과 달리, 일시적 실패(예: 커넥션 풀 경합)에는
     * 유한 재시도(resilience4j "statisticsRebuild", 최대 3회)를 건다. 재시도가 모두
     * 소진되면 예외를 그대로 전파한다 — @Async 메서드라 Spring의 기본
     * AsyncUncaughtExceptionHandler가 스택트레이스와 함께 ERROR로 남기므로(이전의
     * warn+메시지만 남기던 catch-log보다 가시성이 높다) 사용자 요청 경로는 막지 않으면서도
     * 조용히 묻히지 않는다. 다음 회차 수집 이벤트나 주간
     * reconciliation(StatisticsReconciliationScheduler)이 최후 안전망이다.
     */
    @Async("eventTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Retry(name = "statisticsRebuild")
    public void onCollected(WinningNumbersCollectedEvent event) {
        if (!event.dataChanged()) {
            return;
        }
        log.info("WinningNumbersCollectedEvent 수신 — statistics summary 갱신: round={}", event.round());
        summaryRebuilder.rebuildAllSummaries();
    }
}
