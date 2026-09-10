package com.kraft.service.user;

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
 * {@link EmailVerificationService} 단위 테스트. {@code @Value}로 주입되는 {@code baseUrl}은
 * Mockito 순수 단위 테스트에서는 스프링 컨텍스트가 없어 자동 주입되지 않으므로
 * {@link ReflectionTestUtils}로 직접 값을 설정한다({@code CommentServiceTest}와 동일한 패턴).
 */
@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    private static final String BASE_URL = "http://localhost:8080";

    @Mock
    private EmailVerificationTokenRepository tokenRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserService userService;

    @Mock
    private EmailSender emailSender;

    private EmailVerificationService emailVerificationService;

    @BeforeEach
    void setUp() {
        emailVerificationService = new EmailVerificationService(tokenRepository, userRepository, userService, emailSender);
        ReflectionTestUtils.setField(emailVerificationService, "baseUrl", BASE_URL);
    }

    private static User userWithId(Long id, String email) {
        User user = User.builder().name("tester").email(email).password("encoded").role(Role.GUEST).build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    @Test
    @DisplayName("sendVerificationEmail: 존재하지 않는 회원이면 IllegalArgumentException")
    void sendVerificationEmail_회원_없으면_예외() {
        given(userRepository.findByEmailHash(EmailHasher.sha512Hex("nobody@example.com"))).willReturn(Optional.empty());

        assertThatThrownBy(() -> emailVerificationService.sendVerificationEmail("nobody@example.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("존재하지 않는 회원입니다");

        verify(tokenRepository, never()).save(any());
        verify(emailSender, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("sendVerificationEmail: 회원이 존재하면 토큰을 저장하고 baseUrl+token 링크가 포함된 메일을 발송한다")
    void sendVerificationEmail_정상_발송() {
        User user = userWithId(1L, "tester@example.com");
        given(userRepository.findByEmailHash(EmailHasher.sha512Hex("tester@example.com"))).willReturn(Optional.of(user));

        emailVerificationService.sendVerificationEmail("tester@example.com");

        ArgumentCaptor<EmailVerificationToken> tokenCaptor = ArgumentCaptor.forClass(EmailVerificationToken.class);
        verify(tokenRepository).save(tokenCaptor.capture());
        EmailVerificationToken savedToken = tokenCaptor.getValue();
        assertThat(savedToken.getToken()).isNotBlank();
        assertThat(savedToken.getUser()).isEqualTo(user);
        assertThat(savedToken.getExpiresAt()).isAfter(LocalDateTime.now());

        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailSender).send(org.mockito.ArgumentMatchers.eq("tester@example.com"), anyString(), textCaptor.capture());
        assertThat(textCaptor.getValue()).contains(BASE_URL + "/users/verify?token=" + savedToken.getToken());
    }

    @Test
    @DisplayName("sendVerificationEmailSafely: 내부에서 예외가 발생해도 전파되지 않는다")
    void sendVerificationEmailSafely_예외를_흡수한다() {
        given(userRepository.findByEmailHash(EmailHasher.sha512Hex("nobody@example.com"))).willReturn(Optional.empty());

        emailVerificationService.sendVerificationEmailSafely("nobody@example.com");

        verify(tokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("verify: 존재하지 않는 토큰이면 IllegalArgumentException")
    void verify_토큰_없으면_예외() {
        given(tokenRepository.findByToken("unknown")).willReturn(Optional.empty());

        assertThatThrownBy(() -> emailVerificationService.verify("unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("유효하지 않은 인증 링크입니다");

        verify(userService, never()).promoteToUser(any());
    }

    @Test
    @DisplayName("verify: 만료된 토큰이면 IllegalArgumentException을 던지고 토큰을 삭제한다")
    void verify_만료된_토큰이면_예외를_던지고_삭제한다() {
        User user = userWithId(1L, "tester@example.com");
        EmailVerificationToken expiredToken = EmailVerificationToken.builder()
                .token("expired-token")
                .user(user)
                .expiresAt(LocalDateTime.now().minusHours(1))
                .build();
        given(tokenRepository.findByToken("expired-token")).willReturn(Optional.of(expiredToken));

        assertThatThrownBy(() -> emailVerificationService.verify("expired-token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("만료되었습니다");

        verify(tokenRepository).delete(expiredToken);
        verify(userService, never()).promoteToUser(any());
    }

    @Test
    @DisplayName("verify: 유효한 토큰이면 회원을 승격시키고 토큰을 삭제한다")
    void verify_유효한_토큰이면_승격시키고_삭제한다() {
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
}
