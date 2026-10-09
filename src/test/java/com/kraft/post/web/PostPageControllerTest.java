package com.kraft.post.web;

import com.kraft.comment.dto.CommentPageDto;
import com.kraft.comment.dto.CommentViewDto;
import com.kraft.comment.service.CommentService;
import com.kraft.config.security.SecurityConfig;
import com.kraft.post.domain.Category;
import com.kraft.post.domain.PostNotFoundException;
import com.kraft.post.dto.PostRowDto;
import com.kraft.post.dto.PostsListResponseDto;
import com.kraft.post.dto.PostsPageResponseDto;
import com.kraft.post.dto.PostViewDto;
import com.kraft.post.service.PostQueryService;
import com.kraft.shared.web.WriteRateLimiters;
import com.kraft.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
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
 * {@link PostPageController} 화면 계층 테스트. {@code PostQueryService}/{@code CommentService}는 모킹하고, 실제 {@link SecurityConfig}를 임포트해 CSRF 메타 태그가 필요한 헤더 fragment까지 렌더링되는 실제 요청 흐름을 재현한다.
 * <p>
 * {@code page=-1} 테스트는 500을 유발했던 버그의 회귀 방지 테스트다: {@code @RequestParam int page}로 직접 받으면 음수 페이지가 {@code PageRequest.of(-1, ...)}에서 {@code IllegalArgumentException}을 던지지만,
 * {@code Pageable}을 {@code @PageableDefault}로 받으면 Spring Data가 안전하게 0으로 보정한다.
 */
