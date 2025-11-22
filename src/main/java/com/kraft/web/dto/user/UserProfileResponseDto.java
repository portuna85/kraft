package com.kraft.web.dto.user;

import com.kraft.domain.user.User;

/**
 * 사용자 프로필 응답 DTO
 * Record 클래스로 불변성과 간결성 보장
 */
public record UserProfileResponseDto(
        Long id,
        String name,
        String email
) {
    /**
     * 정적 팩토리 메서드 - User 엔티티에서 생성
     */
    public static UserProfileResponseDto from(User user) {
        return new UserProfileResponseDto(
                user.getId(),
                user.getName(),
                user.getEmail()
        );
    }
}

