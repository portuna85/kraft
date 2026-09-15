package com.kraft.user.web;

import com.kraft.config.security.SecurityConfig;
import com.kraft.user.domain.UserRepository;
import com.kraft.user.dto.SuspendedUserDto;
import com.kraft.user.service.SuspensionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * 정지 회원 화면과 해제 API의 경계. 화면과 API를 <b>같은 규칙</b>으로 막는지가 핵심이다 —
 * 화면만 감추면 API는 그대로 열려 있다.
 */
@WebMvcTest({AdminUserPageController.class, AdminUserApiController.class})
@Import(SecurityConfig.class)
class AdminUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SuspensionService suspensionService;

    /** SecurityConfig가 UserDetailsService를 필요로 한다(폼 로그인 구성). */
    @MockitoBean
    private UserRepository userRepository;

    private static Page<SuspendedUserDto> pageOf(SuspendedUserDto... rows) {
        return new PageImpl<>(List.of(rows), PageRequest.of(0, 20), rows.length);
    }

    @Test
    @DisplayName("관리자가 아니면 정지 회원 화면에 들어갈 수 없다")
    void suspendedUsers_whenNotAdmin_returns403Forbidden() throws Exception {
        mockMvc.perform(get("/admin/users").with(user("tester@example.com").roles("USER")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(suspensionService);
    }

    @Test
    @DisplayName("관리자에게는 정지 중인 회원과 기한·사유를 보여준다")
    void suspendedUsers_whenAdmin_rendersList() throws Exception {
        given(suspensionService.findSuspended(any(Pageable.class))).willReturn(pageOf(
                new SuspendedUserDto(7L, "반복스패머", LocalDateTime.now().plusDays(3), "스팸·광고 신고 처리(7일)")));

        mockMvc.perform(get("/admin/users").with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/users"))
                .andExpect(content().string(containsString("반복스패머")))
                .andExpect(content().string(containsString("스팸·광고 신고 처리(7일)")));
    }

    @Test
    @DisplayName("정지 중인 회원이 없으면 빈 상태를 보여준다")
    void suspendedUsers_whenEmpty_rendersEmptyState() throws Exception {
        given(suspensionService.findSuspended(any(Pageable.class))).willReturn(pageOf());

        mockMvc.perform(get("/admin/users").with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("정지 중인 회원이 없습니다")));
    }

    @Test
    @DisplayName("정지 해제는 관리자만 할 수 있다 — 일반 회원은 403이고 서비스는 호출되지 않는다")
    void lift_whenNotAdmin_returns403Forbidden() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users/7/suspension/lift")
                        .with(user("tester@example.com").roles("USER"))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(suspensionService);
    }

    @Test
    @DisplayName("관리자는 정지를 기간 전에 풀 수 있다")
    void lift_whenAdmin_returns204NoContent() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users/7/suspension/lift")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(suspensionService).lift(7L);
    }

    @Test
    @DisplayName("정지 중이 아닌 계정을 풀면 400 ProblemDetail을 반환한다")
    void lift_whenNotSuspended_returns400BadRequest() throws Exception {
        willThrow(new IllegalArgumentException("정지 중인 계정이 아닙니다. id=7"))
                .given(suspensionService).lift(7L);

        mockMvc.perform(post("/api/v1/admin/users/7/suspension/lift")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("정지 중인 계정이 아닙니다. id=7"));
    }
}
