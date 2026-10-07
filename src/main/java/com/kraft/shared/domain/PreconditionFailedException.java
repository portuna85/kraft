package com.kraft.shared.domain;

import org.springframework.orm.ObjectOptimisticLockingFailureException;

/**
 * 수정 요청이 "이 버전을 기준으로 고친다"고 밝힌 버전({@code If-Match})이 지금 DB의 버전과 다를 때 던진다.
 * 요청이 서버에 닿기 전에 이미 다른 곳에서 저장이 일어난 경우다 — {@code ApiExceptionHandler}가
 * 412 Precondition Failed로 바꾼다.
 * <p>
 * {@link ObjectOptimisticLockingFailureException}의 하위 타입으로 둔 이유: 같은 종류의 충돌(낙관적 잠금)이고,
 * 저장 시점(flush)에 JPA가 던지는 같은 계열의 예외(409)를 다루는 코드와 테스트가 그대로 이 예외도 잡는다.
 * 핸들러는 더 구체적인 타입이 우선하므로 이 예외만 412로 나간다.
 */
public class PreconditionFailedException extends ObjectOptimisticLockingFailureException {

    public PreconditionFailedException(Class<?> entityType, Object id) {
        super(entityType, id);
    }
}
