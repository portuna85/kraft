package com.kraft.config.auth.dto;

import com.kraft.domain.user.Role;
import com.kraft.domain.user.User;

import java.io.Serializable;

/**
 * 세션에 저장될 사용자 정보
 * Record 클래스로 불변성 보장
 */
public record SessionUser(
        Long id,
        String name,
        String email,
        Role role
) implements Serializable {

    public SessionUser(User user) {
        this(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole()
        );
    }

    public static SessionUser from(User user) {
        return new SessionUser(user);
    }
}