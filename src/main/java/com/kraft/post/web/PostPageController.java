package com.kraft.post.web;

import com.kraft.comment.dto.CommentPageDto;
import com.kraft.comment.service.CommentService;
import com.kraft.config.security.KraftUserDetails;
import com.kraft.post.domain.Category;
import com.kraft.post.dto.CategoryOptionDto;
import com.kraft.post.dto.PostEditBootstrapDto;
import com.kraft.post.dto.PostSaveBootstrapDto;
import com.kraft.post.dto.PostsPageResponseDto;
import com.kraft.post.dto.PostViewDto;
import com.kraft.post.markdown.MarkdownParser;
import com.kraft.post.service.CategoryPolicy;
import com.kraft.post.service.PostQueryService;
import com.kraft.post.service.PostSortPolicy;
import com.kraft.shared.security.OwnershipPolicy;
import com.kraft.shared.web.PageWindow;
import com.kraft.shared.web.WriteRateLimiters;
import com.kraft.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Controller
public class PostPageController {

    private final PostQueryService postQueryService;
    private final CommentService commentService;
    private final ObjectMapper objectMapper;
    private final UserService userService;
    private final WriteRateLimiters rateLimiters;
    private final PostViewDedup postViewDedup;

    @GetMapping("/community")
    public String index(@PageableDefault(size = 10) Pageable pageable,
                         @RequestParam(required = false) String q,
                         HttpServletRequest request,
                         HttpServletResponse response,
                         @RequestParam(required = false) Category category,
                         @RequestParam(required = false) String scope,
                         Model model) {
        // 검색은 익명·무제한이라 선행 와일드카드 LIKE 전체 스캔을 검색 폼 연타만으로 반복시킬
        // 수 있었다(전체 리뷰 2026-09-26 A-SEC-06). q가 없는 일반 목록 열람은 걸지 않는다.
        if (q != null && !q.isBlank() && !rateLimiters.tryAcquireSearch(request.getRemoteAddr())) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.");
        }
        // 내부 검색 결과는 색인하지 않되 링크는 따라가게 한다(P1-1). robots.txt로 막지 않는 이유는,
        // 막으면 검색엔진이 이 응답의 noindex를 아예 읽지 못하기 때문이다.
        if (q != null && !q.isBlank()) {
            response.setHeader("X-Robots-Tag", "noindex, follow");
        }
        Pageable sanitized = PostSortPolicy.sanitize(pageable);
        boolean searchContent = SearchScope.isContent(scope);
        PostsPageResponseDto postsPage = postQueryService.findAllDesc(sanitized, q, category, searchContent);
        // 화면(검색 폼·페이지 이동 링크)이 되돌려 붙일 수 있는 형태(예: "viewCount,desc").
        // 허용되지 않는 정렬은 sanitize가 이미 비웠으므로 여기서는 항상 안전하다. 정렬을
        // 지정하지 않았으면(기본 최신순) null이라 템플릿이 sort 파라미터 자체를 만들지 않는다.
        String currentSort = currentSortParam(sanitized.getSort());

