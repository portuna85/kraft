package com.kraft.web;

import com.kraft.config.security.SecurityConfig;
import com.kraft.domain.post.Category;
import com.kraft.service.comment.CommentService;
import com.kraft.service.post.PostService;
import com.kraft.service.user.EmailVerificationService;
import com.kraft.web.dto.post.PostViewDto;
import com.kraft.web.dto.post.PostsPageResponseDto;
import com.kraft.web.exception.PostNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * {@link IndexController} 화면 계층 테스트. {@code PostService}/{@code CommentService}는 모킹하고,
 * 실제 {@link SecurityConfig}를 임포트해 CSRF 메타 태그가 필요한 헤더 fragment까지 렌더링되는
 * 실제 요청 흐름을 재현한다.
 * <p>
 * {@code page=-1} 테스트는 과거 실제로 500을 유발했던 버그(08장 8.6절)의 회귀 방지 테스트다:
 * {@code @RequestParam int page}로 직접 받던 시절에는 음수 페이지가 {@code PageRequest.of(-1, ...)}
 * 에서 {@code IllegalArgumentException}을 던졌으나, {@code Pageable}을 {@code @PageableDefault}로
 * 직접 받도록 고친 뒤에는 Spring Data가 안전하게 0으로 보정한다.
 */
@WebMvcTest(IndexController.class)
@Import(SecurityConfig.class)
class IndexControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PostService postService;

    @MockitoBean
    private CommentService commentService;

    @MockitoBean
    private EmailVerificationService emailVerificationService;

    @Test
    @DisplayName("GET / 는 목록을 모델에 담아 index 뷰를 렌더링한다")
    void 목록화면은_정상_렌더링된다() throws Exception {
        given(postService.findAllDesc(any(Pageable.class), any(), any()))
                .willReturn(new PostsPageResponseDto(List.of(), 0, 10, 0, 0, true, true));
        given(postService.findPopular(5)).willReturn(List.of());

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attributeExists("posts", "postsPage", "pageWindow"));
    }

    @Test
    @DisplayName("[회귀 방지] GET /?page=-1 은 500이 아니라 정상 렌더링된다")
    void 음수_페이지_요청은_500이_아니다() throws Exception {
        given(postService.findAllDesc(any(Pageable.class), any(), any()))
                .willReturn(new PostsPageResponseDto(List.of(), 0, 10, 0, 0, true, true));
        given(postService.findPopular(5)).willReturn(List.of());

        mockMvc.perform(get("/").param("page", "-1"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"));
    }

    @Test
    @DisplayName("[회귀 방지] GET /?page=999 (범위 초과) 도 500이 아니라 정상 렌더링된다")
    void 범위초과_페이지_요청도_500이_아니다() throws Exception {
        given(postService.findAllDesc(any(Pageable.class), any(), any()))
                .willReturn(new PostsPageResponseDto(List.of(), 999, 10, 0, 0, true, true));
        given(postService.findPopular(5)).willReturn(List.of());

        mockMvc.perform(get("/").param("page", "999"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"));
    }

    @Test
    @DisplayName("GET /?q=키워드&category=NOTICE 는 검색어·분류를 서비스에 그대로 전달한다")
    void 검색어와_분류를_서비스에_전달한다() throws Exception {
        given(postService.findAllDesc(any(Pageable.class), eq("키워드"), eq(Category.NOTICE)))
                .willReturn(new PostsPageResponseDto(List.of(), 0, 10, 0, 0, true, true));
        given(postService.findPopular(5)).willReturn(List.of());

        mockMvc.perform(get("/").param("q", "키워드").param("category", "NOTICE"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attribute("q", "키워드"))
                .andExpect(model().attribute("category", Category.NOTICE));
    }

    @Test
    @DisplayName("GET /posts/save 는 인증 없이도 등록 화면을 보여준다")
    void 등록화면은_인증없이_접근가능하다() throws Exception {
        mockMvc.perform(get("/posts/save"))
                .andExpect(status().isOk())
                .andExpect(view().name("post/post-save"));
    }

    @Test
    @DisplayName("GET /posts/update/{id} 는 조회한 게시글과 댓글 목록을 모델에 담아 렌더링한다")
    void 수정화면은_게시글과_댓글을_모델에_담는다() throws Exception {
        given(postService.findByIdForView(eq(1L), nullable(Authentication.class)))
                .willReturn(new PostViewDto(1L, "제목", "내용", null, "작성자", false, Category.FREE, 0L, 0L, false));
        given(commentService.findByPostIdForView(eq(1L), nullable(Authentication.class)))
                .willReturn(List.of());

        mockMvc.perform(get("/posts/update/1"))
                .andExpect(status().isOk())
                .andExpect(view().name("post/post-update"))
                .andExpect(model().attributeExists("post", "comments"));
    }

    @Test
    @DisplayName("[의도된 동작] 존재하지 않는 게시글의 읽기 화면은 404 안내 화면을 렌더링한다 — " +
            "PostNotFoundException은 ApiExceptionHandler(web.api 패키지 전용)의 범위 밖이지만, " +
            "ViewExceptionHandler가 화면 컨트롤러 전용으로 404 + error/not-found 뷰로 변환한다.")
    void 존재하지_않는_게시글_읽기화면은_404를_반환한다() throws Exception {
        given(postService.findByIdForView(eq(999L), nullable(Authentication.class)))
                .willThrow(new PostNotFoundException(999L));

        mockMvc.perform(get("/posts/update/999"))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/not-found"));
    }

    @Test
    @DisplayName("GET /signup 은 인증 없이도 회원가입 화면을 보여준다")
    void 회원가입화면은_인증없이_접근가능하다() throws Exception {
        mockMvc.perform(get("/signup"))
                .andExpect(status().isOk())
                .andExpect(view().name("user/signup"));
    }

    @Test
    @DisplayName("GET /users/me/password 는 비밀번호 변경 화면을 보여준다")
    void 비밀번호변경화면이_렌더링된다() throws Exception {
        mockMvc.perform(get("/users/me/password"))
                .andExpect(status().isOk())
                .andExpect(view().name("user/change-password"));
    }

    @Test
    @DisplayName("GET /users/verify 는 토큰이 유효하면 success=true로 렌더링한다")
    void 이메일인증은_토큰이_유효하면_성공화면을_렌더링한다() throws Exception {
        mockMvc.perform(get("/users/verify").param("token", "valid-token"))
                .andExpect(status().isOk())
                .andExpect(view().name("user/verify-result"))
                .andExpect(model().attribute("success", true));
    }

    @Test
    @DisplayName("GET /users/verify 는 토큰이 유효하지 않으면 success=false와 메시지를 담아 렌더링한다")
    void 이메일인증은_토큰이_유효하지_않으면_실패화면을_렌더링한다() throws Exception {
        willThrow(new IllegalArgumentException("유효하지 않은 인증 링크입니다."))
                .given(emailVerificationService).verify("invalid-token");

        mockMvc.perform(get("/users/verify").param("token", "invalid-token"))
                .andExpect(status().isOk())
                .andExpect(view().name("user/verify-result"))
                .andExpect(model().attribute("success", false))
                .andExpect(model().attribute("message", "유효하지 않은 인증 링크입니다."));
    }
}
