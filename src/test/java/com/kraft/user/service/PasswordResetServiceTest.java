package com.kraft.user.service;

import com.kraft.user.domain.EmailHasher;
import com.kraft.user.domain.PasswordResetToken;
import com.kraft.user.domain.PasswordResetTokenRepository;
import com.kraft.user.domain.Role;
import com.kraft.user.domain.User;
import com.kraft.user.domain.UserRepository;
import com.kraft.user.mail.OutboxMailKind;
import com.kraft.user.mail.OutboxMailStore;
import com.kraft.user.mail.OutboxMailWorker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * {@link PasswordResetService} 단위 테스트.
 * <p>
 * 이 기능에서 가장 지키기 어려운 성질은 "요청 단계가 아무것도 알려주지 않는다"이다. 가입하지
 * 않은 주소와 가입한 주소의 동작이 조금이라도 갈리면(예외, 다른 응답, 다른 소요 시간) 그것만으로
 * 계정 존재 여부를 확인할 수 있다. 앞의 세 테스트가 그 성질을 고정한다.
 */
@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock
    private PasswordResetTokenRepository tokenRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserService userService;

    @Mock
    private OutboxMailStore outboxMailStore;

    @Mock
    private OutboxMailWorker outboxMailWorker;

    @Mock
    private ExpiredTokenPurger expiredTokenPurger;

    private PasswordResetService passwordResetService;

    @BeforeEach
    void setUp() {
        passwordResetService = new PasswordResetService(tokenRepository, userRepository, userService,
                outboxMailStore, outboxMailWorker, expiredTokenPurger);
    }

    private static User userWithId(Long id, String email) {
        User user = User.builder().name("tester").email(email).password("encoded").role(Role.USER).build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    @Test
    @DisplayName("request: 가입하지 않은 주소는 예외 없이 조용히 끝난다 — 응답으로 가입 여부를 알 수 없어야 한다")
    void request_whenEmailIsNotRegistered_doesNothingQuietly() {
        given(userRepository.findByEmailHash(EmailHasher.sha512Hex("nobody@example.com")))
                .willReturn(Optional.empty());

        passwordResetService.request("nobody@example.com");

        verify(tokenRepository, never()).save(any());
        verify(outboxMailStore, never()).enqueue(any(), anyString(), any());
    }

    @Test
    @DisplayName("request: 가입한 주소면 옛 링크를 무효로 만들고 30분짜리 새 토큰을 메일 대기열에 넣는다")
    void request_whenEmailIsRegistered_replacesTokenAndQueuesMail() {
        User user = userWithId(7L, "user@example.com");
        given(userRepository.findByEmailHash(EmailHasher.sha512Hex("user@example.com")))
                .willReturn(Optional.of(user));
        given(outboxMailStore.lastQueuedAt(7L, OutboxMailKind.PASSWORD_RESET)).willReturn(Optional.empty());

        passwordResetService.request("user@example.com");

        // 메일함에 살아 있는 링크가 하나뿐이어야 한다.
        verify(tokenRepository).deleteByUserId(7L);

        ArgumentCaptor<PasswordResetToken> saved = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).save(saved.capture());
        assertThat(saved.getValue().getUser()).isSameAs(user);
        assertThat(saved.getValue().getExpiresAt())
                .isBetween(LocalDateTime.now().plusMinutes(29), LocalDateTime.now().plusMinutes(31));

        // 대기열에는 평문 토큰이 실린다(발송 본문에 필요하다). 저장된 조회 테이블 행에는
        // 그 해시만 있으므로(SEC-04), 둘을 직접 비교하는 대신 같은 값에서 나온 것인지 확인한다.
        ArgumentCaptor<String> enqueuedToken = ArgumentCaptor.forClass(String.class);
        verify(outboxMailStore).enqueue(eq(user), enqueuedToken.capture(), eq(OutboxMailKind.PASSWORD_RESET));
        assertThat(EmailHasher.sha512Hex(enqueuedToken.getValue())).isEqualTo(saved.getValue().getTokenHash());
    }

    @Test
    @DisplayName("request: 방금 보냈으면 다시 보내지 않는다 — 거절도 조용히 한다")
    void request_whenRequestedTooRecently_doesNotQueueAnotherMail() {
        User user = userWithId(7L, "user@example.com");
        given(userRepository.findByEmailHash(EmailHasher.sha512Hex("user@example.com")))
                .willReturn(Optional.of(user));
        given(outboxMailStore.lastQueuedAt(7L, OutboxMailKind.PASSWORD_RESET))
                .willReturn(Optional.of(LocalDateTime.now().minusSeconds(5)));

        passwordResetService.request("user@example.com");

        // 예외를 던지면 "이 주소는 가입되어 있다"를 알려주는 셈이 된다.
        verify(tokenRepository, never()).save(any());
        verify(outboxMailStore, never()).enqueue(any(), anyString(), any());
    }

    @Test
    @DisplayName("request: 요청 제한은 메일 종류별로 본다 — 방금 받은 인증 메일이 재설정 요청을 막지 않는다")
    void request_cooldownIsPerMailKind() {
        // 가입 직후에는 인증 메일이 막 나간 상태다. 종류를 가리지 않고 제한하면 그 사람은
        // 비밀번호를 잊어도 1분 동안 아무 반응 없는 화면만 보게 된다.
        User user = userWithId(7L, "user@example.com");
        given(userRepository.findByEmailHash(EmailHasher.sha512Hex("user@example.com")))
                .willReturn(Optional.of(user));
        given(outboxMailStore.lastQueuedAt(7L, OutboxMailKind.PASSWORD_RESET)).willReturn(Optional.empty());

        passwordResetService.request("user@example.com");

        verify(outboxMailStore).enqueue(eq(user), anyString(), eq(OutboxMailKind.PASSWORD_RESET));
    }

    @Test
    @DisplayName("reset: 유효한 토큰이면 비밀번호를 바꾸고 그 토큰을 지운다(1회용)")
    void reset_withValidToken_changesPasswordAndConsumesToken() {
        User user = userWithId(7L, "user@example.com");
        String tokenHash = EmailHasher.sha512Hex("valid-token");
        PasswordResetToken token = PasswordResetToken.builder()
                .tokenHash(tokenHash)
                .user(user)
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .build();
        ReflectionTestUtils.setField(token, "id", 99L);
        given(tokenRepository.findByTokenHash(tokenHash)).willReturn(Optional.of(token));
        given(tokenRepository.deleteByIdAndTokenHash(99L, tokenHash)).willReturn(1);

        passwordResetService.reset("valid-token", "NewPass1!");

        // 메일함에 남은 링크를 두 번째로 눌러도 아무 일이 없어야 한다.
        verify(tokenRepository).deleteByIdAndTokenHash(99L, tokenHash);
        verify(userService).resetPassword(7L, "NewPass1!");
    }

    @Test
    @DisplayName("reset: 동시에 소비되어 이미 지워진 토큰이면 비밀번호를 바꾸지 않고 다시 요청하라고 알려준다")
    void reset_whenTokenAlreadyConsumedConcurrently_throwsAndDoesNotChangePassword() {
        User user = userWithId(7L, "user@example.com");
        String tokenHash = EmailHasher.sha512Hex("valid-token");
        PasswordResetToken token = PasswordResetToken.builder()
                .tokenHash(tokenHash)
                .user(user)
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .build();
        ReflectionTestUtils.setField(token, "id", 99L);
        given(tokenRepository.findByTokenHash(tokenHash)).willReturn(Optional.of(token));
        // 다른 요청이 먼저 소비해 이미 지워졌다 — 조건부 삭제가 0행을 돌려준다.
        given(tokenRepository.deleteByIdAndTokenHash(99L, tokenHash)).willReturn(0);

        assertThatThrownBy(() -> passwordResetService.reset("valid-token", "NewPass1!"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이미 사용되었거나");

        verify(userService, never()).resetPassword(anyLong(), anyString());
    }

    @Test
    @DisplayName("reset: 없는 토큰이면 다시 요청하라고 알려준다")
    void reset_withUnknownToken_isRejected() {
        given(tokenRepository.findByTokenHash(EmailHasher.sha512Hex("unknown"))).willReturn(Optional.empty());

        assertThatThrownBy(() -> passwordResetService.reset("unknown", "NewPass1!"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("유효하지 않은 재설정 링크");

        verify(userService, never()).resetPassword(anyLong(), anyString());
    }

    @Test
    @DisplayName("reset: 만료된 토큰은 거부하고, 삭제는 롤백되지 않도록 별도 트랜잭션에 맡긴다")
    void reset_withExpiredToken_isRejectedAndPurgedInItsOwnTransaction() {
        User user = userWithId(7L, "user@example.com");
        String tokenHash = EmailHasher.sha512Hex("expired-token");
        PasswordResetToken expired = PasswordResetToken.builder()
                .tokenHash(tokenHash)
                .user(user)
                .expiresAt(LocalDateTime.now().minusMinutes(1))
                .build();
        ReflectionTestUtils.setField(expired, "id", 42L);
        given(tokenRepository.findByTokenHash(tokenHash)).willReturn(Optional.of(expired));

        assertThatThrownBy(() -> passwordResetService.reset("expired-token", "NewPass1!"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("만료");

        verify(userService, never()).resetPassword(anyLong(), anyString());
        // 이 트랜잭션에서 지우면 뒤따르는 예외와 함께 삭제도 롤백된다(F08과 같은 덫).
        verify(expiredTokenPurger).purgePasswordResetToken(42L);
        verify(tokenRepository, never()).delete(expired);
    }
}
