package com.kraft.web.dto.user;

import com.kraft.config.auth.dto.SessionUser;

/**
 * 로그인 응답 DTO
 * Record 클래스로 불변성과 간결성 보장
 */
public record LoginResponseDto(SessionUser user) {

    /**
     * 정적 팩토리 메서드 - SessionUser에서 생성
     */
    public static LoginResponseDto from(SessionUser sessionUser) {
        return new LoginResponseDto(sessionUser);
    }
}

