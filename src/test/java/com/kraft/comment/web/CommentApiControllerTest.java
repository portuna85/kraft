package com.kraft.comment.web;

import com.kraft.comment.dto.CommentPageDto;
import com.kraft.comment.dto.CommentResponseDto;
import com.kraft.comment.dto.CommentUpdateRequestDto;
import com.kraft.comment.service.CommentService;
import com.kraft.config.security.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link CommentApiController} 웹 계층 테스트. {@link CommentService}는 {@code @MockitoBean}으로
 * 대체하고, 실제 {@link SecurityConfig}를 {@code @Import}해 {@code PostApiControllerTest}와 동일한
 * 방식으로 CSRF/인증/인가 규칙, {@code ApiExceptionHandler}의 응답 형식을 검증한다.
 */
@WebMvcTest(CommentApiController.class)
@Import(SecurityConfig.class)
class CommentApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CommentService commentService;

    @Test
    @DisplayName("GET /api/v1/posts/{postId}/comments 는 인증 없이도 호출할 수 있다")
    void listComments_isAccessibleWithoutAuthentication() throws Exception {
        given(commentService.findByPostId(1L))
                .willReturn(List.of(new CommentResponseDto(1L, 1L, null, "댓글", "tester", LocalDateTime.now())));

        mockMvc.perform(get("/api/v1/posts/1/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].content").value("댓글"));
    }

    @Test
    @DisplayName("F13: GET .../comments/page 는 인증 없이도 afterId 커서를 그대로 서비스에 전달한다")
    void pageComments_isAccessibleWithoutAuthenticationAndPassesAfterIdCursor() throws Exception {
        given(commentService.findNextPageForView(eq(1L), eq(20L), any()))
                .willReturn(new CommentPageDto(List.of(), 30, true));

        mockMvc.perform(get("/api/v1/posts/1/comments/page").param("afterId", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(30))
                .andExpect(jsonPath("$.hasMore").value(true));
    }

    @Test
    @DisplayName("POST .../comments 는 CSRF 토큰이 없으면 403")
    void saveComment_withoutCsrfToken_returns403Forbidden() throws Exception {
        mockMvc.perform(post("/api/v1/posts/1/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"댓글\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST .../comments 는 CSRF 토큰이 있어도 미인증이면 로그인 페이지로 리다이렉트된다")
    void saveComment_whenUnauthenticated_redirectsToLoginPage() throws Exception {
        mockMvc.perform(post("/api/v1/posts/1/comments")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"댓글\"}"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("POST .../comments 는 인증+CSRF+유효한 본문이면 200과 ID를 반환한다")
    void saveComment_whenAuthenticatedAndValid_returns200AndId() throws Exception {
        given(commentService.save(eq(1L), eq("tester@example.com"), any())).willReturn(10L);

        mockMvc.perform(post("/api/v1/posts/1/comments")
                        .with(user("tester@example.com"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"댓글 내용\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string("10"));
    }

    @Test
    @DisplayName("POST .../comments 는 내용이 비어 있으면 400이고 서비스는 호출되지 않는다")
    void saveComment_whenContentIsEmpty_returns400BadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/posts/1/comments")
                        .with(user("tester@example.com"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("content: 내용은 필수입니다."));

        verify(commentService, never()).save(any(), any(), any());
    }

    /** 2단계 댓글: parentId가 body에 실려 서비스로 그대로 전달되는지 본다(JSON 계약). */
    @Test
    @DisplayName("POST .../comments 는 parentId가 있으면 답글로 저장하고 그대로 서비스에 전달한다")
    void saveComment_withParentId_savesAsReply() throws Exception {
        given(commentService.save(eq(1L), eq("tester@example.com"), any())).willReturn(20L);

        mockMvc.perform(post("/api/v1/posts/1/comments")
                        .with(user("tester@example.com"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"답글 내용\",\"parentId\":10}"))
                .andExpect(status().isOk())
                .andExpect(content().string("20"));
    }

    /**
     * 2단계 댓글: 답글에 다시 답글을 달면(3단계) 서비스가 {@code IllegalArgumentException}을
     * 던지고, {@code ApiExceptionHandler}가 이를 그대로 400 ProblemDetail로 바꾼다 — 새
     * 예외 핸들러가 필요 없다는 것까지 함께 확인한다.
     */
    @Test
    @DisplayName("POST .../comments 는 답글에 답글을 달려는 요청을 400으로 거절한다")
    void saveComment_replyToAReply_returns400BadRequest() throws Exception {
        given(commentService.save(eq(1L), eq("tester@example.com"), any()))
                .willThrow(new IllegalArgumentException("답글에는 답글을 달 수 없습니다."));

        mockMvc.perform(post("/api/v1/posts/1/comments")
                        .with(user("tester@example.com"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"답글의 답글\",\"parentId\":20}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("답글에는 답글을 달 수 없습니다."));
    }

    @Test
    @DisplayName("PUT /api/v1/comments/{id} 는 작성자가 아니면 403 ProblemDetail")
    void updateComment_whenNotAuthor_returns403Forbidden() throws Exception {
        given(commentService.update(eq(1L), any(CommentUpdateRequestDto.class), any(Authentication.class)))
                .willThrow(new AccessDeniedException("작성자 본인 또는 관리자만 수정·삭제할 수 있습니다. id=1"));

        mockMvc.perform(put("/api/v1/comments/1")
                        .with(user("intruder@example.com"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"해킹\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("DELETE /api/v1/comments/{id} 는 인증된 사용자가 요청하면 ID를 반환한다")
    void deleteComment_whenAuthenticated_returns200AndId() throws Exception {
        mockMvc.perform(delete("/api/v1/comments/1")
                        .with(user("tester@example.com"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string("1"));

        verify(commentService).delete(eq(1L), any(Authentication.class));
    }
}
