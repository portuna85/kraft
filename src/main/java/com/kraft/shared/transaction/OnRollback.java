package com.kraft.shared.transaction;

import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * {@link AfterCommit}과 반대로, 트랜잭션이 <b>커밋 이외의 상태로 끝났을 때만</b> 보상 작업을
 * 실행한다. 파일 저장처럼 DB 트랜잭션에 참여하지 않는 부수 작업을 먼저 해 두고, 그 뒤에 이어지는
 * DB 등록이 성공해도 <b>바깥 트랜잭션의 최종 커밋 자체</b>가 실패할 수 있다(개선 보고서 "파일
 * 저장 성공 후 최종 커밋 실패 시 대장 없는 파일"). 메서드 안의 {@code catch}로는 이 실패를 잡을
 * 수 없다 — 커밋은 메서드가 반환한 뒤, 트랜잭션 프록시가 처리하기 때문이다.
 * <p>
 * 다만 Spring이 커밋 실패를 항상 {@code STATUS_ROLLED_BACK}으로 알려주지는 않는다 — 커밋
 * 도중 실패해 최종 상태를 확신할 수 없으면 {@code STATUS_UNKNOWN}으로 통지되기도 한다. 이
 * 유틸리티는 그 두 경우 모두 보상을 시도하는 <b>최선의 노력</b>이며, 그래도 놓치는 경우(커밋
 * 직후 프로세스 종료 등)를 위한 최후 수단은 별도의 주기적 디스크-대장 대조
 * ({@code OrphanFileReconciler})가 맡는다.
 */
@Slf4j
public final class OnRollback {

    private OnRollback() {
    }

    public static void run(Runnable compensation) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            // 트랜잭션 밖이면 커밋도 롤백도 없다 — 보상할 일이 없다.
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == TransactionSynchronization.STATUS_COMMITTED) {
                    return;
                }
                try {
                    compensation.run();
                } catch (RuntimeException e) {
                    log.error("[POST_ROLLBACK_COMPENSATION_FAILURE] 롤백 후 보상 작업이 실패했습니다.", e);
                }
            }
        });
    }
}
