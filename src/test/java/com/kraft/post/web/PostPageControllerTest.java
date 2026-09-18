package com.kraft.post.web;

import com.kraft.comment.dto.CommentPageDto;
import com.kraft.comment.dto.CommentViewDto;
import com.kraft.comment.service.CommentService;
import com.kraft.config.security.SecurityConfig;
import com.kraft.post.domain.Category;
import com.kraft.post.domain.PostNotFoundException;
import com.kraft.post.dto.PostsPageResponseDto;
import com.kraft.post.dto.PostViewDto;
import com.kraft.post.service.PostService;
import com.kraft.user.service.UserService;
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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * {@link PostPageController} 화면 계층 테스트. {@code PostService}/{@code CommentService}는 모킹하고,
 * 실제 {@link SecurityConfig}를 임포트해 CSRF 메타 태그가 필요한 헤더 fragment까지 렌더링되는
 * 실제 요청 흐름을 재현한다.
 * <p>
 * {@code page=-1} 테스트는 과거 실제로 500을 유발했던 버그(08장 8.6절)의 회귀 방지 테스트다:
 * {@code @RequestParam int page}로 직접 받던 시절에는 음수 페이지가 {@code PageRequest.of(-1, ...)}
 * 에서 {@code IllegalArgumentException}을 던졌으나, {@code Pageable}을 {@code @PageableDefault}로
 * 직접 받도록 고친 뒤에는 Spring Data가 안전하게 0으로 보정한다.
 */
