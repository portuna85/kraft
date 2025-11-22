package com.kraft.service;

import com.kraft.domain.user.Role;
import com.kraft.domain.user.User;
import com.kraft.domain.user.UserRepository;
import com.kraft.web.dto.user.UserProfileResponseDto;
import com.kraft.common.exception.DuplicateResourceException;
import com.kraft.common.exception.UnauthorizedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @Test
    @DisplayName("회원가입에 성공한다")
    void register_success() {
        // given
        given(userRepository.existsByName("testuser")).willReturn(false);
        given(userRepository.existsByEmail("test@example.com")).willReturn(false);
        given(passwordEncoder.encode("password123")).willReturn("encoded");
        given(userRepository.save(any(User.class))).willAnswer(invocation -> {
            User user = invocation.getArgument(0);
            return User.builder()
                    .name(user.getName())
                    .password(user.getPassword())
                    .email(user.getEmail())
                    .role(Role.USER)
                    .build();
        });

        // when
        Long userId = userService.register("testuser", "password123", "test@example.com");

        // then
        verify(passwordEncoder).encode("password123");
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("중복된 이름으로 회원가입하면 예외가 발생한다")
    void register_duplicateName() {
        // given
        given(userRepository.existsByName("testuser")).willReturn(true);

        // expect
        assertThatThrownBy(() -> userService.register("testuser", "password123", "test@example.com"))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("이미 존재하는");
    }

    @Test
    @DisplayName("중복된 이메일로 회원가입하면 예외가 발생한다")
    void register_duplicateEmail() {
        // given
        given(userRepository.existsByName("testuser")).willReturn(false);
        given(userRepository.existsByEmail("test@example.com")).willReturn(true);

        // expect
        assertThatThrownBy(() -> userService.register("testuser", "password123", "test@example.com"))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("이미 존재하는");
    }

    @Test
    @DisplayName("이메일 변경에 성공한다")
    void updateEmail_success() {
        // given
        User user = User.of("testuser", "encoded", "old@example.com");
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(userRepository.existsByEmailAndIdNot("new@example.com", 1L)).willReturn(false);

        // when
        UserProfileResponseDto result = userService.updateEmail(1L, "new@example.com");

        // then
        assertThat(result.email()).isEqualTo("new@example.com");
    }

    @Test
    @DisplayName("중복된 이메일로 변경하면 예외가 발생한다")
    void updateEmail_duplicate() {
        // given
        User user = User.of("testuser", "encoded", "old@example.com");
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(userRepository.existsByEmailAndIdNot("duplicate@example.com", 1L)).willReturn(true);

        // expect
        assertThatThrownBy(() -> userService.updateEmail(1L, "duplicate@example.com"))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("이미");
    }

    @Test
    @DisplayName("비밀번호 변경에 성공한다")
    void changePassword_success() {
        // given
        User user = User.of("testuser", "encodedOld", "test@example.com");
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(passwordEncoder.matches("oldPassword", "encodedOld")).willReturn(true);
        given(passwordEncoder.encode("newPassword")).willReturn("encodedNew");

        // when
        userService.changePassword(1L, "oldPassword", "newPassword");

        // then
        verify(passwordEncoder).matches("oldPassword", "encodedOld");
        verify(passwordEncoder).encode("newPassword");
    }

    @Test
    @DisplayName("현재 비밀번호가 틀리면 예외가 발생한다")
    void changePassword_wrongCurrent() {
        // given
        User user = User.of("testuser", "encodedOld", "test@example.com");
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(passwordEncoder.matches("wrongPassword", "encodedOld")).willReturn(false);

        // expect
        assertThatThrownBy(() -> userService.changePassword(1L, "wrongPassword", "newPassword"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("비밀번호");
    }

    @Test
    @DisplayName("회원 탈퇴에 성공한다")
    void delete_success() {
        // given
        User user = User.of("testuser", "encoded", "test@example.com");
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        // when
        userService.delete(1L);

        // then
        verify(userRepository).delete(user);
    }
}

