package com.kraft.post.web;

import com.kraft.config.security.SecurityConfig;
import com.kraft.post.domain.Category;
import com.kraft.post.domain.Post;
import com.kraft.post.domain.PostNotFoundException;
import com.kraft.post.dto.PostLikeResponseDto;
import com.kraft.post.dto.PostSaveRequestDto;
import com.kraft.post.dto.PostsPageResponseDto;
import com.kraft.post.dto.PostUpdateRequestDto;
import com.kraft.post.service.PostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link PostApiController} 웹 계층 테스트. {@link PostService}는 {@code @MockitoBean}으로
 * 대체하고, 실제 {@link SecurityConfig}를 {@code @Import}해 인증/CSRF/인가 규칙이 문서에
 * 기록된 그대로 동작하는지 확인한다. {@link com.kraft.shared.web.ApiExceptionHandler}는
 * {@code @RestController} 대상 {@code @RestControllerAdvice}라 별도 {@code @Import}
 * 없이도 이 슬라이스에 함께 적용된다.
 */
@WebMvcTest(PostApiController.class)
@Import(SecurityConfig.class)
class PostApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PostService postService;

    @Test
    @DisplayName("GET /api/v1/posts 는 인증 없이도 호출할 수 있다")
    void listPosts_isAccessibleWithoutAuthentication() throws Exception {
        given(postService.findAllDesc(any(Pageable.class), any(), any()))
                .willReturn(new PostsPageResponseDto(List.of(), 0, 10, 0, 0, true, true));

        mockMvc.perform(get("/api/v1/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.first").value(true));
    }

    @Test
    @DisplayName("GET /api/v1/posts?q=...&category=... 는 검색어·분류를 서비스에 그대로 전달한다")
    void listPosts_passesSearchKeywordAndCategoryToService() throws Exception {
        given(postService.findAllDesc(any(Pageable.class), eq("공지"), eq(Category.NOTICE)))
                .willReturn(new PostsPageResponseDto(List.of(), 0, 10, 0, 0, true, true));

        mockMvc.perform(get("/api/v1/posts").param("q", "공지").param("category", "NOTICE"))
                .andExpect(status().isOk());

        verify(postService).findAllDesc(any(Pageable.class), eq("공지"), eq(Category.NOTICE));
    }

    @Test
    @DisplayName("PUT /api/v1/posts/{id}/like 는 CSRF 토큰이 있어도 미인증이면 로그인 페이지로 리다이렉트된다")
    void toggleLike_whenUnauthenticated_redirectsToLoginPage() throws Exception {
        mockMvc.perform(put("/api/v1/posts/1/like").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        verify(postService, never()).setLike(any(), org.mockito.ArgumentMatchers.anyBoolean(), any());
    }

    @Test
    @DisplayName("PUT /api/v1/posts/{id}/like 는 인증+CSRF면 토글 결과를 반환한다")
    void toggleLike_whenAuthenticatedWithCsrf_returnsToggleResult() throws Exception {
        given(postService.setLike(eq(1L), eq(true), any(Authentication.class)))
                .willReturn(new PostLikeResponseDto(true, 3L));

        mockMvc.perform(put("/api/v1/posts/1/like")
                        .with(user("tester@example.com"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"liked\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liked").value(true))
                .andExpect(jsonPath("$.likeCount").value(3));
    }

    @Test
    @DisplayName("PUT /api/v1/posts/{id}/like 는 없는 글이면 404 ProblemDetail을 반환한다")
    void setLike_whenPostNotFound_returns404NotFound() throws Exception {
        given(postService.setLike(eq(999L), eq(true), any(Authentication.class)))
                .willThrow(new PostNotFoundException(999L));

        mockMvc.perform(put("/api/v1/posts/999/like")
                        .with(user("tester@example.com"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"liked\":true}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/v1/posts/{id} 는 없는 글이면 404 ProblemDetail을 반환한다")
    void getPost_whenPostNotFound_returns404NotFound() throws Exception {
        given(postService.findById(999L))
                .willThrow(new PostNotFoundException(999L));

        mockMvc.perform(get("/api/v1/posts/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("해당 게시글이 없습니다. id=999"))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("[회귀 방지] 옛 경로 GET /api/v1/posts/list 는 id 타입 변환 실패로 500이 아니라 400을 반환한다")
    void getPost_withNonNumericId_returns400BadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/posts/list"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("POST /api/v1/posts 는 CSRF 토큰이 없으면 403")
    void savePost_withoutCsrfToken_returns403Forbidden() throws Exception {
        mockMvc.perform(post("/api/v1/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"t\",\"content\":\"c\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/v1/posts 는 CSRF 토큰이 있어도 미인증이면 로그인 페이지로 리다이렉트된다")
    void savePost_whenUnauthenticated_redirectsToLoginPage() throws Exception {
        mockMvc.perform(post("/api/v1/posts")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"t\",\"content\":\"c\"}"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("POST /api/v1/posts 는 인증+CSRF+유효한 본문이면 200과 ID를 반환한다")
    void savePost_whenAuthenticatedAndValid_returns200AndId() throws Exception {
        given(postService.save(any(Authentication.class), any(PostSaveRequestDto.class))).willReturn(1L);

        mockMvc.perform(post("/api/v1/posts")
                        .with(user("tester@example.com"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"제목\",\"content\":\"내용\",\"picture\":null}"))
                .andExpect(status().isOk())
                .andExpect(content().string("1"));
    }

    @Test
    @DisplayName("POST /api/v1/posts 는 제목이 비어 있으면 400이고 서비스는 호출되지 않는다")
    void savePost_whenTitleIsEmpty_returns400BadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/posts")
                        .with(user("tester@example.com"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\",\"content\":\"내용\",\"picture\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("title: 제목은 필수입니다."));

        verify(postService, never()).save(any(), any());
    }

    @Test
    @DisplayName("POST /api/v1/posts 는 제목이 255자를 초과하면 400이고 서비스는 호출되지 않는다")
    void savePost_whenTitleExceedsMaxLength_returns400BadRequest() throws Exception {
        String tooLongTitle = "가".repeat(256);

        mockMvc.perform(post("/api/v1/posts")
                        .with(user("tester@example.com"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + tooLongTitle + "\",\"content\":\"내용\",\"picture\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("title: 제목은 255자 이하로 입력하세요."));

        verify(postService, never()).save(any(), any());
    }

    @Test
    @DisplayName("PUT /api/v1/posts/{id} 는 작성자가 아니면 403 ProblemDetail")
    void updatePost_whenNotAuthor_returns403Forbidden() throws Exception {
        given(postService.update(eq(1L), any(PostUpdateRequestDto.class), any(Authentication.class)))
                .willThrow(new AccessDeniedException("작성자 본인 또는 관리자만 수정·삭제할 수 있습니다. id=1"));

        mockMvc.perform(put("/api/v1/posts/1")
                        .with(user("intruder@example.com"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"해킹\",\"content\":\"해킹\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("PUT /api/v1/posts/{id} 는 picture를 포함한 요청 본문을 그대로 서비스에 전달한다")
    void updatePost_passesPictureAndRequestBodyToService() throws Exception {
        given(postService.update(eq(1L), any(PostUpdateRequestDto.class), any(Authentication.class))).willReturn(1L);

        mockMvc.perform(put("/api/v1/posts/1")
                        .with(user("tester@example.com"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"제목\",\"content\":\"내용\",\"picture\":\"/images/new.png\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string("1"));

        var captor = org.mockito.ArgumentCaptor.forClass(PostUpdateRequestDto.class);
        verify(postService).update(eq(1L), captor.capture(), any(Authentication.class));
        assertThat(captor.getValue().picture()).isEqualTo("/images/new.png");
    }

    @Test
    @DisplayName("PUT /api/v1/posts/{id} 는 제목이 255자를 초과하면 400이고 서비스는 호출되지 않는다")
    void updatePost_whenTitleExceedsMaxLength_returns400BadRequest() throws Exception {
        String tooLongTitle = "가".repeat(256);

        mockMvc.perform(put("/api/v1/posts/1")
                        .with(user("tester@example.com"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + tooLongTitle + "\",\"content\":\"내용\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("title: 제목은 255자 이하로 입력하세요."));

        verify(postService, never()).update(any(), any(), any());
    }

    @Test
    @DisplayName("DELETE /api/v1/posts/{id} 는 인증된 사용자가 요청하면 ID를 반환한다")
    void deletePost_whenAuthenticated_returns200AndId() throws Exception {
        mockMvc.perform(delete("/api/v1/posts/1")
                        .with(user("tester@example.com"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string("1"));

        verify(postService).delete(eq(1L), any(Authentication.class));
    }

    @Test
    @DisplayName("POST /api/v1/posts/images 는 CSRF 토큰이 없으면 403")
    void uploadImage_withoutCsrfToken_returns403Forbidden() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", "img".getBytes());

        mockMvc.perform(multipart("/api/v1/posts/images").file(file))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/v1/posts/images 는 CSRF 토큰이 있어도 미인증이면 로그인 페이지로 리다이렉트된다")
    void uploadImage_whenUnauthenticated_redirectsToLoginPage() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", "img".getBytes());

        mockMvc.perform(multipart("/api/v1/posts/images").file(file).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("POST /api/v1/posts/images 는 인증+CSRF+유효한 파일이면 200과 업로드된 URL을 반환한다")
    void uploadImage_whenAuthenticatedAndValid_returns200AndUrl() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", "img".getBytes());
        given(postService.uploadImage(any(), any(Authentication.class))).willReturn("/images/generated-uuid.png");

        mockMvc.perform(multipart("/api/v1/posts/images").file(file)
                        .with(user("tester@example.com"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("/images/generated-uuid.png"));
    }

    @Test
    @DisplayName("POST /api/v1/posts/images 는 허용되지 않는 파일이면 400 ProblemDetail을 반환한다")
    void uploadImage_withDisallowedExtension_returns400BadRequest() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "malware.exe", "application/octet-stream", "x".getBytes());
        given(postService.uploadImage(any(), any(Authentication.class)))
                .willThrow(new IllegalArgumentException("허용되지 않는 파일 형식입니다: exe"));

        mockMvc.perform(multipart("/api/v1/posts/images").file(file)
                        .with(user("tester@example.com"))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("허용되지 않는 파일 형식입니다: exe"));
    }

    @Test
    @DisplayName("POST /api/v1/posts/images 는 이메일 인증 전(GUEST)이면 403 ProblemDetail을 반환한다")
    void uploadImage_whenGuest_returns403Forbidden() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", "img".getBytes());
        given(postService.uploadImage(any(), any(Authentication.class)))
                .willThrow(new AccessDeniedException("이메일 인증을 완료해야 글을 작성할 수 있습니다. id=1"));

        mockMvc.perform(multipart("/api/v1/posts/images").file(file)
                        .with(user("guest@example.com"))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT /api/v1/posts/{id} 는 편집 충돌이면 409 ProblemDetail을 반환한다")
    void updatePost_whenEditConflict_returns409Conflict() throws Exception {
        given(postService.update(eq(1L), any(PostUpdateRequestDto.class), any(Authentication.class)))
                .willThrow(new ObjectOptimisticLockingFailureException(Post.class, 1L));

        mockMvc.perform(put("/api/v1/posts/1")
                        .with(user("tester@example.com"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"제목\",\"content\":\"내용\",\"version\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("다른 곳에서 이미 수정된 글입니다. 새로고침 후 다시 시도해 주세요."));
    }
}
