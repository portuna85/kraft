package com.kraft.service.support;

import com.kraft.domain.user.Role;
import com.kraft.domain.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WriteAccessPolicyTest {

    private static User userOf(Role role) {
        return User.builder().name("tester").email("tester@example.com").password("encoded").role(role).build();
    }

    @Test
    void requireVerified_GUEST면_AccessDeniedException() {
        assertThatThrownBy(() -> WriteAccessPolicy.requireVerified(userOf(Role.GUEST)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("이메일 인증을 완료해야");
    }

    @Test
    void requireVerified_USER면_예외없이_통과() {
        assertThatCode(() -> WriteAccessPolicy.requireVerified(userOf(Role.USER))).doesNotThrowAnyException();
    }

    @Test
    void requireVerified_ADMIN이면_예외없이_통과() {
        assertThatCode(() -> WriteAccessPolicy.requireVerified(userOf(Role.ADMIN))).doesNotThrowAnyException();
    }
}
