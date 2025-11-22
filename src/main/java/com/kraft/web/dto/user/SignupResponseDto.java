package com.kraft.web.dto.user;

import com.kraft.domain.user.User;

/**
 * 회원가입 응답 DTO
 * Record 클래스로 불변성과 간결성 보장
 */
public record SignupResponseDto(
        Long userId,
        String name,
        String email
) {
    /**
     * 정적 팩토리 메서드 - 개별 파라미터로 생성
     */
    public static SignupResponseDto of(Long userId, String name, String email) {
        return new SignupResponseDto(userId, name, email);
    }

    /**
     * 정적 팩토리 메서드 - User 엔티티에서 생성
     */
    public static SignupResponseDto from(User user) {
        return new SignupResponseDto(
                user.getId(),
                user.getName(),
                user.getEmail()
        );
    }
}

