package com.kraft.shared.transaction;

import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * {@link AfterCommit}과 반대로, 트랜잭션이 <b>커밋 이외로 끝났을 때만</b> 보상 작업을 실행한다. 파일 저장처럼 DB에 참여하지 않는
 * 작업을 먼저 해 두면, 이어진 DB 등록이 성공해도 바깥 트랜잭션의 최종 커밋이 실패할 수 있고 메서드 안 {@code catch}로는 못 잡는다
 * (커밋은 메서드 반환 뒤 프록시가 처리). 커밋 도중 실패는 {@code STATUS_UNKNOWN}으로 통지되기도 해 그 경우도 보상하는 최선의
 * 노력이며, 그래도 놓치는 경우는 주기적 디스크-대장 대조({@code OrphanFileReconciler})가 맡는다.
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
