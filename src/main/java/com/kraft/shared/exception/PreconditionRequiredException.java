package com.kraft.shared.exception;

/**
 * 수정 요청에 기준 버전이 전혀 없을 때 던진다 — {@code If-Match} 헤더도, 전환기 폴백인 본문 {@code version}도
 * 없는 경우다. 조건 없이 덮어쓰면 오래된 화면이 다른 사람의 저장을 말없이 지우므로, 기준 버전 없는 수정은
 * 받지 않는다. {@code ApiExceptionHandler}가 428 Precondition Required로 바꾼다.
 */
public class PreconditionRequiredException extends RuntimeException {

    public PreconditionRequiredException(String message) {
        super(message);
    }
}
