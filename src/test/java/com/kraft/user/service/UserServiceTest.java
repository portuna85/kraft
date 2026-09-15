package com.kraft.user.service;

import com.kraft.user.domain.EmailHasher;
import com.kraft.user.domain.EmailVerificationTokenRepository;
import com.kraft.user.domain.PasswordResetTokenRepository;
import com.kraft.user.domain.Role;
import com.kraft.user.domain.User;
import com.kraft.user.domain.UserRepository;
import com.kraft.user.mail.OutboxMailRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * {@link UserService} 단위 테스트. {@link PasswordEncoder}도 모킹해 실제 BCrypt 연산 없이
 * "인코딩된 값이 저장되는가"만 빠르게 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private SessionRevoker sessionRevoker;

    @Mock
    private EmailVerificationTokenRepository emailVerificationTokenRepository;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private OutboxMailRepository outboxMailRepository;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, passwordEncoder, sessionRevoker,
                emailVerificationTokenRepository, passwordResetTokenRepository, outboxMailRepository);
    }

    @Test
    @DisplayName("signUp: 이메일이 중복되지 않으면 비밀번호를 인코딩해 GUEST로 저장한다")
    void signUp_whenValid_encodesPasswordAndSavesGuestUser() {
        given(userRepository.existsByName("new")).willReturn(false);
        given(userRepository.existsByEmailHash(EmailHasher.sha512Hex("new@example.com"))).willReturn(false);
        given(passwordEncoder.encode("rawPassword")).willReturn("encodedPassword");

        User saved = User.builder().name("new").email("new@example.com")
                .password("encodedPassword").role(Role.GUEST).build();
        ReflectionTestUtils.setField(saved, "id", 1L);
        given(userRepository.save(any(User.class))).willReturn(saved);

        Long id = userService.signUp("new", "new@example.com", "rawPassword");

        assertThat(id).isEqualTo(1L);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo(Role.GUEST);
        assertThat(captor.getValue().getPassword()).isEqualTo("encodedPassword");
        assertThat(captor.getValue().getEmail()).isEqualTo("new@example.com");
    }

    @Test
    @DisplayName("signUp: 이메일이 이미 있으면 IllegalArgumentException이고 저장을 시도하지 않는다")
    void signUp_whenEmailAlreadyExists_throwsIllegalArgumentExceptionAndDoesNotSave() {
        given(userRepository.existsByName("dup")).willReturn(false);
        given(userRepository.existsByEmailHash(EmailHasher.sha512Hex("dup@example.com"))).willReturn(true);

        assertThatThrownBy(() -> userService.signUp("dup", "dup@example.com", "pw12345678"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이미 가입된 이메일");

        verify(userRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    @DisplayName("signUp: 이름이 이미 있으면 IllegalArgumentException이고 이메일 중복확인·저장을 시도하지 않는다")
    void signUp_whenNameAlreadyExists_throwsIllegalArgumentExceptionAndDoesNotCheckEmailOrSave() {
        given(userRepository.existsByName("dupName")).willReturn(true);

        assertThatThrownBy(() -> userService.signUp("dupName", "new2@example.com", "pw12345678"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이미 사용중인 이름");

        verify(userRepository, never()).existsByEmailHash(any());
        verify(userRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    @DisplayName("changePassword: 현재 비밀번호가 일치하면 새 비밀번호로 변경한다")
    void changePassword_whenCurrentPasswordMatches_changesToNewPassword() {
        User user = User.builder().name("a").email("a@example.com").password("oldEncoded").role(Role.USER).build();
        given(userRepository.findByEmailHash(EmailHasher.sha512Hex("a@example.com"))).willReturn(Optional.of(user));
        given(passwordEncoder.matches("oldRaw", "oldEncoded")).willReturn(true);
        given(passwordEncoder.encode("newRawPassword")).willReturn("newEncoded");

        userService.changePassword("a@example.com", "oldRaw", "newRawPassword");

        assertThat(user.getPassword()).isEqualTo("newEncoded");
    }

    @Test
    @DisplayName("changePassword: 현재 비밀번호가 틀리면 IllegalArgumentException이고 비밀번호는 변경되지 않는다")
    void changePassword_whenCurrentPasswordMismatch_throwsIllegalArgumentException() {
        User user = User.builder().name("a").email("a@example.com").password("oldEncoded").role(Role.USER).build();
        given(userRepository.findByEmailHash(EmailHasher.sha512Hex("a@example.com"))).willReturn(Optional.of(user));
        given(passwordEncoder.matches("wrongRaw", "oldEncoded")).willReturn(false);

        assertThatThrownBy(() -> userService.changePassword("a@example.com", "wrongRaw", "newRawPassword"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("현재 비밀번호가 일치하지 않습니다");

        assertThat(user.getPassword()).isEqualTo("oldEncoded");
    }

    @Test
    @DisplayName("changePassword: 존재하지 않는 이메일이면 IllegalArgumentException")
    void changePassword_whenUserNotFound_throwsIllegalArgumentException() {
        given(userRepository.findByEmailHash(EmailHasher.sha512Hex("nobody@example.com"))).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.changePassword("nobody@example.com", "raw", "newRawPassword"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("존재하지 않는 회원");
    }

    @Test
    @DisplayName("withdraw: 이름·이메일·비밀번호를 지우고 남은 링크와 보낼 메일도 함께 치운다")
    void withdraw_anonymizesAccountAndClearsPendingArtifacts() {
        User user = User.builder().name("탈퇴할사람").email("bye@example.com").password("oldEncoded")
                .role(Role.USER).build();
        ReflectionTestUtils.setField(user, "id", 7L);
        given(userRepository.findByEmailHash(EmailHasher.sha512Hex("bye@example.com")))
                .willReturn(Optional.of(user));
        given(passwordEncoder.matches("rawPassword", "oldEncoded")).willReturn(true);
        given(passwordEncoder.encode(any())).willReturn("unusableEncoded");

        userService.withdraw("bye@example.com", "rawPassword");

        // 남는 것은 "이 글을 누군가 썼다"는 연결뿐이다.
        assertThat(user.isWithdrawn()).isTrue();
        assertThat(user.getName()).isEqualTo("탈퇴한 사용자7");
        assertThat(user.getEmail()).isEqualTo("withdrawn-7@kraft.invalid");
        assertThat(user.getPassword()).isEqualTo("unusableEncoded");

        // 탈퇴 후에도 옛 링크로 무언가 할 수 있는 길이 남으면 안 된다.
        verify(emailVerificationTokenRepository).deleteByUserId(7L);
        verify(passwordResetTokenRepository).deleteByUserId(7L);
        verify(outboxMailRepository).deleteByUserId(7L);
    }

    @Test
    @DisplayName("withdraw: 현재 비밀번호가 틀리면 아무것도 지우지 않는다")
    void withdraw_whenPasswordDoesNotMatch_changesNothing() {
        User user = User.builder().name("탈퇴할사람").email("bye@example.com").password("oldEncoded")
                .role(Role.USER).build();
        ReflectionTestUtils.setField(user, "id", 7L);
        given(userRepository.findByEmailHash(EmailHasher.sha512Hex("bye@example.com")))
                .willReturn(Optional.of(user));
        given(passwordEncoder.matches("wrongRaw", "oldEncoded")).willReturn(false);

        assertThatThrownBy(() -> userService.withdraw("bye@example.com", "wrongRaw"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("현재 비밀번호가 일치하지 않습니다");

        // 되돌릴 수 없는 작업이므로, 확인에 실패하면 한 줄도 건드리지 않아야 한다.
        assertThat(user.isWithdrawn()).isFalse();
        assertThat(user.getName()).isEqualTo("탈퇴할사람");
        verify(outboxMailRepository, never()).deleteByUserId(any());
    }

    @Test
    @DisplayName("promoteToUser: GUEST를 USER로 승격한다")
    void promoteToUser_promotesGuestToUser() {
        User user = User.builder().name("a").email("a@example.com").password("pw").role(Role.GUEST).build();
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        userService.promoteToUser(1L);

        assertThat(user.getRole()).isEqualTo(Role.USER);
    }

    @Test
    @DisplayName("promoteToUser: 존재하지 않는 회원 ID면 IllegalArgumentException")
    void promoteToUser_whenUserNotFound_throwsIllegalArgumentException() {
        given(userRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.promoteToUser(999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("id=999");
    }
}
