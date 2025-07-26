package com.kraft.logistics.security;

import com.kraft.logistics.domain.user.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class SecurityUtil {

    public static User getLoginUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("현재 인증된 사용자가 없습니다.");
        }

        Object principal = authentication.getPrincipal();
        if (!(principal instanceof CustomUserDetails userDetails)) {
            throw new IllegalStateException("인증 정보가 올바르지 않습니다.");
        }

        return userDetails.getUser();
    }

    public static Long getLoginUserId() {
        return getLoginUser().getId();
    }

    public static String getLoginUsername() {
        return getLoginUser().getUsername();
    }
}