@WebMvcTest(PostPageController.class)
@Import(SecurityConfig.class)
class PostPageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PostService postService;

    @MockitoBean
    private CommentService commentService;

    /** 화면이 "글을 쓸 수 있는 사람인가"를 물어보는 곳. 기본 모킹은 빈 값(=쓸 수 있음)이다. */
    @MockitoBean
    private UserService userService;

    @Test
    @DisplayName("GET / 는 목록을 모델에 담아 index 뷰를 렌더링한다")
    void index_rendersIndexViewWithPostsModel() throws Exception {
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
    void index_withNegativePage_rendersSuccessfullyWithoutServerError() throws Exception {
        given(postService.findAllDesc(any(Pageable.class), any(), any()))
                .willReturn(new PostsPageResponseDto(List.of(), 0, 10, 0, 0, true, true));
        given(postService.findPopular(5)).willReturn(List.of());

        mockMvc.perform(get("/").param("page", "-1"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"));
    }

    @Test
    @DisplayName("[회귀 방지] GET /?page=999 (범위 초과, 글이 하나도 없음) 도 500이 아니라 정상 렌더링된다")
    void index_withOutOfRangePage_rendersSuccessfullyWithoutServerError() throws Exception {
        given(postService.findAllDesc(any(Pageable.class), any(), any()))
                .willReturn(new PostsPageResponseDto(List.of(), 999, 10, 0, 0, true, true));
        given(postService.findPopular(5)).willReturn(List.of());

        mockMvc.perform(get("/").param("page", "999"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"));
    }

    /**
     * F09: PageWindow는 표시용 페이지 번호를 [0, totalPages-1]로 보정하지만, 실제 조회는
     * 요청받은 원래 page 그대로 돈다 — 글이 있는데도 범위를 넘는 page를 요청하면(예: 처리 중
     * 다른 글이 지워져 페이지 수가 줄어든 경우) 빈 목록과, 그중 어느 것도 "현재"로 표시되지
     * 않는 페이지네이션이 동시에 보였다. 검색어·분류를 유지한 채 유효한 마지막 페이지로
     * 보내는지 확인한다.
     */
    @Test
    @DisplayName("F09: 글은 있지만 범위를 넘는 page를 요청하면 유효한 마지막 페이지로 보낸다")
    void index_withOutOfRangePageButPostsExist_redirectsToLastValidPage() throws Exception {
        given(postService.findAllDesc(any(Pageable.class), eq("키워드"), eq(Category.NOTICE)))
                .willReturn(new PostsPageResponseDto(List.of(), 5, 10, 42, 5, false, true));

        mockMvc.perform(get("/").param("page", "5").param("q", "키워드").param("category", "NOTICE"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location",
                        containsString("page=4")));
    }

    @Test
    @DisplayName("GET /?q=키워드&category=NOTICE 는 검색어·분류를 서비스에 그대로 전달한다")
    void index_passesSearchKeywordAndCategoryToService() throws Exception {
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
    @DisplayName("F12: GET /?sort=content,desc 는 허용되지 않는 정렬을 무시하고 기본 정렬로 렌더링한다")
    void index_withDisallowedSort_ignoresSortAndRendersWithDefault() throws Exception {
        given(postService.findAllDesc(any(Pageable.class), any(), any()))
                .willReturn(new PostsPageResponseDto(List.of(), 0, 10, 0, 0, true, true));
        given(postService.findPopular(5)).willReturn(List.of());

        mockMvc.perform(get("/").param("sort", "content,desc"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"));
    }

    @Test
    @DisplayName("GET /posts/save 는 인증 없이도 등록 화면을 보여준다")
    void postsSave_isAccessibleWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/posts/save"))
                .andExpect(status().isOk())
                .andExpect(view().name("post/post-save"));
    }

    @Test
    @DisplayName("GET /posts/update/{id} 는 조회한 게시글과 댓글 목록을 모델에 담아 렌더링한다")
    void postsUpdate_rendersUpdateViewWithPostAndComments() throws Exception {
        given(postService.findByIdForView(eq(1L), nullable(Authentication.class)))
                .willReturn(new PostViewDto(1L, "제목", "내용", null, "작성자", false, Category.FREE, 0L, 0L, false, 0L));
        given(commentService.findInitialPageForView(eq(1L), nullable(Authentication.class)))
                .willReturn(new CommentPageDto(List.of(), 0, false));

        mockMvc.perform(get("/posts/update/1"))
                .andExpect(status().isOk())
                .andExpect(view().name("post/post-update"))
                .andExpect(model().attributeExists("post", "comments"));
    }

    @Test
    @DisplayName("[의도된 동작] 존재하지 않는 게시글의 읽기 화면은 404 안내 화면을 렌더링한다 — " +
            "PostNotFoundException은 ApiExceptionHandler(REST 컨트롤러 전용)의 범위 밖이지만, " +
            "ViewExceptionHandler가 화면 컨트롤러 전용으로 404 + error/not-found 뷰로 변환한다.")
    void postsUpdate_whenPostNotFound_rendersNotFoundViewWith404() throws Exception {
        given(postService.findByIdForView(eq(999L), nullable(Authentication.class)))
                .willThrow(new PostNotFoundException(999L));

        mockMvc.perform(get("/posts/update/999"))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/not-found"));
    }

    @Test
    @DisplayName("GET /posts/save 는 일반 사용자에게 공지(NOTICE) 분류 옵션을 보여주지 않는다")
    void postsSave_hidesNoticeOptionFromNonAdmin() throws Exception {
        // 등록 폼은 Vue 아일랜드(src/vue/post-save)로 렌더링된다. 고를 수 있는 분류는
        // #post-save-initial-data 스크립트의 JSON으로 내려가며, 실제 경계는 저장 요청에서
        // CategoryPolicy가 다시 잡는다.
        mockMvc.perform(get("/posts/save").with(user("tester@example.com").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"post-save-initial-data\"")))
                .andExpect(content().string(containsString("\"value\":\"FREE\"")))
                .andExpect(content().string(containsString("\"value\":\"QNA\"")))
                .andExpect(content().string(not(containsString("\"value\":\"NOTICE\""))));
    }

    @Test
    @DisplayName("GET /posts/save 는 관리자에게 공지(NOTICE) 분류 옵션을 보여준다")
    void postsSave_showsNoticeOptionToAdmin() throws Exception {
        mockMvc.perform(get("/posts/save").with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"value\":\"NOTICE\"")));
    }

    @Test
    @DisplayName("GET /posts/save 는 로그인하지 않은 방문자에게 등록 폼 아일랜드를 렌더링하지 않는다")
    void postsSave_doesNotMountFormForAnonymous() throws Exception {
        // 폼을 보여줄지는 템플릿의 sec:authorize가 정한다. 마운트 지점이 없으면 mount.js도
        // 조용히 아무 일도 하지 않지만, 애초에 번들을 싣지도 않는다.
        mockMvc.perform(get("/posts/save"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("id=\"post-save-app\""))))
                .andExpect(content().string(containsString("로그인 후 게시글을 작성할 수 있습니다.")));
    }

    @Test
    @DisplayName("GET /posts/update/{id} 는 편집 충돌 감지용 버전을 Vue 초기 상태(JSON)로 내려준다")
    void postsUpdate_rendersVersionForConflictDetection() throws Exception {
        given(postService.findByIdForView(eq(1L), nullable(Authentication.class)))
                .willReturn(new PostViewDto(1L, "제목", "내용", null, "작성자", true, Category.FREE, 0L, 0L, false, 7L));
        given(commentService.findInitialPageForView(eq(1L), nullable(Authentication.class)))
                .willReturn(new CommentPageDto(List.of(), 0, false));

        // 게시글 편집은 Vue 아일랜드(src/vue/post-edit)로 렌더링된다. 버전은 #post-initial-data
        // 스크립트의 JSON에 담겨 내려가고, 저장 요청이 그대로 돌려보내 서버가 충돌을 판별한다.
        mockMvc.perform(get("/posts/update/1").with(user("tester@example.com").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"post-initial-data\"")))
                .andExpect(content().string(containsString("\"version\":7")));
    }

    @Test
    @DisplayName("F12: 편집 취소가 분류를 되돌릴 수 있도록 원본 분류를 Vue 초기 상태(JSON)로 내려준다")
    void postsUpdate_rendersOriginalCategoryForCancel() throws Exception {
        given(postService.findByIdForView(eq(1L), nullable(Authentication.class)))
                .willReturn(new PostViewDto(1L, "제목", "내용", null, "작성자", true, Category.QNA, 0L, 0L, false, 0L));
        given(commentService.findInitialPageForView(eq(1L), nullable(Authentication.class)))
                .willReturn(new CommentPageDto(List.of(), 0, false));

        // 이 값이 없어서 cancelEdit()이 분류만 복원하지 못했다 — 변경 감지에서도 빠져 있었다
        // (지금은 Vue의 original/draft 키 순회 비교가 이 회귀를 구조적으로 막는다).
        mockMvc.perform(get("/posts/update/1").with(user("tester@example.com").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"post-initial-data\"")))
                .andExpect(content().string(containsString("\"category\":\"QNA\"")));
    }

    @Test
    @DisplayName("제목·댓글에 </script>가 있어도 script 태그를 탈출하지 못한다 (저장형 XSS 방지)")
    void postsUpdate_escapesScriptClosingSequenceInEmbeddedJson() throws Exception {
        String payload = "</script><script>alert(1)</script>";
        given(postService.findByIdForView(eq(1L), nullable(Authentication.class)))
                .willReturn(new PostViewDto(1L, payload, "내용", null, "작성자", true, Category.FREE, 0L, 0L, false, 0L));
        given(commentService.findInitialPageForView(eq(1L), nullable(Authentication.class)))
                .willReturn(new CommentPageDto(
                        List.of(new CommentViewDto(2L, 1L, payload, "댓글작성자", null, true)), 1, false));

        mockMvc.perform(get("/posts/update/1").with(user("tester@example.com").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("</script><script>alert"))))
                .andExpect(content().string(containsString("\\u003c/script\\u003e")));
    }
}
