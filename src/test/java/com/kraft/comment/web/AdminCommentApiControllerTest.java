package com.kraft.comment.web;

import com.kraft.comment.service.CommentService;
import com.kraft.config.security.SecurityConfig;
import com.kraft.shared.exception.NotFoundException;
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

/** 관리자용 댓글 API의 경계 검증. 화면에서 버튼을 감추는 것만으로는 아무것도 막지 못한다. */
@WebMvcTest(AdminCommentApiController.class)
@Import(SecurityConfig.class)
class AdminCommentApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CommentService commentService;

    /** SecurityConfig가 UserDetailsService를 필요로 한다(폼 로그인 구성). */
    @MockitoBean
    private UserRepository userRepository;

    @Test
    @DisplayName("숨기기는 관리자만 할 수 있다 — 일반 회원은 403이고 서비스는 호출되지 않는다")
    void blind_whenNotAdmin_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/admin/comments/5/blind")
                        .with(user("tester@example.com").roles("USER"))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(commentService);
    }

    @Test
    @DisplayName("관리자가 댓글을 숨기면 204다")
    void blind_whenAdmin_returns204() throws Exception {
        mockMvc.perform(post("/api/v1/admin/comments/5/blind")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(commentService).blind(5L);
    }

    @Test
    @DisplayName("없는 댓글이면 404다")
    void blind_whenMissing_returns404() throws Exception {
        willThrow(new NotFoundException("해당 댓글이 없습니다. id=5")).given(commentService).blind(5L);

        mockMvc.perform(post("/api/v1/admin/comments/5/blind")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("숨김 해제는 관리자만 할 수 있다 — 일반 회원은 403이고 서비스는 호출되지 않는다")
    void unblind_whenNotAdmin_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/admin/comments/5/unblind")
                        .with(user("tester@example.com").roles("USER"))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(commentService);
    }

    @Test
    @DisplayName("관리자가 숨김을 풀면 204다")
    void unblind_whenAdmin_returns204() throws Exception {
        mockMvc.perform(post("/api/v1/admin/comments/5/unblind")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(commentService).unblind(5L);
    }

    @Test
    @DisplayName("숨겨진 댓글이 아니면 404다")
    void unblind_whenNotBlinded_returns404() throws Exception {
        willThrow(new NotFoundException("숨겨진 댓글이 아닙니다. id=5")).given(commentService).unblind(5L);

        mockMvc.perform(post("/api/v1/admin/comments/5/unblind")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }
}
