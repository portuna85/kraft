package com.kraft.service.support;

import com.kraft.domain.user.Role;
import com.kraft.domain.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WriteAccessPolicyTest {

    private static User userOf(Role role) {
        return User.builder().name("tester").email("tester@example.com").password("encoded").role(role).build();
    }

    @Test
    @DisplayName("GUEST 회원이면 AccessDeniedException이 발생한다")
    void requireVerified_whenGuest_throwsAccessDeniedException() {
        assertThatThrownBy(() -> WriteAccessPolicy.requireVerified(userOf(Role.GUEST)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("이메일 인증을 완료해야");
    }

    @Test
    @DisplayName("USER 회원이면 예외 없이 통과한다")
    void requireVerified_whenUser_passesWithoutException() {
        assertThatCode(() -> WriteAccessPolicy.requireVerified(userOf(Role.USER))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("ADMIN 회원이면 예외 없이 통과한다")
    void requireVerified_whenAdmin_passesWithoutException() {
        assertThatCode(() -> WriteAccessPolicy.requireVerified(userOf(Role.ADMIN))).doesNotThrowAnyException();
    }
}
