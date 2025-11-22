package com.kraft.service;

import com.kraft.config.auth.dto.SessionUser;
import com.kraft.domain.user.User;
import com.kraft.web.dto.user.LoginRequestDto;
import com.kraft.common.exception.UnauthorizedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserService userService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    @Test
    @DisplayName("올바른 정보로 로그인에 성공한다")
    void login_success() {
        // given
        String name = "testUser";
        String password = "password123";
        String encodedPassword = "encodedPassword";

        User user = User.builder()
                .name(name)
                .password(encodedPassword)
                .email("test@example.com")
                .build();

        LoginRequestDto requestDto = LoginRequestDto.builder()
                .name(name)
                .password(password)
                .build();

        given(userService.findByName(name)).willReturn(user);
        given(passwordEncoder.matches(password, encodedPassword)).willReturn(true);

        // when
        SessionUser sessionUser = authService.login(requestDto);

        // then
        assertThat(sessionUser).isNotNull();
        assertThat(sessionUser.name()).isEqualTo(name);
    }

    @Test
    @DisplayName("잘못된 비밀번호로 로그인하면 예외가 발생한다")
    void login_wrongPassword() {
        // given
        String name = "testUser";
        String wrongPassword = "wrongPassword";
        String encodedPassword = "encodedPassword";

        User user = User.builder()
                .name(name)
                .password(encodedPassword)
                .email("test@example.com")
                .build();

        LoginRequestDto requestDto = LoginRequestDto.builder()
                .name(name)
                .password(wrongPassword)
                .build();

        given(userService.findByName(name)).willReturn(user);
        given(passwordEncoder.matches(wrongPassword, encodedPassword)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> authService.login(requestDto))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("name과 password로 로그인에 성공한다")
    void login_withNameAndPassword() {
        // given
        String name = "testUser";
        String password = "password123";
        String encodedPassword = "encodedPassword";

        User user = User.builder()
                .name(name)
                .password(encodedPassword)
                .email("test@example.com")
                .build();

        given(userService.findByName(name)).willReturn(user);
        given(passwordEncoder.matches(password, encodedPassword)).willReturn(true);

        // when
        SessionUser sessionUser = authService.login(name, password);

        // then
        assertThat(sessionUser).isNotNull();
        assertThat(sessionUser.name()).isEqualTo(name);
    }

    @Test
    @DisplayName("존재하지 않는 사용자로 로그인하면 예외가 발생한다")
    void login_userNotFound() {
        // given
        LoginRequestDto requestDto = LoginRequestDto.builder()
                .name("nonexistent")
                .password("password")
                .build();

        given(userService.findByName("nonexistent"))
                .willThrow(new RuntimeException("사용자를 찾을 수 없습니다"));

        // when & then
        assertThatThrownBy(() -> authService.login(requestDto))
                .isInstanceOf(RuntimeException.class);
    }
}

