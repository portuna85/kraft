package com.kraft.post.web;

import com.kraft.config.security.SecurityConfig;
import com.kraft.post.domain.PostNotFoundException;
import com.kraft.post.service.PostModerationService;
import com.kraft.user.domain.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 관리자용 게시글 API의 경계 검증. 화면에서 버튼을 감추는 것만으로는 아무것도 막지 못한다. */
@WebMvcTest(AdminPostApiController.class)
@Import(SecurityConfig.class)
class AdminPostApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PostModerationService postModerationService;

    /** SecurityConfig가 UserDetailsService를 필요로 한다(폼 로그인 구성). */
    @MockitoBean
    private UserRepository userRepository;

    @Test
    @DisplayName("복구는 관리자만 할 수 있다 — 일반 회원은 403이고 서비스는 호출되지 않는다")
    void restore_whenNotAdmin_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/admin/posts/5/restore")
                        .with(user("tester@example.com").roles("USER"))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(postModerationService);
    }

    @Test
    @DisplayName("관리자가 복구하면 204이고 서비스에 글 id가 전달된다")
    void restore_whenAdmin_returns204() throws Exception {
        mockMvc.perform(post("/api/v1/admin/posts/5/restore")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(postModerationService).restore(5L);
    }

    @Test
    @DisplayName("삭제된 글이 아니면(없거나 이미 복구됨) 404다")
    void restore_whenNotDeleted_returns404() throws Exception {
        willThrow(new PostNotFoundException(5L)).given(postModerationService).restore(5L);

        mockMvc.perform(post("/api/v1/admin/posts/5/restore")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("숨기기는 관리자만 할 수 있다 — 일반 회원은 403이고 서비스는 호출되지 않는다")
    void blind_whenNotAdmin_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/admin/posts/5/blind")
                        .with(user("tester@example.com").roles("USER"))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(postModerationService);
    }

    @Test
    @DisplayName("관리자가 글을 숨기면 204이고, 없는 글이면 404다")
    void blind_whenAdmin_returns204_orNotFound() throws Exception {
        mockMvc.perform(post("/api/v1/admin/posts/5/blind")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isNoContent());
        verify(postModerationService).blindPost(5L);

        willThrow(new PostNotFoundException(6L)).given(postModerationService).blindPost(6L);
        mockMvc.perform(post("/api/v1/admin/posts/6/blind")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("숨김 해제는 관리자만 할 수 있다 — 일반 회원은 403이고 서비스는 호출되지 않는다")
    void unblind_whenNotAdmin_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/admin/posts/5/unblind")
                        .with(user("tester@example.com").roles("USER"))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(postModerationService);
    }

    @Test
    @DisplayName("관리자가 숨김을 풀면 204이고, 숨겨진 글이 아니면 404다")
    void unblind_whenAdmin_returns204_orNotFound() throws Exception {
        mockMvc.perform(post("/api/v1/admin/posts/5/unblind")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isNoContent());
        verify(postModerationService).unblindPost(5L);

        willThrow(new PostNotFoundException(6L)).given(postModerationService).unblindPost(6L);
        mockMvc.perform(post("/api/v1/admin/posts/6/unblind")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    private static final String PIN_BODY = "{\"pinnedUntil\":\"" + java.time.LocalDateTime.now().plusDays(3).withNano(0) + "\"}";

    @Test
    @DisplayName("고정은 관리자만 할 수 있다 — 일반 회원은 403이고 서비스는 호출되지 않는다")
    void pin_whenNotAdmin_returns403() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/admin/posts/5/pin")
                        .with(user("tester@example.com").roles("USER"))
                        .with(csrf())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(PIN_BODY))
                .andExpect(status().isForbidden());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/admin/posts/5/pin")
                        .with(user("tester@example.com").roles("USER"))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(postModerationService);
    }

    @Test
    @DisplayName("관리자가 고정하면 204이고 글 id와 기한이 서비스에 전달된다")
    void pin_whenAdmin_returns204() throws Exception {
        java.time.LocalDateTime until = java.time.LocalDateTime.now().plusDays(3).withNano(0);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/admin/posts/5/pin")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"pinnedUntil\":\"" + until + "\"}"))
                .andExpect(status().isNoContent());

        verify(postModerationService).pin(5L, until);
    }

    @Test
    @DisplayName("과거 기한이거나 기한이 없으면 400이고 서비스는 호출되지 않는다")
    void pin_withPastOrMissingDeadline_returns400() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/admin/posts/5/pin")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"pinnedUntil\":\"2020-01-01T00:00:00\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/admin/posts/5/pin")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(postModerationService);
    }

    @Test
    @DisplayName("서비스가 거절한 고정(개수 초과·기한 상한)은 400 ProblemDetail로 전달된다")
    void pin_whenServiceRejects_returns400WithDetail() throws Exception {
        willThrow(new com.kraft.shared.exception.BusinessValidationException("고정은 최대 5개까지입니다."))
                .given(postModerationService).pin(org.mockito.ArgumentMatchers.eq(5L), org.mockito.ArgumentMatchers.any());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/admin/posts/5/pin")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(PIN_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.detail").value("고정은 최대 5개까지입니다."));
    }

    @Test
    @DisplayName("관리자가 고정을 풀면 204다")
    void unpin_whenAdmin_returns204() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/admin/posts/5/pin")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(postModerationService).unpin(5L);
    }
}
