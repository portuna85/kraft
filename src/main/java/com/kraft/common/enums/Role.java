package com.kraft.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 시스템 권한 Role
 * - ADMIN: 관리자 (모든 기능 접근 가능)
 * - USER: 일반 사용자 (기본 권한)
 */
@Getter
@RequiredArgsConstructor
public enum Role {

    ADMIN("ROLE_ADMIN", "관리자"),
    USER("ROLE_USER", "일반 사용자");

    private final String key;
    private final String title;

    /**
     * Spring Security 권한 문자열 반환
     */
    public String getAuthority() {
        return key;
    }
}

