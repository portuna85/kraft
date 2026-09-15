package com.kraft.shared.security;

import com.kraft.user.domain.Role;
import com.kraft.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Test
    @DisplayName("정지 중이면 거절하고 언제까지·왜인지 함께 알린다")
    void requireVerified_whenSuspended_throwsWithDeadlineAndReason() {
        User suspended = userOf(Role.USER);
        suspended.suspendUntil(LocalDateTime.now().plusDays(3), "욕설·비방 신고 처리(7일)");

        assertThatThrownBy(() -> WriteAccessPolicy.requireVerified(suspended))
                .isInstanceOf(AccessDeniedException.class)
                // 이유 없이 막히면 사용자는 고장으로 여기고 같은 시도를 반복한다.
                .hasMessageContaining("이용이 제한된 계정")
                .hasMessageContaining("시간 뒤")
                .hasMessageContaining("욕설·비방");
    }

    @Test
    @DisplayName("정지 기간이 지나면 아무것도 하지 않아도 저절로 풀린다")
    void requireVerified_whenSuspensionExpired_passes() {
        User served = userOf(Role.USER);
        served.suspendUntil(LocalDateTime.now().minusMinutes(1), "지난 정지");

        // 해제 배치가 없다. 배치가 멈춰서 정지가 안 풀리는 일도 없다는 뜻이다.
        assertThatCode(() -> WriteAccessPolicy.requireVerified(served)).doesNotThrowAnyException();
        assertThat(served.isSuspended()).isFalse();
    }

    @Test
    @DisplayName("관리자가 기간 전에 풀면 곧바로 다시 쓸 수 있다")
    void liftSuspension_restoresWriteAccess() {
        User suspended = userOf(Role.USER);
        suspended.suspendUntil(LocalDateTime.now().plusDays(3), "오판이었던 정지");

        suspended.liftSuspension();

        assertThatCode(() -> WriteAccessPolicy.requireVerified(suspended)).doesNotThrowAnyException();
        // 사유는 기록에서 지우지 않는다 — 무슨 일이 있었는지는 남아야 한다.
        assertThat(suspended.getSuspensionReason()).isEqualTo("오판이었던 정지");
    }

    @Test
    @DisplayName("GUEST이면서 정지 중이면 인증 안내를 먼저 준다 — 먼저 할 일이 그것이다")
    void blockReason_prefersVerificationOverSuspension() {
        User guest = userOf(Role.GUEST);
        guest.suspendUntil(LocalDateTime.now().plusDays(1), "정지");

        assertThat(WriteAccessPolicy.blockReason(guest)).contains("이메일 인증을 완료해야 글을 쓸 수 있습니다.");
    }
}
