package com.kraft.shared.exception;

/**
 * 사용자 입력·상태 때문에 요청을 처리할 수 없다는 검증 실패. {@code ApiExceptionHandler}가 400으로 변환하며, 그 밖의
 * {@link IllegalArgumentException}은 프로그래밍 오류로 500이다. 기존 {@code catch (IllegalArgumentException)} 코드가 그대로
 * 동작하도록 IAE를 상속한다.
 */
public class BusinessValidationException extends IllegalArgumentException {

    public BusinessValidationException(String message) {
        super(message);
    }

    public BusinessValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