        // PageWindow는 표시용 페이지 번호를 [0, totalPages-1]로 보정하지만, 위 조회는 요청받은
        // 원래 page 그대로 돌았다 — 범위를 넘는 page(예: ?page=999)는 빈 목록을 돌려주면서
        // 페이지네이션 링크는 보정된(마지막) 페이지를 가리켜, 그중 어느 것도 "현재"로 표시되지
        // 않는 채 빈 화면만 보였다(F09). 검색어·분류·정렬은 유지한 채 유효한 마지막 페이지로 보낸다.
        // 검색어가 있으면 전체 건수를 세지 않으므로(BE-08) totalPages가 null이다. 그때는 "마지막
        // 페이지"를 모르니, 결과가 빈 범위 밖 페이지는 첫 페이지로 보낸다.
        Integer totalPages = postsPage.totalPages();
        boolean pastLastKnownPage = totalPages != null && totalPages > 0 && pageable.getPageNumber() >= totalPages;
        boolean emptyBeyondFirst = totalPages == null && postsPage.content().isEmpty() && pageable.getPageNumber() > 0;
        if (pastLastKnownPage || emptyBeyondFirst) {
            return "redirect:" + UriComponentsBuilder.fromPath("/community")
                    // 마지막 페이지를 아는 경우만 page를 싣는다. 모를 때는 첫 페이지(page 생략)다.
                    .queryParamIfPresent("page", pastLastKnownPage ? Optional.of(totalPages - 1) : Optional.empty())
                    .queryParamIfPresent("q", Optional.ofNullable(q).filter(s -> !s.isBlank()))
                    .queryParamIfPresent("category", Optional.ofNullable(category))
                    .queryParamIfPresent("sort", Optional.ofNullable(currentSort))
                    .queryParamIfPresent("scope", Optional.ofNullable(scope).filter(SearchScope::isContent))
                    // 한글 검색어가 그대로 Location에 실리면 Tomcat이 헤더를 만들지 못해 리다이렉트가
                    // 아예 일어나지 않는다 — 반드시 퍼센트 인코딩한다.
                    .build()
                    .encode()
                    .toUriString();
        }

