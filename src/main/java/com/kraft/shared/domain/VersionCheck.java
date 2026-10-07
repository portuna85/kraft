package com.kraft.shared.domain;

/**
 * 수정 요청이 기준으로 삼은 버전과 지금 DB의 버전이 다르면, 그 사이 다른 곳에서 저장이 일어난 것이다(F11).
 * 글·댓글 수정이 같은 규칙을 쓰므로 한 곳에 둔다. 던지는 {@link PreconditionFailedException}은
 * {@code ApiExceptionHandler}가 412로 변환한다 — 이 검사를 통과한 뒤 저장 시점(flush)에 겹친 저장이 있었다면
 * JPA가 던지는 낙관적 잠금 예외가 409로 나간다.
 * <p>
 * 기준 버전이 없는 요청은 컨트롤러가 428로 거절한다({@code EntityTags.expectedVersion}). null은 API를 거치지
 * 않는 내부 호출과 {@code If-Match: *}만 해당하며 그때는 검사하지 않는다.
 */
public final class VersionCheck {

    private VersionCheck() {
    }

    /**
     * @throws PreconditionFailedException {@code expectedVersion}이 있고 현재 버전과 다르면
     */
    public static void require(Class<?> entityType, Object id, Long currentVersion, Long expectedVersion) {
        if (expectedVersion != null && !expectedVersion.equals(currentVersion)) {
            throw new PreconditionFailedException(entityType, id);
        }
    }
}
