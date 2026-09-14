package com.kraft.service.user;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.kraft.domain.user.EmailHasher;
import com.kraft.domain.user.EmailVerificationToken;
import com.kraft.domain.user.EmailVerificationTokenRepository;
import com.kraft.domain.user.Role;
import com.kraft.domain.user.User;
import com.kraft.domain.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * {@link EmailVerificationService} 단위 테스트.
 * <p>
 * 메일 본문과 baseUrl은 더 이상 이 서비스의 관심사가 아니다 — 서비스는 대기열에 넣기만 하고
 * 링크 생성과 발송은 {@link OutboxMailWorker}가 한다. 그래서 여기서는 "같은 토큰이 대기열에
 * 들어갔는가"까지만 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {


    @Mock
    private EmailVerificationTokenRepository tokenRepository;

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

    private EmailVerificationService emailVerificationService;

    @BeforeEach
    void setUp() {
        emailVerificationService = new EmailVerificationService(tokenRepository, userRepository, userService,
                outboxMailStore, outboxMailWorker, expiredTokenPurger);
    }

    private static User userWithId(Long id, String email) {
        User user = User.builder().name("tester").email(email).password("encoded").role(Role.GUEST).build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    @Test
    @DisplayName("sendVerificationEmail: 존재하지 않는 회원이면 IllegalArgumentException")
    void sendVerificationEmail_whenUserNotFound_throwsIllegalArgumentException() {
        given(userRepository.findByEmailHash(EmailHasher.sha512Hex("nobody@example.com"))).willReturn(Optional.empty());

        assertThatThrownBy(() -> emailVerificationService.sendVerificationEmail("nobody@example.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("존재하지 않는 회원입니다");

        verify(tokenRepository, never()).save(any());
        verify(outboxMailStore, never()).enqueue(any(), anyString());
    }

    @Test
    @DisplayName("sendVerificationEmail: 회원이 존재하면 토큰을 저장하고 같은 토큰으로 메일을 대기열에 넣는다")
    void sendVerificationEmail_whenUserExists_savesTokenAndSendsEmail() {
        User user = userWithId(1L, "tester@example.com");
        given(userRepository.findByEmailHash(EmailHasher.sha512Hex("tester@example.com"))).willReturn(Optional.of(user));

        emailVerificationService.sendVerificationEmail("tester@example.com");

        ArgumentCaptor<EmailVerificationToken> tokenCaptor = ArgumentCaptor.forClass(EmailVerificationToken.class);
        verify(tokenRepository).save(tokenCaptor.capture());
        EmailVerificationToken savedToken = tokenCaptor.getValue();
        assertThat(savedToken.getToken()).isNotBlank();
        assertThat(savedToken.getUser()).isEqualTo(user);
        assertThat(savedToken.getExpiresAt()).isAfter(LocalDateTime.now());

        // SMTP는 여기서 부르지 않는다. 같은 트랜잭션에서 대기열에 같은 토큰이 들어가야 한다.
        verify(outboxMailStore).enqueue(user, savedToken.getToken());
    }

    @Test
    @DisplayName("sendVerificationEmailSafely: 내부에서 예외가 발생해도 전파되지 않는다")
    void sendVerificationEmailSafely_absorbsInternalException() {
        given(userRepository.findByEmailHash(EmailHasher.sha512Hex("nobody@example.com"))).willReturn(Optional.empty());

        emailVerificationService.sendVerificationEmailSafely("nobody@example.com");

        verify(tokenRepository, never()).save(any());
    }

    /**
     * 이 경로가 이메일이 로그에 남을 수 있는 거의 유일한 자리다. 주소를 그대로 남기면
     * 암호화해 저장한 값이 로그 파일에는 평문으로 쌓인다(개선 보고서 "로그에 남는 이메일 최소화").
     * 로그는 DB보다 다루기 쉽고 오래 남으며 종종 그대로 복사되어 나간다.
     */
    @Test
    @DisplayName("sendVerificationEmailSafely: 실패를 로그로 남기되 주소는 가린다")
    void sendVerificationEmailSafely_masksTheAddressInTheLog() {
        given(userRepository.findByEmailHash(anyString())).willReturn(Optional.empty());

        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        Logger logger = (Logger) LoggerFactory.getLogger(EmailVerificationService.class);
        logger.addAppender(logs);
        try {
            emailVerificationService.sendVerificationEmailSafely("identifiable.person@example.com");
        } finally {
            logger.detachAppender(logs);
        }

        assertThat(logs.list).singleElement().satisfies(event -> assertThat(event.getFormattedMessage())
                .as("실패 자체는 남아야 조사할 수 있다").contains("대기열에 넣지 못했습니다")
                .as("그러나 누구인지는 남기지 않는다").doesNotContain("identifiable.person")
                .as("도메인은 남긴다 — 특정 메일 서버만 실패하는지 보려면 필요하다")
                .contains("@example.com"));
    }

    @Test
    @DisplayName("verify: 존재하지 않는 토큰이면 IllegalArgumentException")
    void verify_whenTokenNotFound_throwsIllegalArgumentException() {
        given(tokenRepository.findByToken("unknown")).willReturn(Optional.empty());

        assertThatThrownBy(() -> emailVerificationService.verify("unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("유효하지 않은 인증 링크입니다");

        verify(userService, never()).promoteToUser(any());
    }

    @Test
    @DisplayName("verify: 만료된 토큰이면 별도 트랜잭션에 삭제를 맡기고 IllegalArgumentException을 던진다")
    void verify_whenTokenExpired_delegatesDeletionToPurgerAndThrows() {
        User user = userWithId(1L, "tester@example.com");
        EmailVerificationToken expiredToken = EmailVerificationToken.builder()
                .token("expired-token")
                .user(user)
                .expiresAt(LocalDateTime.now().minusHours(1))
                .build();
        ReflectionTestUtils.setField(expiredToken, "id", 42L);
        given(tokenRepository.findByToken("expired-token")).willReturn(Optional.of(expiredToken));

        assertThatThrownBy(() -> emailVerificationService.verify("expired-token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("만료되었습니다");

        // 이 트랜잭션에서 직접 지우면 이어지는 예외가 삭제까지 롤백시킨다(개선 보고서 F08).
        // "실제로 DB에서 사라지는지"는 ExpiredTokenPurgeTest가 진짜 트랜잭션으로 검증한다.
        verify(expiredTokenPurger).purge(42L);
        verify(tokenRepository, never()).delete(any());
        verify(userService, never()).promoteToUser(any());
    }

    @Test
    @DisplayName("verify: 유효한 토큰이면 회원을 승격시키고 토큰을 삭제한다")
    void verify_whenTokenValid_promotesUserAndDeletesToken() {
        User user = userWithId(1L, "tester@example.com");
        EmailVerificationToken validToken = EmailVerificationToken.builder()
                .token("valid-token")
                .user(user)
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();
        given(tokenRepository.findByToken("valid-token")).willReturn(Optional.of(validToken));

        emailVerificationService.verify("valid-token");

        verify(userService, times(1)).promoteToUser(1L);
        verify(tokenRepository).delete(validToken);
    }

    @Test
    @DisplayName("resend: 존재하지 않는 회원이면 IllegalArgumentException")
    void resend_whenUserNotFound_throwsIllegalArgumentException() {
        given(userRepository.findByEmailHash(EmailHasher.sha512Hex("nobody@example.com"))).willReturn(Optional.empty());

        assertThatThrownBy(() -> emailVerificationService.resend("nobody@example.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("존재하지 않는 회원입니다");

        verify(tokenRepository, never()).deleteByUserId(any());
        verify(outboxMailStore, never()).enqueue(any(), anyString());
    }

    @Test
    @DisplayName("resend: 이미 인증된(GUEST가 아닌) 회원이면 IllegalArgumentException")
    void resend_whenUserAlreadyVerified_throwsIllegalArgumentException() {
        User verifiedUser = User.builder().name("tester").email("tester@example.com").password("encoded").role(Role.USER).build();
        ReflectionTestUtils.setField(verifiedUser, "id", 1L);
        given(userRepository.findByEmailHash(EmailHasher.sha512Hex("tester@example.com"))).willReturn(Optional.of(verifiedUser));

        assertThatThrownBy(() -> emailVerificationService.resend("tester@example.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이미 인증된 계정입니다");

        verify(tokenRepository, never()).deleteByUserId(any());
        verify(outboxMailStore, never()).enqueue(any(), anyString());
    }

    @Test
    @DisplayName("resend: GUEST 회원이면 기존 토큰을 지우고 새 토큰으로 메일을 다시 대기열에 넣는다")
    void resend_whenUserIsGuest_deletesOldTokenAndResendsEmail() {
        User user = userWithId(1L, "tester@example.com");
        given(userRepository.findByEmailHash(EmailHasher.sha512Hex("tester@example.com"))).willReturn(Optional.of(user));

        emailVerificationService.resend("tester@example.com");

        verify(tokenRepository).deleteByUserId(1L);
        verify(tokenRepository).save(any(EmailVerificationToken.class));
        verify(outboxMailStore).enqueue(org.mockito.ArgumentMatchers.eq(user), anyString());
    }
}
