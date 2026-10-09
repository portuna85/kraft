package com.kraft.shared.exception;

/**
 * 사용자 입력·상태 때문에 요청을 처리할 수 없다는 검증 실패. {@code ApiExceptionHandler}가
 * 400으로 변환한다. 예전에는 메시지에 한글이 있는지로 "검증 실패(400)"와 "프로그래밍 오류(500)"를
 * 가렸는데, 영문 검증 메시지는 500이 되고 라이브러리가 던진 한글 메시지는 400이 되는 구멍이 있었다.
 * 이제 이 타입만 400이고, 그 밖의 {@link IllegalArgumentException}은 500이다.
 * <p>
 * 기존 {@code catch (IllegalArgumentException)} 코드가 그대로 동작하도록 IAE를 상속한다.
 */
public class BusinessValidationException extends IllegalArgumentException {

    public BusinessValidationException(String message) {
        super(message);
    }

    public BusinessValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
