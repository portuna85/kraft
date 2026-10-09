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
        // 검색(LIKE 전체 스캔)은 속도를 제한한다. q가 없는 일반 열람은 제외.
        if (q != null && !q.isBlank() && !rateLimiters.tryAcquireSearch(request.getRemoteAddr())) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.");
        }
        // 검색 결과는 색인하지 않되 링크는 따라가게 한다(robots.txt로 막으면 noindex를 읽지 못한다).
        if (q != null && !q.isBlank()) {
            response.setHeader("X-Robots-Tag", "noindex, follow");
        }
        Pageable sanitized = PostSortPolicy.sanitize(pageable);
        boolean searchContent = SearchScope.isContent(scope);
        PostsPageResponseDto postsPage = postQueryService.findAllDesc(sanitized, q, category, searchContent);
        // 화면이 링크에 되돌려 붙일 정렬 문자열(예: "viewCount,desc"). 기본 최신순이면 null.
        String currentSort = currentSortParam(sanitized.getSort());

        // 범위를 넘는 page(?page=999)는 빈 화면이 되므로 검색어·분류·정렬을 유지한 채 마지막 페이지로
        // 보낸다. 검색 중에는 전체 건수를 몰라(totalPages가 null) 빈 범위 밖 페이지는 첫 페이지로 보낸다.
        Integer totalPages = postsPage.totalPages();
        boolean pastLastKnownPage = totalPages != null && totalPages > 0 && pageable.getPageNumber() >= totalPages;
        boolean emptyBeyondFirst = totalPages == null && postsPage.content().isEmpty() && pageable.getPageNumber() > 0;
        if (pastLastKnownPage || emptyBeyondFirst) {
            return "redirect:" + UriComponentsBuilder.fromPath("/community")
                    .queryParamIfPresent("page", pastLastKnownPage ? Optional.of(totalPages - 1) : Optional.empty())
                    .queryParamIfPresent("q", Optional.ofNullable(q).filter(s -> !s.isBlank()))
                    .queryParamIfPresent("category", Optional.ofNullable(category))
                    .queryParamIfPresent("sort", Optional.ofNullable(currentSort))
                    .queryParamIfPresent("scope", Optional.ofNullable(scope).filter(SearchScope::isContent))
                    // 한글 검색어는 반드시 퍼센트 인코딩한다(아니면 Tomcat이 Location 헤더를 못 만든다).
                    .build()
                    .encode()
                    .toUriString();
        }

        model.addAttribute("posts", postsPage.content());
        model.addAttribute("postsPage", postsPage);
        // 전체 건수를 모르는 검색 결과는 이전·다음만 둔다.
        model.addAttribute("pageWindow", totalPages != null
                ? PageWindow.of(postsPage.page(), totalPages)
                : PageWindow.simple(postsPage.page(), !postsPage.last()));
        model.addAttribute("popularPosts", postQueryService.findPopular(5));
        // 고정 글은 검색·분류로 좁히지 않은 첫 페이지에만 보인다.
        boolean showPinned = (q == null || q.isBlank()) && category == null && pageable.getPageNumber() == 0;
        model.addAttribute("pinnedPosts", showPinned ? postQueryService.findPinned(PostQueryService.PINNED_LIMIT) : List.of());
        model.addAttribute("q", q);
        model.addAttribute("category", category);
        model.addAttribute("currentSort", currentSort);
        model.addAttribute("searchContent", searchContent);
        model.addAttribute("categories", Category.values());
        model.addAttribute("pageTitle", "커뮤니티");
        // 대표 경로는 검색하지 않은 첫 페이지에만 준다(2쪽 이후를 첫 페이지로 모으면 나머지 글이 색인되지 않는다).
        if ((q == null || q.isBlank()) && pageable.getPageNumber() == 0) {
            model.addAttribute("canonicalPath", category == null ? "/community" : "/community?category=" + category.name());
        }
        return "index";
    }

    /**
     * 게시글 등록 화면. 폼은 Vue 아일랜드(src/vue/post-save)가 그리고, 서버만 아는 값(고를 수 있는 분류,
     * 닉네임)을 초기 상태로 내려준다. 분류 규칙은 편집 화면과 같은 {@link CategoryPolicy}다.
     */
    @GetMapping("/posts/save")
    public String postsSave(Authentication authentication, Model model) {
        // 서버가 거절할 이유(이메일 미인증 등)를 화면이 미리 보여준다.
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
        return userService.writeBlockReason(authentication).orElse(null);
    }

    /** 화면에 보여줄 닉네임. principal name은 회원 id라 보여줄 수 없으므로 {@link KraftUserDetails}를 쓴다. */
    private static String displayNameOf(Authentication authentication) {
        if (authentication == null) {
            return "";
        }
        if (authentication.getPrincipal() instanceof KraftUserDetails details) {
            return details.getDisplayName();
        }
        return authentication.getName();
    }

    /** 임시 저장 키를 계정별로 나누는 회원 id. 알 수 없으면 null(화면이 임시 저장을 건너뛴다). */
    private static Long userIdOf(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof KraftUserDetails details) {
            return details.getUserId();
        }
        return null;
    }

    /** 게시글 읽기 화면(편집은 같은 경로에서 상태만 전환한다). 관리 버튼 노출 여부는 서버가 계산한 값을 쓴다. */
    @GetMapping("/posts/update/{id}")
    public String postsUpdate(@PathVariable Long id, Authentication authentication, Model model,
                               HttpServletRequest request, HttpServletResponse response) {
        boolean countView = postViewDedup.shouldCount(request, response, id, authentication);
        PostViewDto post = postQueryService.findByIdForView(id, authentication, countView);
        CommentPageDto commentPage = commentService.findInitialPageForView(id, authentication);
        model.addAttribute("post", post);
        model.addAttribute("comments", commentPage.comments());
        // 서버가 먼저 그리는 읽기 전용 본문(Vue 마운트 전·실패 시)도 Vue(MarkdownBody.vue)와 같은 규칙의
        // MarkdownParser로 해석한다.
        model.addAttribute("postBody", MarkdownParser.parse(post.content()));
        model.addAttribute("relatedPosts", postQueryService.findRelated(post.category(), id, 5));
        // 댓글 아일랜드의 초기 상태(canManage 등 서버만 아는 화면 전용 필드 포함).
        JsonHtmlEmbedding.put(model, "commentsJson", objectMapper, commentPage);

        // 읽기·편집 아일랜드의 초기 상태. 분류 선택지는 CategoryPolicy 규칙을 따른다.
        List<CategoryOptionDto> categoryOptions = CategoryPolicy.availableCategoriesFor(authentication, post.category())
                .stream()
                .map(c -> new CategoryOptionDto(c.name(), c.getTitle()))
                .toList();
        boolean authenticated = OwnershipPolicy.isAuthenticated(authentication);
        JsonHtmlEmbedding.put(model, "postEditInitialJson", objectMapper,
                new PostEditBootstrapDto(post, categoryOptions, authenticated, userIdOf(authentication)));

        // 댓글 입력창을 보여줄지 정하는 값(글쓰기 화면과 같은 규칙). 삭제·숨김 글에는 서버도 댓글을 거절한다.
        String writeBlockReason = writeBlockReasonOf(authentication);
        model.addAttribute("canWriteComment",
                authenticated && writeBlockReason == null && !post.deleted() && !post.blinded());
        model.addAttribute("writeBlockReason", writeBlockReason);

        model.addAttribute("pageTitle", post.title());
        // 검색·링크 미리보기용(대표 경로는 쿼리 없이 글 번호만).
        model.addAttribute("pageDescription", excerpt(post.content()));
        model.addAttribute("canonicalPath", "/posts/update/" + post.id());
        model.addAttribute("ogType", "article");
        return "post/post-update";
    }

    /**
     * 정렬 select·페이지 링크가 되돌려 쓸 "property,direction" 문자열. 기본 최신순이면 null이라 URL에
     * sort를 넣지 않는다. tie-breaker는 서비스({@link PostSortPolicy#effectiveSort})가 덧붙이므로 여기는
     * 사용자가 고른 단일 정렬뿐이다.
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
