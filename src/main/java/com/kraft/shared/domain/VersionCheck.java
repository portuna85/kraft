package com.kraft.shared.domain;

import org.springframework.orm.ObjectOptimisticLockingFailureException;

/**
 * 화면이 받아간 버전과 지금 DB의 버전이 다르면, 그 사이 다른 곳에서 저장이 일어난 것이다(F11).
 * 글·댓글 수정이 같은 규칙을 쓰므로 한 곳에 둔다. {@link ObjectOptimisticLockingFailureException}은
 * {@code ApiExceptionHandler}가 이미 409로 변환한다.
 * <p>
 * API 요청은 DTO 검증이 버전을 필수로 받는다. null은 API를 거치지 않는 내부 호출만 해당하며
 * 그때는 검사하지 않는다.
 */
public final class VersionCheck {

    private VersionCheck() {
    }

    /**
     * @throws ObjectOptimisticLockingFailureException {@code expectedVersion}이 있고 현재 버전과 다르면
     */
    public static void require(Class<?> entityType, Object id, Long currentVersion, Long expectedVersion) {
        if (expectedVersion != null && !expectedVersion.equals(currentVersion)) {
            throw new ObjectOptimisticLockingFailureException(entityType, id);
        }
    }
}
