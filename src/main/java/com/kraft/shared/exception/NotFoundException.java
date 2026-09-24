package com.kraft.shared.exception;

/**
 * "대상이 존재하지 않는다"는 뜻의 공용 예외(BE-07). {@code IllegalArgumentException}을 상속하지
 * 않는다 — 지금까지 이 뜻의 오류 상당수가 IAE로 던져져 {@code ApiExceptionHandler}의 범용 400
 * 핸들러에 걸렸다("검증 실패"와 "대상 없음"이 같은 상태 코드로 뭉뚱그려짐). {@code PostNotFoundException}처럼
 * 도메인마다 전용 타입을 새로 만드는 대신, 메시지만 다르고 나머지는 같은 경우를 위한 공용
 * 타입이다. {@code ApiExceptionHandler}가 404로 변환한다.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
