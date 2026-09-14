package com.kraft.shared.transaction;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * "DB 커밋이 끝난 뒤에 실행한다"를 한 줄로 쓰기 위한 순수 정적 유틸리티.
 * {@link OwnershipPolicy}·{@link WriteAccessPolicy}와 같은 스타일이다.
 * <p>
 * 파일 삭제와 세션 폐기는 DB 트랜잭션에 참여하지 않는다. 트랜잭션 안에서 먼저 실행하면
 * 이후 커밋이 실패해도 되돌아오지 않아, 게시글은 남았는데 이미지 파일만 사라지거나
 * (개선 보고서 F05) 비밀번호는 그대로인데 세션만 끊기는 상태가 된다. 그래서 이런 작업은
 * 전부 커밋 이후로 미룬다.
 * <p>
 * 트랜잭션 밖에서 호출하면 등록할 동기화 지점이 없으므로 즉시 실행한다.
 */
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
                action.run();
            }
        });
    }
}