@WebMvcTest(PostPageController.class)
@Import(SecurityConfig.class)
class PostPageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PostQueryService postQueryService;

    @MockitoBean
    private CommentService commentService;

    /** 화면이 "글을 쓸 수 있는 사람인가"를 물어보는 곳. 기본 모킹은 빈 값(=쓸 수 있음)이다. */
    @MockitoBean
    private UserService userService;

    @MockitoBean
    private WriteRateLimiters rateLimiters;

    @MockitoBean
    private PostViewDedup postViewDedup;

    /** 검색 제한기·중복 방문 판정은 이 슬라이스의 관심사가 아니다 — 기본으로 항상 통과(=조회수를 센다)시킨다. */
    @BeforeEach
    void allowAllRateLimits() {
        given(rateLimiters.tryAcquireSearch(any())).willReturn(true);
        given(postViewDedup.shouldCount(any(), any(), any(), any())).willReturn(true);
    }

    @Test
    @DisplayName("GET / 는 목록을 모델에 담아 index 뷰를 렌더링한다")
    void index_rendersIndexViewWithPostsModel() throws Exception {
        given(postQueryService.findAllDesc(any(Pageable.class), any(), any(), anyBoolean()))
                .willReturn(new PostsPageResponseDto(List.of(), 0, 10, 0L, 0, true, true));
        given(postQueryService.findPopular(5)).willReturn(List.of());

        mockMvc.perform(get("/community"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attributeExists("posts", "postsPage", "pageWindow"));
    }

    @Test
    @DisplayName("[회귀 방지] GET /?page=-1 은 500이 아니라 정상 렌더링된다")
    void index_withNegativePage_rendersSuccessfullyWithoutServerError() throws Exception {
        given(postQueryService.findAllDesc(any(Pageable.class), any(), any(), anyBoolean()))
                .willReturn(new PostsPageResponseDto(List.of(), 0, 10, 0L, 0, true, true));
        given(postQueryService.findPopular(5)).willReturn(List.of());

        mockMvc.perform(get("/community").param("page", "-1"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"));
    }

    @Test
    @DisplayName("[회귀 방지] GET /?page=999 (범위 초과, 글이 하나도 없음) 도 500이 아니라 정상 렌더링된다")
    void index_withOutOfRangePage_rendersSuccessfullyWithoutServerError() throws Exception {
        given(postQueryService.findAllDesc(any(Pageable.class), any(), any(), anyBoolean()))
                .willReturn(new PostsPageResponseDto(List.of(), 999, 10, 0L, 0, true, true));
        given(postQueryService.findPopular(5)).willReturn(List.of());

        mockMvc.perform(get("/community").param("page", "999"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"));
    }

    /**
     * PageWindow는 표시용 페이지 번호를 [0, totalPages-1]로 보정하지만 실제 조회는 요청받은 원래 page 그대로 돈다 — 글이 있는데도 범위를 넘는 page를 요청하면(예: 다른 글이 지워져 페이지 수가 줄어든 경우)
     * 빈 목록과 "현재"로 표시되는 페이지가 하나도 없는 페이지네이션이 동시에 보인다. 검색어·분류를 유지한 채 유효한 마지막 페이지로 보내는지 확인한다.
     */
    @Test
    @DisplayName("글은 있지만 범위를 넘는 page를 요청하면 유효한 마지막 페이지로 보낸다")
    void index_withOutOfRangePageButPostsExist_redirectsToLastValidPage() throws Exception {
        given(postQueryService.findAllDesc(any(Pageable.class), eq("키워드"), eq(Category.NOTICE), anyBoolean()))
                .willReturn(new PostsPageResponseDto(List.of(), 5, 10, 42L, 5, false, true));

        mockMvc.perform(get("/community").param("page", "5").param("q", "키워드").param("category", "NOTICE"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location",
                        containsString("page=4")));
    }

    /** 검색어가 있으면 전체 건수를 세지 않는다(totalElements/totalPages == null). 화면은 총 건수 없이도 렌더링되어야 하고, 번호 목록 대신 이전·다음과 "N페이지"만 보여야 한다. */
    @Test
    @DisplayName("검색 결과(총 건수 없음)는 총 개수·번호 목록 없이 이전·다음과 N페이지만 그린다")
    void index_searchResultsWithoutTotals_rendersPrevNextOnly() throws Exception {
        PostsListResponseDto row = new PostsListResponseDto(new PostRowDto(
                1L, "검색 결과 글", "작성자", LocalDateTime.of(2026, 10, 1, 9, 0),
                LocalDateTime.of(2026, 10, 1, 9, 0), Category.FREE, 0L), 0L);
        given(postQueryService.findAllDesc(any(Pageable.class), eq("검색"), any(), anyBoolean()))
                .willReturn(new PostsPageResponseDto(List.of(row), 1, 10, null, null, false, false));
        given(postQueryService.findPopular(5)).willReturn(List.of());

        mockMvc.perform(get("/community").param("q", "검색").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(content().string(containsString("검색 결과 글")))
                .andExpect(content().string(not(containsString("board-head__count"))))
                .andExpect(content().string(not(containsString("pager__numbers"))))
                .andExpect(content().string(containsString("2</span>페이지")))
                .andExpect(content().string(containsString("data-role=\"next\"")))
                .andExpect(content().string(containsString("data-total-pages=\"0\"")));
    }

    @Test
    @DisplayName("검색 결과가 한 페이지에 다 들어오면(첫 페이지, 다음 없음) pager를 그리지 않는다")
    void index_singlePageOfSearchResults_hasNoPager() throws Exception {
        PostsListResponseDto row = new PostsListResponseDto(new PostRowDto(
                1L, "검색 결과 글", "작성자", LocalDateTime.of(2026, 10, 1, 9, 0),
                LocalDateTime.of(2026, 10, 1, 9, 0), Category.FREE, 0L), 0L);
        given(postQueryService.findAllDesc(any(Pageable.class), eq("검색"), any(), anyBoolean()))
                .willReturn(new PostsPageResponseDto(List.of(row), 0, 10, null, null, true, true));
        given(postQueryService.findPopular(5)).willReturn(List.of());

        mockMvc.perform(get("/community").param("q", "검색"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("검색 결과 글")))
                .andExpect(content().string(not(containsString("class=\"pager\""))));
    }

    @Test
    @DisplayName("검색 결과가 없으면(총 건수 없음) '검색 조건에 맞는 게시글이 없습니다'를 보여준다")
    void index_emptySearchWithoutTotals_showsNoMatchMessage() throws Exception {
        given(postQueryService.findAllDesc(any(Pageable.class), eq("없는검색어"), any(), anyBoolean()))
                .willReturn(new PostsPageResponseDto(List.of(), 0, 10, null, null, true, true));
        given(postQueryService.findPopular(5)).willReturn(List.of());

        mockMvc.perform(get("/community").param("q", "없는검색어"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("검색 조건에 맞는 게시글이 없습니다")))
                .andExpect(content().string(not(containsString("아직 게시글이 없습니다"))));
    }

    /** 총 페이지 수를 모르므로 "마지막 페이지"로 보낼 수 없다 — 빈 결과인 범위 밖 페이지는 검색어·분류·정렬을 유지한 채 첫 페이지(page 생략)로 보낸다. */
    @Test
    @DisplayName("검색 결과가 빈 범위 밖 page는 검색 조건을 유지한 채 첫 페이지로 보낸다")
    void index_searchBeyondLastPage_redirectsToFirstPageKeepingFilters() throws Exception {
        given(postQueryService.findAllDesc(any(Pageable.class), eq("키워드"), eq(Category.NOTICE), anyBoolean()))
                .willReturn(new PostsPageResponseDto(List.of(), 7, 10, null, null, false, true));

        mockMvc.perform(get("/community").param("page", "7").param("q", "키워드").param("category", "NOTICE"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", not(containsString("page="))))
                .andExpect(header().string("Location", containsString("category=NOTICE")))
                // 한글은 퍼센트 인코딩되어야 한다("키워드"). 인코딩하지 않으면 실제 서버가 Location 헤더를 만들지 못해 리다이렉트가 일어나지 않는다.
                .andExpect(header().string("Location", containsString("q=%ED%82%A4%EC%9B%8C%EB%93%9C")));
    }

    @Test
    @DisplayName("검색어 없는 목록의 범위 밖 page는 기존처럼 마지막 페이지로 보낸다(총 건수가 있다)")
    void index_listWithTotals_stillRedirectsToLastPage() throws Exception {
        given(postQueryService.findAllDesc(any(Pageable.class), any(), any(), anyBoolean()))
                .willReturn(new PostsPageResponseDto(List.of(), 9, 10, 25L, 3, false, true));

        mockMvc.perform(get("/community").param("page", "9"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("page=2")));
    }

    @Test
    @DisplayName("범위를 넘는 page를 정렬과 함께 요청해도 리다이렉트 URL이 sort를 유지한다")
    void index_withOutOfRangePageAndSort_redirectKeepsSort() throws Exception {
        given(postQueryService.findAllDesc(any(Pageable.class), any(), any(), anyBoolean()))
                .willReturn(new PostsPageResponseDto(List.of(), 5, 10, 42L, 5, false, true));

        mockMvc.perform(get("/community").param("page", "5").param("sort", "viewCount,desc"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("sort=viewCount,desc")));
    }

    @Test
    @DisplayName("GET /?q=키워드&category=NOTICE 는 검색어·분류를 서비스에 그대로 전달한다")
    void index_passesSearchKeywordAndCategoryToService() throws Exception {
        given(postQueryService.findAllDesc(any(Pageable.class), eq("키워드"), eq(Category.NOTICE), anyBoolean()))
                .willReturn(new PostsPageResponseDto(List.of(), 0, 10, 0L, 0, true, true));
        given(postQueryService.findPopular(5)).willReturn(List.of());

        mockMvc.perform(get("/community").param("q", "키워드").param("category", "NOTICE"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attribute("q", "키워드"))
                .andExpect(model().attribute("category", Category.NOTICE));
    }

    @Test
    @DisplayName("GET /?q=...&scope=all 은 제목+내용 검색으로 전달하고 searchContent 모델 값도 true다")
    void index_withScopeAll_searchesContentTooAndExposesModelFlag() throws Exception {
        given(postQueryService.findAllDesc(any(Pageable.class), eq("키워드"), any(), eq(true)))
                .willReturn(new PostsPageResponseDto(List.of(), 0, 10, 0L, 0, true, true));
        given(postQueryService.findPopular(5)).willReturn(List.of());

        mockMvc.perform(get("/community").param("q", "키워드").param("scope", "all"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("searchContent", true));

        org.mockito.Mockito.verify(postQueryService).findAllDesc(any(Pageable.class), eq("키워드"), any(), eq(true));
    }

    @Test
    @DisplayName("scope 파라미터가 없으면 제목만(false)으로 검색하고 모델 값도 false다")
    void index_withoutScope_defaultsToTitleOnly() throws Exception {
        given(postQueryService.findAllDesc(any(Pageable.class), eq("키워드"), any(), eq(false)))
                .willReturn(new PostsPageResponseDto(List.of(), 0, 10, 0L, 0, true, true));
        given(postQueryService.findPopular(5)).willReturn(List.of());

        mockMvc.perform(get("/community").param("q", "키워드"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("searchContent", false));
    }

    /** q 없는 일반 목록 열람은 검색 제한기를 건드리지 않는다. */
    @Test
    @DisplayName("GET / 는 q가 없으면 검색 속도 제한을 검사하지 않는다")
    void index_withoutKeyword_skipsSearchRateLimit() throws Exception {
        given(postQueryService.findAllDesc(any(Pageable.class), any(), any(), anyBoolean()))
                .willReturn(new PostsPageResponseDto(List.of(), 0, 10, 0L, 0, true, true));
        given(postQueryService.findPopular(5)).willReturn(List.of());

        mockMvc.perform(get("/community")).andExpect(status().isOk());

        org.mockito.Mockito.verify(rateLimiters, org.mockito.Mockito.never()).tryAcquireSearch(any());
    }

    @Test
    @DisplayName("GET /?q=... 는 검색 속도 제한에 걸리면 429(전역 4xx 오류 화면)를 돌려준다")
    void index_withKeyword_whenRateLimited_returns429() throws Exception {
        given(rateLimiters.tryAcquireSearch(any())).willReturn(false);

        mockMvc.perform(get("/community").param("q", "키워드"))
                .andExpect(status().isTooManyRequests());

        org.mockito.Mockito.verify(postQueryService, org.mockito.Mockito.never()).findAllDesc(any(), any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("GET /?sort=content,desc 는 허용되지 않는 정렬을 무시하고 기본 정렬로 렌더링한다")
    void index_withDisallowedSort_ignoresSortAndRendersWithDefault() throws Exception {
        given(postQueryService.findAllDesc(any(Pageable.class), any(), any(), anyBoolean()))
                .willReturn(new PostsPageResponseDto(List.of(), 0, 10, 0L, 0, true, true));
        given(postQueryService.findPopular(5)).willReturn(List.of());

        mockMvc.perform(get("/community").param("sort", "content,desc"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                // 허용되지 않는 정렬은 무시되므로 화면이 되돌려 쓸 currentSort도 비어 있어야 한다 — 검색 폼의 정렬 select가 "최신 등록순"으로 남고 페이지 링크에도 sort=content,desc가 실리지 않는다.
                .andExpect(model().attribute("currentSort", org.hamcrest.Matchers.nullValue()));
    }

    @Test
    @DisplayName("GET /?sort=viewCount,desc 는 화면이 되돌려 쓸 currentSort를 모델에 담는다")
    void index_withAllowedSort_setsCurrentSortModelAttribute() throws Exception {
        given(postQueryService.findAllDesc(any(Pageable.class), any(), any(), anyBoolean()))
                .willReturn(new PostsPageResponseDto(List.of(), 0, 10, 0L, 0, true, true));
        given(postQueryService.findPopular(5)).willReturn(List.of());

        mockMvc.perform(get("/community").param("sort", "viewCount,desc"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attribute("currentSort", "viewCount,desc"));
    }

    @Test
    @DisplayName("정렬을 지정하지 않으면 currentSort는 null이다(기본 최신순을 URL에 노출하지 않는다)")
    void index_withoutSortParam_currentSortIsNull() throws Exception {
        given(postQueryService.findAllDesc(any(Pageable.class), any(), any(), anyBoolean()))
                .willReturn(new PostsPageResponseDto(List.of(), 0, 10, 0L, 0, true, true));
        given(postQueryService.findPopular(5)).willReturn(List.of());

        mockMvc.perform(get("/community"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("currentSort", org.hamcrest.Matchers.nullValue()));
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
        given(postQueryService.findByIdForView(eq(1L), nullable(Authentication.class), anyBoolean()))
                .willReturn(new PostViewDto(1L, "제목", "내용", null, null, null, "작성자", false, Category.FREE, 0L, 0L, false, 0L));
        given(commentService.findInitialPageForView(eq(1L), nullable(Authentication.class)))
                .willReturn(new CommentPageDto(List.of(), 0L, false));

        mockMvc.perform(get("/posts/update/1"))
                .andExpect(status().isOk())
                .andExpect(view().name("post/post-update"))
                .andExpect(model().attributeExists("post", "comments", "relatedPosts"));
    }

    @Test
    @DisplayName("GET /posts/update/{id} 는 본문을 마크다운으로 해석해 그린다(SSR)")
    void postsUpdate_rendersMarkdownContentAsHtml() throws Exception {
        given(postQueryService.findByIdForView(eq(1L), nullable(Authentication.class), anyBoolean()))
                .willReturn(new PostViewDto(1L, "제목", "**굵게** 본문", null, null, null, "작성자", false, Category.FREE, 0L, 0L, false, 0L));
        given(commentService.findInitialPageForView(eq(1L), nullable(Authentication.class)))
                .willReturn(new CommentPageDto(List.of(), 0L, false));

        String content = mockMvc.perform(get("/posts/update/1"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // post-initial-data(Vue 하이드레이션용 원문 JSON)·meta description에는 원문 그대로 "**굵게**"가 남는다(의도된 동작 — Vue가 그 JSON을 마크다운으로 다시 해석하고, 메타 설명은 검색엔진·링크 미리보기용 평문 발췌다).
        // 이 테스트가 보려는 것은 서버가 먼저 그리는 post-ssr 본문 하나뿐이므로 그 구간만 뽑아 확인한다.
        String ssrBody = content.substring(content.indexOf("post-ssr"), content.indexOf("</article>"));
        String normalizedBody = normalizedWhitespace(ssrBody);

        // 템플릿 소스의 줄바꿈·들여쓰기가 th:text 주변에 그대로 남아 "<strong>굵게</strong>"처럼 붙어 나오지 않는다 — 태그 인접 여부만 볼 때는 공백을 지우고 비교한다(브라우저에서는 인라인 요소 사이 공백이 문제되지 않는다).
        assertThat(normalizedBody).contains("<strong>굵게</strong>");
        // 마크다운 문법 문자 자체는 렌더링된 본문에 남지 않는다.
        assertThat(normalizedBody).doesNotContain("**굵게**");
    }

    @Test
    @DisplayName("GET /posts/update/{id} 는 본문에 HTML 태그가 있어도 해석하지 않고 이스케이프해 보여준다")
    void postsUpdate_escapesHtmlTagsInMarkdownContent() throws Exception {
        given(postQueryService.findByIdForView(eq(1L), nullable(Authentication.class), anyBoolean()))
                .willReturn(new PostViewDto(1L, "제목", "<script>alert(1)</script>", null, null, null, "작성자", false, Category.FREE, 0L, 0L, false, 0L));
        given(commentService.findInitialPageForView(eq(1L), nullable(Authentication.class)))
                .willReturn(new CommentPageDto(List.of(), 0L, false));

        String content = mockMvc.perform(get("/posts/update/1"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(content).doesNotContain("<script>alert(1)</script>");
        assertThat(content).contains("&lt;script&gt;");
    }

    private static String normalizedWhitespace(String html) {
        return html.replaceAll("\\s+", "");
    }

    @Test
    @DisplayName("GET /posts/update/{id} 는 관련 게시글 조회를 조회한 글의 분류·id로 위임한다")
    void postsUpdate_delegatesRelatedPostsLookupToPostCategoryAndId() throws Exception {
        given(postQueryService.findByIdForView(eq(1L), nullable(Authentication.class), anyBoolean()))
                .willReturn(new PostViewDto(1L, "제목", "내용", null, null, null, "작성자", false, Category.QNA, 0L, 0L, false, 0L));
        given(commentService.findInitialPageForView(eq(1L), nullable(Authentication.class)))
                .willReturn(new CommentPageDto(List.of(), 0L, false));

        mockMvc.perform(get("/posts/update/1"))
                .andExpect(status().isOk());

        org.mockito.BDDMockito.then(postQueryService).should().findRelated(Category.QNA, 1L, 5);
    }

    @Test
    @DisplayName("[의도된 동작] 존재하지 않는 게시글의 읽기 화면은 404 안내 화면을 렌더링한다 — " +
            "PostNotFoundException은 ApiExceptionHandler(REST 컨트롤러 전용)의 범위 밖이지만, " +
            "ViewExceptionHandler가 화면 컨트롤러 전용으로 404 + error/not-found 뷰로 변환한다.")
    void postsUpdate_whenPostNotFound_rendersNotFoundViewWith404() throws Exception {
        given(postQueryService.findByIdForView(eq(999L), nullable(Authentication.class), anyBoolean()))
                .willThrow(new PostNotFoundException(999L));

        mockMvc.perform(get("/posts/update/999"))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/not-found"));
    }

    @Test
    @DisplayName("관리자가 숨긴 글의 읽기 화면은 404지만 \"관리자가 숨긴 글\" 안내를 보여준다")
    void postsUpdate_whenPostHidden_rendersHiddenNoticeWith404() throws Exception {
        given(postQueryService.findByIdForView(eq(999L), nullable(Authentication.class), anyBoolean()))
                .willThrow(new com.kraft.post.domain.PostHiddenException(999L));

        mockMvc.perform(get("/posts/update/999"))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/not-found"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().string(org.hamcrest.Matchers.containsString("관리자가 숨긴 글입니다.")));
    }

    @Test
    @DisplayName("GET /posts/save 는 일반 사용자에게 공지(NOTICE) 분류 옵션을 보여주지 않는다")
    void postsSave_hidesNoticeOptionFromNonAdmin() throws Exception {
        // 등록 폼은 Vue 아일랜드(src/vue/post-save)로 렌더링된다. 고를 수 있는 분류는 #post-save-initial-data 스크립트의 JSON으로 내려가며, 실제 경계는 저장 요청에서 CategoryPolicy가 다시 잡는다.
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
        // 폼을 보여줄지는 템플릿의 sec:authorize가 정한다. 마운트 지점이 없으면 번들도 싣지 않는다.
        mockMvc.perform(get("/posts/save"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("id=\"post-save-app\""))))
                .andExpect(content().string(containsString("로그인 후 게시글을 작성할 수 있습니다.")));
    }

    @Test
    @DisplayName("GET /posts/update/{id} 는 편집 충돌 감지용 버전을 Vue 초기 상태(JSON)로 내려준다")
    void postsUpdate_rendersVersionForConflictDetection() throws Exception {
        given(postQueryService.findByIdForView(eq(1L), nullable(Authentication.class), anyBoolean()))
                .willReturn(new PostViewDto(1L, "제목", "내용", null, null, null, "작성자", true, Category.FREE, 0L, 0L, false, 7L));
        given(commentService.findInitialPageForView(eq(1L), nullable(Authentication.class)))
                .willReturn(new CommentPageDto(List.of(), 0L, false));

        // 게시글 편집은 Vue 아일랜드(src/vue/post-edit)로 렌더링된다. 버전은 #post-initial-data 스크립트의 JSON에 담겨 내려가고, 저장 요청이 그대로 돌려보내 서버가 충돌을 판별한다.
        mockMvc.perform(get("/posts/update/1").with(user("tester@example.com").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"post-initial-data\"")))
                .andExpect(content().string(containsString("\"version\":7")));
    }

    @Test
    @DisplayName("편집 취소가 분류를 되돌릴 수 있도록 원본 분류를 Vue 초기 상태(JSON)로 내려준다")
    void postsUpdate_rendersOriginalCategoryForCancel() throws Exception {
        given(postQueryService.findByIdForView(eq(1L), nullable(Authentication.class), anyBoolean()))
                .willReturn(new PostViewDto(1L, "제목", "내용", null, null, null, "작성자", true, Category.QNA, 0L, 0L, false, 0L));
        given(commentService.findInitialPageForView(eq(1L), nullable(Authentication.class)))
                .willReturn(new CommentPageDto(List.of(), 0L, false));

        // 변경 감지가 분류 필드를 빠뜨리면 cancelEdit()이 분류만 복원하지 못한다 — 지금은 Vue의 original/draft 키 순회 비교가 이를 구조적으로 막는다.
        mockMvc.perform(get("/posts/update/1").with(user("tester@example.com").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"post-initial-data\"")))
                .andExpect(content().string(containsString("\"category\":\"QNA\"")));
    }

    @Test
    @DisplayName("제목·댓글에 </script>가 있어도 script 태그를 탈출하지 못한다 (저장형 XSS 방지)")
    void postsUpdate_escapesScriptClosingSequenceInEmbeddedJson() throws Exception {
        String payload = "</script><script>alert(1)</script>";
        given(postQueryService.findByIdForView(eq(1L), nullable(Authentication.class), anyBoolean()))
                .willReturn(new PostViewDto(1L, payload, "내용", null, null, null, "작성자", true, Category.FREE, 0L, 0L, false, 0L));
        given(commentService.findInitialPageForView(eq(1L), nullable(Authentication.class)))
                .willReturn(new CommentPageDto(
                        List.of(new CommentViewDto(2L, 1L, null, payload, "댓글작성자", null, true, List.of(), 0L, false, 0L, false)), 1L, false));

        mockMvc.perform(get("/posts/update/1").with(user("tester@example.com").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("</script><script>alert"))))
                .andExpect(content().string(containsString("\\u003c/script\\u003e")));
    }
}
