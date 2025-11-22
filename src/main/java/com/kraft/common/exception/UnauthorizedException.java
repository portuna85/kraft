package com.kraft.common.exception;

/**
 * 인증 실패 시 발생하는 예외
 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }

    public static UnauthorizedException invalidCredentials() {
        return new UnauthorizedException("아이디 또는 비밀번호가 올바르지 않습니다");
    }

    public static UnauthorizedException sessionExpired() {
        return new UnauthorizedException("세션이 만료되었습니다. 다시 로그인해주세요");
    }
}

