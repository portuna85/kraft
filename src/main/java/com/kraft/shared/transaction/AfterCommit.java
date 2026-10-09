package com.kraft.shared.transaction;

import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * "DB 커밋이 끝난 뒤에 실행한다"를 한 줄로 쓰는 순수 정적 유틸. 파일 삭제·세션 폐기처럼 DB 트랜잭션에 참여하지 않는 작업을 먼저
 * 실행하면 이후 커밋이 실패해도 되돌릴 수 없어 DB와 외부 상태가 어긋나므로 커밋 이후로 미룬다. 트랜잭션 밖에서는 즉시 실행한다.
 */
@Slf4j
public final class AfterCommit {

    private AfterCommit() {
    }

    public static void run(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                // afterCommit 콜백의 예외는 호출한 메서드 밖으로 전파돼, DB는 이미 커밋됐는데 응답이 500이 된다(예: 비밀번호는
                // 바뀌었는데 세션 폐기 실패로 사용자가 옛 비밀번호로 재시도). 잡아서 로그로만 남긴다.
                try {
                    action.run();
                } catch (RuntimeException e) {
                    log.error("[POST_COMMIT_FAILURE] 커밋 후 작업이 실패했습니다. DB 변경은 이미 커밋된 상태입니다.", e);
                }
            }
        });
    }
}
