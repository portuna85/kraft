package com.kraft.config.security;

import com.kraft.user.domain.Role;
import com.kraft.user.domain.User;
import com.kraft.user.domain.UserRepository;
import com.kraft.user.mail.OutboxMailKind;
import com.kraft.user.mail.OutboxMailStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 로그인 연속 실패가 계정에 누적되고, 임계를 넘으면 잠기며, 성공하면 지워지는지를 실제 DB로
 * 확인한다. 이벤트를 직접 만들어 보내는 이유는 {@code SecurityFilterChain} 전체를 띄우지
 * 않고도 {@link LoginLockoutService}의 리스너 메서드만 정확히 호출하기 위해서다.
 */
@SpringBootTest
class LoginLockoutServiceTest {

    @Autowired
    private LoginLockoutService loginLockoutService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OutboxMailStore outboxMailStore;

    private User user;

    @BeforeEach
    void setUp() {
        String unique = "lockout-" + UUID.randomUUID().toString().substring(0, 8);
        user = userRepository.save(User.builder()
                .name(unique)
                .email(unique + "@example.com")
                .password("encoded")
                .role(Role.USER)
                .build());
    }

    private void fail() {
        UsernamePasswordAuthenticationToken attempt =
                UsernamePasswordAuthenticationToken.unauthenticated(user.getEmail(), "wrong-password");
        loginLockoutService.onAuthenticationFailure(
                new AuthenticationFailureBadCredentialsEvent(attempt, new BadCredentialsException("bad")));
    }

    private User reload() {
        return userRepository.findById(user.getId()).orElseThrow();
    }

    @Test
    @DisplayName("실패 4번까지는 잠기지 않는다")
    void onAuthenticationFailure_belowThreshold_doesNotLock() {
        for (int i = 0; i < LoginLockoutService.LOCK_THRESHOLD - 1; i++) {
            fail();
        }

        User reloaded = reload();
        assertThat(reloaded.isLocked()).isFalse();
        assertThat(reloaded.getFailedLoginAttempts()).isEqualTo(LoginLockoutService.LOCK_THRESHOLD - 1);
    }

    @Test
    @DisplayName("실패가 임계를 넘으면 잠기고, 경고 메일이 한 번 쌓인다")
    void onAuthenticationFailure_atThreshold_locksAndQueuesWarningMail() {
        for (int i = 0; i < LoginLockoutService.LOCK_THRESHOLD; i++) {
            fail();
        }

        assertThat(reload().isLocked()).isTrue();
        assertThat(outboxMailStore.lastQueuedAt(user.getId(), OutboxMailKind.LOGIN_ATTEMPTS_WARNING)).isPresent();
    }

    @Test
    @DisplayName("잠긴 뒤 계속 실패하면 대기 시간이 두 배씩 늘어난다")
    void onAuthenticationFailure_pastThreshold_extendsLockDuration() {
        for (int i = 0; i < LoginLockoutService.LOCK_THRESHOLD; i++) {
            fail();
        }
        var lockedUntilAtThreshold = reload().getLockedUntil();

        fail();

        var lockedUntilAfterOneMore = reload().getLockedUntil();
        assertThat(lockedUntilAfterOneMore).isAfter(lockedUntilAtThreshold);
    }

    @Test
    @DisplayName("성공하면 누적과 잠금이 모두 풀린다")
    void onAuthenticationSuccess_resetsAttemptsAndLock() {
        for (int i = 0; i < LoginLockoutService.LOCK_THRESHOLD; i++) {
            fail();
        }
        assertThat(reload().isLocked()).isTrue();

        KraftUserDetails principal = new KraftUserDetails(user.getId(), user.getPassword(), user.getName(),
                List.of(new SimpleGrantedAuthority(user.getRoleKey())));
        loginLockoutService.onAuthenticationSuccess(new AuthenticationSuccessEvent(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities())));

        User reloaded = reload();
        assertThat(reloaded.isLocked()).isFalse();
        assertThat(reloaded.getFailedLoginAttempts()).isZero();
    }

    @Test
    @DisplayName("존재하지 않는 계정의 실패는 조용히 지나간다")
    void onAuthenticationFailure_forUnknownAccount_doesNothing() {
        UsernamePasswordAuthenticationToken attempt =
                UsernamePasswordAuthenticationToken.unauthenticated("nobody@example.com", "x");

        loginLockoutService.onAuthenticationFailure(
                new AuthenticationFailureBadCredentialsEvent(attempt, new BadCredentialsException("bad")));

        assertThat(reload().getFailedLoginAttempts()).isZero();
    }
}
