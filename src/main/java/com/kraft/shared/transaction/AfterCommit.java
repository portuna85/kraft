package com.kraft.shared.transaction;

import lombok.extern.slf4j.Slf4j;
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
                // Spring은 afterCommit 콜백이 던진 예외를 그대로 호출한 트랜잭션 메서드
                // 밖으로 전파한다 — DB는 이미 커밋되었는데도 응답은 500이 된다(개선 보고서
                // "커밋 후 실패가 이미 커밋된 변경을 실패 응답으로 보이게 함"). 비밀번호 변경
                // 뒤 세션 폐기가 실패하는 경우가 실제 사례다: 비밀번호는 이미 바뀌었는데
                // 사용자는 요청 전체가 실패했다고 믿고 옛 비밀번호로 재시도하게 된다. 여기서
                // 잡아 로그로만 남긴다 — 실패를 완전히 숨기지 않으면서도, 이미 끝난 DB 변경을
                // 실패로 보이게 하지 않는다.
                try {
                    action.run();
                } catch (RuntimeException e) {
                    log.error("[POST_COMMIT_FAILURE] 커밋 후 작업이 실패했습니다. DB 변경은 이미 커밋된 상태입니다.", e);
                }
            }
        });
    }
}
