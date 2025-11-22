package com.kraft.web.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kraft.config.auth.dto.SessionUser;
import com.kraft.domain.user.User;
import com.kraft.service.AuthService;
import com.kraft.service.UserService;
import com.kraft.web.dto.user.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class UserApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private AuthService authService;

    @Test
    @DisplayName("회원가입에 성공한다")
    void signup_success() throws Exception {
        // given
        SignupRequestDto requestDto = SignupRequestDto.builder()
                .name("testuser")
                .password("password123")
                .email("test@example.com")
                .build();

        given(userService.register(any(SignupRequestDto.class))).willReturn(1L);

        // expect
        mockMvc.perform(post("/api/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(1L))
                .andExpect(jsonPath("$.name").value("testuser"))
                .andExpect(jsonPath("$.email").value("test@example.com"));
    }

    @Test
    @DisplayName("로그인에 성공한다")
    void login_success() throws Exception {
        // given
        LoginRequestDto requestDto = LoginRequestDto.builder()
                .name("testuser")
                .password("password123")
                .build();

        User user = User.of("testuser", "encoded", "test@example.com");
        SessionUser sessionUser = new SessionUser(user);

        given(authService.login(any(LoginRequestDto.class))).willReturn(sessionUser);

        // expect
        mockMvc.perform(post("/api/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.name").value("testuser"))
                .andExpect(jsonPath("$.user.email").value("test@example.com"));
    }

    @Test
    @DisplayName("로그아웃에 성공한다")
    void logout_success() throws Exception {
        // given
        MockHttpSession session = new MockHttpSession();

        // expect
        mockMvc.perform(post("/api/users/logout")
                        .session(session))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("프로필 조회에 성공한다")
    void getProfile_success() throws Exception {
        // given
        User user = User.of("testuser", "encoded", "test@example.com");
        UserProfileResponseDto responseDto = UserProfileResponseDto.from(user);
        given(userService.getProfile(any())).willReturn(responseDto);

        SessionUser sessionUser = new SessionUser(user);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("user", sessionUser);


        // expect
        mockMvc.perform(get("/api/users/me")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("testuser"))
                .andExpect(jsonPath("$.email").value("test@example.com"));
    }

    @Test
    @DisplayName("이메일 수정에 성공한다")
    void updateEmail_success() throws Exception {
        // given
        User user = User.of("testuser", "encoded", "old@example.com");
        SessionUser sessionUser = new SessionUser(user);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("user", sessionUser);

        UserProfileUpdateRequestDto requestDto = UserProfileUpdateRequestDto.builder()
                .email("new@example.com")
                .build();

        User updatedUser = User.of("testuser", "encoded", "new@example.com");
        UserProfileResponseDto responseDto = UserProfileResponseDto.from(updatedUser);
        given(userService.updateEmail(any(), anyString())).willReturn(responseDto);

        // expect
        mockMvc.perform(patch("/api/users/me/email")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("new@example.com"));
    }

    @Test
    @DisplayName("비밀번호 변경에 성공한다")
    void changePassword_success() throws Exception {
        // given
        User user = User.of("testuser", "encoded", "test@example.com");
        SessionUser sessionUser = new SessionUser(user);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("user", sessionUser);

        UserPasswordChangeRequestDto requestDto = UserPasswordChangeRequestDto.builder()
                .currentPassword("oldPassword")
                .newPassword("newPassword123")
                .build();

        // expect
        mockMvc.perform(patch("/api/users/me/password")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("회원 탈퇴에 성공한다")
    void deleteUser_success() throws Exception {
        // given
        User user = User.of("testuser", "encoded", "test@example.com");
        SessionUser sessionUser = new SessionUser(user);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("user", sessionUser);

        // expect
        mockMvc.perform(delete("/api/users/me")
                        .session(session))
                .andExpect(status().isNoContent());
    }
}