        model.addAttribute("posts", postsPage.content());
        model.addAttribute("postsPage", postsPage);
        // 전체 건수를 모르는 검색 결과는 번호 목록 없이 이전·다음만 둔다.
        model.addAttribute("pageWindow", totalPages != null
                ? PageWindow.of(postsPage.page(), totalPages)
                : PageWindow.simple(postsPage.page(), !postsPage.last()));
        model.addAttribute("popularPosts", postQueryService.findPopular(5));
        // 검색·분류로 좁히지 않은 첫 페이지에만 공지를 고정한다(A-BE-05) — 검색 결과나 분류별
        // 목록, 2페이지 이후에 공지가 끼어들면 "이 조건에 맞는 글"이라는 목록의 의미가 흐려진다.
        boolean showPinned = (q == null || q.isBlank()) && category == null && pageable.getPageNumber() == 0;
        model.addAttribute("pinnedPosts", showPinned ? postQueryService.findPinned(PostQueryService.PINNED_LIMIT) : List.of());
        model.addAttribute("q", q);
        model.addAttribute("category", category);
        model.addAttribute("currentSort", currentSort);
        model.addAttribute("searchContent", searchContent);
        model.addAttribute("categories", Category.values());
        model.addAttribute("pageTitle", "커뮤니티");
        // 대표 경로(F08)는 검색하지 않은 첫 페이지에만 준다 — 분류만 고른 첫 페이지는 그 분류의
        // 대표 목록이다. 검색 결과와 2쪽 이후는 대표 경로를 선언하지 않는다(첫 페이지로 모으면
        // 검색 엔진이 그 목록의 나머지 글을 보지 않게 된다).
        if ((q == null || q.isBlank()) && pageable.getPageNumber() == 0) {
            model.addAttribute("canonicalPath", category == null ? "/community" : "/community?category=" + category.name());
        }
        return "index";
    }

    /**
     * 게시글 등록 화면. 폼은 Vue 아일랜드(src/vue/post-save)가 그리므로, 서버만 판정할 수 있는
     * 값(고를 수 있는 분류, 보여줄 닉네임)을 초기 상태로 한 번 내려준다.
     * <p>
     * 새 글의 분류 기본값은 {@link Category#FREE}라 {@code current}로 그대로 넘긴다 —
     * 편집 화면과 같은 {@link CategoryPolicy} 규칙을 쓰므로 "관리자가 아니면 공지 없음"이
     * 두 화면에서 갈라지지 않는다.
     */
    @GetMapping("/posts/save")
    public String postsSave(Authentication authentication, Model model) {
        // 서버가 거절할 이유(이메일 미인증·정지)를 화면이 미리 같은 규칙으로 판단한다. 다 쓰고
        // 등록을 눌러야 이유를 알게 되는 흐름을 만들지 않으려는 것이다.
        model.addAttribute("writeBlockReason", writeBlockReasonOf(authentication));

        List<CategoryOptionDto> categoryOptions = CategoryPolicy.availableCategoriesFor(authentication, Category.FREE)
                .stream()
                .map(c -> new CategoryOptionDto(c.name(), c.getTitle()))
                .toList();
        JsonHtmlEmbedding.put(model, "postSaveInitialJson", objectMapper,
                new PostSaveBootstrapDto(categoryOptions, displayNameOf(authentication), userIdOf(authentication)));
        model.addAttribute("pageTitle", "글쓰기");
        return "post/post-save";
    }

    /** 로그인하지 않았으면 null(그 경우의 안내는 템플릿의 익명 분기가 맡는다). */
    private String writeBlockReasonOf(Authentication authentication) {
        if (!OwnershipPolicy.isAuthenticated(authentication)) {
            return null;
        }
        // principal은 이메일을 들고 있지 않다(F01) — 회원은 principal의 불변 id로 찾는다.
        return userService.writeBlockReason(authentication).orElse(null);
    }

    /**
     * 화면에 보여줄 이름. 로그인 아이디(principal name)는 회원 id다(BE-04) — 그대로 보여줄
     * 수 없으므로 닉네임을 들고 다니는 principal이면 그쪽을 쓴다(옛 세션에는 없을 수 있어
     * 이름으로 물러선다 — layout/navbar와 같은 규칙).
     */
    private static String displayNameOf(Authentication authentication) {
        if (authentication == null) {
            return "";
        }
        if (authentication.getPrincipal() instanceof KraftUserDetails details) {
            return details.getDisplayName();
        }
        return authentication.getName();
    }

    /**
     * 자동 임시 저장 키를 계정별로 분리하는 데 쓸 회원 id(전체 리뷰 2026-09-26 A-FE-03).
     * 로그인하지 않았거나 principal이 {@link KraftUserDetails}가 아니면(옛 세션 등) null —
     * 그 경우 화면 쪽(useDraftAutosave)이 사용자 구분 없는 키로 물러서지 않고 그냥 자동
     * 임시 저장을 건너뛴다.
     */
    private static Long userIdOf(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof KraftUserDetails details) {
            return details.getUserId();
        }
        return null;
    }

    /**
     * 게시글 읽기 화면. 편집은 같은 경로에서 상태만 전환한다.
     * <p>
     * 관리 버튼 노출 여부는 브라우저가 추정하지 않고 서버가 계산한 값을 그대로 쓴다
     * (익명 요청이면 {@code authentication}이 null이거나 익명 토큰이며, 두 경우 모두 false).
     */
    @GetMapping("/posts/update/{id}")
    public String postsUpdate(@PathVariable Long id, Authentication authentication, Model model,
                               HttpServletRequest request, HttpServletResponse response) {
        // 새로고침·봇·재방문이 매번 조회수를 올리지 않게 한다(전체 리뷰 2026-09-26 A-BE-04).
        boolean countView = postViewDedup.shouldCount(request, response, id, authentication);
        PostViewDto post = postQueryService.findByIdForView(id, authentication, countView);
        CommentPageDto commentPage = commentService.findInitialPageForView(id, authentication);
        model.addAttribute("post", post);
        model.addAttribute("comments", commentPage.comments());
        // 서버가 먼저 그리는 읽기 전용 본문(Vue가 마운트되기 전·마운트 실패 시)도 마크다운을
        // 해석해 보여준다(13단계) — 전에는 평문 그대로 찍어 `**굵게**` 같은 문법이 그대로
        // 보였다. Vue 쪽 렌더링(MarkdownBody.vue)과 같은 파서 로직을 옮긴
        // MarkdownParser(src/main/java/com/kraft/post/markdown)를 쓴다.
        model.addAttribute("postBody", MarkdownParser.parse(post.content()));
        model.addAttribute("relatedPosts", postQueryService.findRelated(post.category(), id, 5));
        // 댓글 영역은 Vue 아일랜드로 렌더링된다. canManage는 서버만 판정할 수 있으므로(공개
        // REST 응답에는 없는 화면 전용 필드), 초기 렌더에서 그대로 JSON으로 내려 이후 목록
        // 갱신은 클라이언트가 이 값을 들고 낙관적으로 처리하게 한다. 최초 페이지는 최대
        // PAGE_SIZE개만 담고, 전체 개수·다음 페이지 존재 여부를 함께 내려 "더 보기"가
        // 이어받게 한다(개선 보고서 "댓글 전체 로딩").
        JsonHtmlEmbedding.put(model, "commentsJson", objectMapper, commentPage);

        // 게시글 읽기·편집 영역도 Vue 아일랜드(src/vue/post-edit)로 렌더링된다. 분류 선택지는
        // post-update.html이 예전에 th:each/th:if로 걸러내던 것과 같은 규칙(CategoryPolicy)을
        // 그대로 써서, "관리자가 아니면 NOTICE 숨김·이미 공지인 글은 유지" 동작이 갈라지지 않게 한다.
        List<CategoryOptionDto> categoryOptions = CategoryPolicy.availableCategoriesFor(authentication, post.category())
                .stream()
                .map(c -> new CategoryOptionDto(c.name(), c.getTitle()))
                .toList();
        boolean authenticated = OwnershipPolicy.isAuthenticated(authentication);
        JsonHtmlEmbedding.put(model, "postEditInitialJson", objectMapper,
                new PostEditBootstrapDto(post, categoryOptions, authenticated, userIdOf(authentication)));

        // 댓글 아일랜드가 "입력창을 보여줄지"를 정하는 값. 글쓰기 화면과 같은 규칙을 쓴다.
        // 한 번만 조회해 두 속성에 함께 쓴다 — 예전에는 같은 조회를 두 번 했다.
        String writeBlockReason = writeBlockReasonOf(authentication);
        // 삭제되거나 숨겨진 글(관리자만 열 수 있다)에는 댓글을 달 수 없다 — 서버도 거절한다.
        model.addAttribute("canWriteComment",
                authenticated && writeBlockReason == null && !post.deleted() && !post.blinded());
        model.addAttribute("writeBlockReason", writeBlockReason);

        model.addAttribute("pageTitle", post.title());
        // 검색·링크 미리보기용(F08). 대표 경로는 쿼리 없이 글 번호로만 정한다.
        model.addAttribute("pageDescription", excerpt(post.content()));
        model.addAttribute("canonicalPath", "/posts/update/" + post.id());
        model.addAttribute("ogType", "article");
        return "post/post-update";
    }

    /**
     * 목록 화면의 정렬 select·페이지 링크가 되돌려 쓸 수 있는 "property,direction" 문자열을
     * 만든다. 정렬을 지정하지 않았으면(기본 최신순) null이다 — 화면은 이 값이 null이면
     * sort 파라미터 자체를 URL에 넣지 않는다(기본값을 굳이 노출하지 않는다).
     * <p>
     * 정렬 속성이 둘 이상(예: updatedAt + id tie-breaker)일 수 있으나, 그 tie-breaker는
     * {@link PostSortPolicy#effectiveSort}가 서비스 내부에서 덧붙이는 것이라 여기서 보는
     * {@code sanitized.getSort()}(sanitize 직후, effectiveSort 이전)에는 사용자가 고른
     * 단일 정렬만 있다.
     */
    private static String currentSortParam(Sort sort) {
        if (sort.isUnsorted()) {
            return null;
        }
        Sort.Order order = sort.iterator().next();
        return order.getProperty() + "," + order.getDirection().name().toLowerCase();
    }

    /** meta description에 쓸 본문 앞부분. 공백·줄바꿈을 한 칸으로 줄이고 길면 자른다. */
    static String excerpt(String content) {
        if (content == null) {
            return null;
        }
        String flat = content.strip().replaceAll("\\s+", " ");
        if (flat.isEmpty()) {
            return null;
        }
        return flat.length() <= DESCRIPTION_LENGTH
                ? flat
                : flat.substring(0, flat.offsetByCodePoints(0, flat.codePointCount(0, DESCRIPTION_LENGTH))) + "…";
    }

    private static final int DESCRIPTION_LENGTH = 150;

}
