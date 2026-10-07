package com.kraft.comment.web;

import com.kraft.shared.exception.BusinessValidationException;
import com.kraft.comment.dto.CommentDeleteResultDto;
import com.kraft.comment.dto.CommentPageDto;
import com.kraft.comment.dto.CommentUpdateRequestDto;
import com.kraft.comment.dto.CommentViewDto;
import com.kraft.comment.service.CommentService;
import com.kraft.config.security.SecurityConfig;
import com.kraft.shared.web.WriteRateLimiters;
import org.junit.jupiter.api.BeforeEach;
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

import java.time.OffsetDateTime;
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

    @MockitoBean
    private WriteRateLimiters rateLimiters;

    /** A-SEC-06 제한기는 이 슬라이스의 관심사가 아니다 — 기본으로 항상 통과시킨다. */
    @BeforeEach
    void allowAllRateLimits() {
        given(rateLimiters.tryAcquireComment(any())).willReturn(true);
    }

    @Test
    @DisplayName("F13: GET .../comments/page 는 인증 없이도 afterId 커서를 그대로 서비스에 전달한다")
    void pageComments_isAccessibleWithoutAuthenticationAndPassesAfterIdCursor() throws Exception {
        given(commentService.findNextPageForView(eq(1L), eq(20L), any()))
                .willReturn(new CommentPageDto(List.of(), 30L, true));

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
    @DisplayName("POST .../comments 는 인증+CSRF+유효한 본문이면 200과 확정된 댓글(id·version 포함)을 반환한다")
    void saveComment_whenAuthenticatedAndValid_returns200AndId() throws Exception {
        given(commentService.save(eq(1L), any(), any())).willReturn(
                new CommentViewDto(10L, 1L, null, "댓글 내용", "tester", OffsetDateTime.now(), true, List.of(), 0L, false, 0L, false));

        mockMvc.perform(post("/api/v1/posts/1/comments")
                        .with(user("tester@example.com"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"댓글 내용\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    @DisplayName("POST .../comments 는 속도 제한에 걸리면 429이고 서비스는 호출되지 않는다")
    void saveComment_whenRateLimited_returns429AndDoesNotCallService() throws Exception {
        given(rateLimiters.tryAcquireComment(any())).willReturn(false);

        mockMvc.perform(post("/api/v1/posts/1/comments")
                        .with(user("tester@example.com"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"댓글 내용\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("COMMENT_RATE_LIMITED"));

        verify(commentService, never()).save(any(), any(), any());
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
                .andExpect(jsonPath("$.detail").value("내용은 필수입니다."));

        verify(commentService, never()).save(any(), any(), any());
    }

    /** 2단계 댓글: parentId가 body에 실려 서비스로 그대로 전달되는지 본다(JSON 계약). */
    @Test
    @DisplayName("POST .../comments 는 parentId가 있으면 답글로 저장하고 그대로 서비스에 전달한다")
    void saveComment_withParentId_savesAsReply() throws Exception {
        given(commentService.save(eq(1L), any(), any())).willReturn(
                new CommentViewDto(20L, 1L, 10L, "답글 내용", "tester", OffsetDateTime.now(), true, List.of(), 0L, false, 0L, false));

        mockMvc.perform(post("/api/v1/posts/1/comments")
                        .with(user("tester@example.com"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"답글 내용\",\"parentId\":10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(20))
                .andExpect(jsonPath("$.parentId").value(10));
    }

    /**
     * 2단계 댓글: 답글에 다시 답글을 달면(3단계) 서비스가 {@code IllegalArgumentException}을
     * 던지고, {@code ApiExceptionHandler}가 이를 그대로 400 ProblemDetail로 바꾼다 — 새
     * 예외 핸들러가 필요 없다는 것까지 함께 확인한다.
     */
    @Test
    @DisplayName("POST .../comments 는 답글에 답글을 달려는 요청을 400으로 거절한다")
    void saveComment_replyToAReply_returns400BadRequest() throws Exception {
        given(commentService.save(eq(1L), any(), any()))
                .willThrow(new BusinessValidationException("답글에는 답글을 달 수 없습니다."));

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
        given(commentService.update(eq(1L), any(CommentUpdateRequestDto.class), any(), any(Authentication.class)))
                .willThrow(new AccessDeniedException("작성자 본인 또는 관리자만 수정·삭제할 수 있습니다. id=1"));

        mockMvc.perform(put("/api/v1/comments/1")
                        .with(user("intruder@example.com"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"해킹\",\"version\":0}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("F11: PUT /api/v1/comments/{id} 에 기준 버전(If-Match도 본문 version도)이 없으면 428이고 서비스는 호출되지 않는다")
    void updateComment_withoutAnyVersion_returns428() throws Exception {
        mockMvc.perform(put("/api/v1/comments/1")
                        .with(user("tester@example.com"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"수정\"}"))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.code").value("VERSION_REQUIRED"));

        verify(commentService, never()).update(any(), any(), any(), any());
    }

    @Test
    @DisplayName("DELETE /api/v1/comments/{id} 는 인증된 사용자가 요청하면 id와 소프트 삭제 여부를 반환한다")
    void deleteComment_whenAuthenticated_returns200AndResult() throws Exception {
        given(commentService.delete(eq(1L), any())).willReturn(new CommentDeleteResultDto(1L, false));

        mockMvc.perform(delete("/api/v1/comments/1")
                        .with(user("tester@example.com"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.softDeleted").value(false));

        verify(commentService).delete(eq(1L), any(Authentication.class));
    }

    @Test
    @DisplayName("PUT /api/v1/comments/{id} 는 If-Match를 기준 버전으로 쓰고 응답에 저장 뒤 새 버전을 ETag로 싣는다")
    void updateComment_withIfMatch_usesHeaderVersionAndReturnsNewEtag() throws Exception {
        given(commentService.update(eq(1L), any(CommentUpdateRequestDto.class), any(), any(Authentication.class)))
                .willReturn(new CommentViewDto(1L, 1L, null, "수정", "tester", OffsetDateTime.now(), true,
                        List.of(), 0L, false, 5L, false));

        mockMvc.perform(put("/api/v1/comments/1")
                        .with(user("tester@example.com")).with(csrf())
                        .header("If-Match", "\"4\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"수정\"}"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("ETag", "\"5\""))
                .andExpect(jsonPath("$.version").value(5));

        verify(commentService).update(eq(1L), any(CommentUpdateRequestDto.class), eq(4L), any(Authentication.class));
    }

    @Test
    @DisplayName("PUT /api/v1/comments/{id} 는 기준 버전이 지금 버전과 다르면 412와 EDIT_CONFLICT 코드를 반환한다")
    void updateComment_whenPreconditionFails_returns412() throws Exception {
        given(commentService.update(eq(1L), any(CommentUpdateRequestDto.class), any(), any(Authentication.class)))
                .willThrow(new com.kraft.shared.domain.PreconditionFailedException(
                        com.kraft.comment.domain.Comment.class, 1L));

        mockMvc.perform(put("/api/v1/comments/1")
                        .with(user("tester@example.com")).with(csrf())
                        .header("If-Match", "\"1\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"수정\"}"))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value("EDIT_CONFLICT"))
                .andExpect(jsonPath("$.detail").value("다른 곳에서 이미 수정된 댓글입니다. 새로고침 후 다시 시도해 주세요."));
    }
}
