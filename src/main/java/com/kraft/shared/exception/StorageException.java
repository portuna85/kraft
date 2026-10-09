package com.kraft.shared.exception;

/**
 * 디스크 읽기·쓰기·삭제 실패를 나타낸다. {@code IllegalArgumentException}을
 * 쓰지 않는 이유는 이게 "사용자가 잘못 요청함"이 아니라 "서버 환경이 문제"이기 때문이다 —
 * IAE로 던지면 {@code ApiExceptionHandler}가 400으로 바꿔, 디스크가 가득 찬 상황이 사용자
 * 잘못으로 집계되고 5xx 경보에도 잡히지 않았다. {@code ApiExceptionHandler}가 500으로 변환한다.
 */
public class StorageException extends RuntimeException {

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
