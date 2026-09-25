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
import com.kraft.post.service.CategoryPolicy;
import com.kraft.post.service.PostService;
import com.kraft.shared.security.OwnershipPolicy;
import com.kraft.shared.web.PageWindow;
import com.kraft.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Controller
public class PostPageController {

    private final PostService postService;
    private final CommentService commentService;
    private final ObjectMapper objectMapper;
    private final UserService userService;

    @GetMapping("/")
    public String index(@PageableDefault(size = 10) Pageable pageable,
                         @RequestParam(required = false) String q,
                         @RequestParam(required = false) Category category,
                         Model model) {
        PostsPageResponseDto postsPage = postService.findAllDesc(PostSortPolicy.sanitize(pageable), q, category);

        // PageWindow는 표시용 페이지 번호를 [0, totalPages-1]로 보정하지만, 위 조회는 요청받은
        // 원래 page 그대로 돌았다 — 범위를 넘는 page(예: ?page=999)는 빈 목록을 돌려주면서
        // 페이지네이션 링크는 보정된(마지막) 페이지를 가리켜, 그중 어느 것도 "현재"로 표시되지
        // 않는 채 빈 화면만 보였다(F09). 검색어·분류는 유지한 채 유효한 마지막 페이지로 보낸다.
        if (postsPage.totalPages() > 0 && pageable.getPageNumber() >= postsPage.totalPages()) {
            return "redirect:" + UriComponentsBuilder.fromPath("/")
                    .queryParam("page", postsPage.totalPages() - 1)
                    .queryParamIfPresent("q", Optional.ofNullable(q).filter(s -> !s.isBlank()))
                    .queryParamIfPresent("category", Optional.ofNullable(category))
                    .build()
                    .toUriString();
        }

        model.addAttribute("posts", postsPage.content());
        model.addAttribute("postsPage", postsPage);
        model.addAttribute("pageWindow", PageWindow.of(postsPage.page(), postsPage.totalPages()));
        model.addAttribute("popularPosts", postService.findPopular(5));
        model.addAttribute("q", q);
        model.addAttribute("category", category);
        model.addAttribute("categories", Category.values());
        model.addAttribute("pageTitle", "전체 게시글");
        // 대표 경로(F08)는 검색하지 않은 첫 페이지에만 준다 — 분류만 고른 첫 페이지는 그 분류의
        // 대표 목록이다. 검색 결과와 2쪽 이후는 대표 경로를 선언하지 않는다(첫 페이지로 모으면
        // 검색 엔진이 그 목록의 나머지 글을 보지 않게 된다).
        if ((q == null || q.isBlank()) && pageable.getPageNumber() == 0) {
            model.addAttribute("canonicalPath", category == null ? "/" : "/?category=" + category.name());
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
        model.addAttribute("postSaveInitialJson", JsonHtmlEmbedding.escapeForHtmlScript(
                objectMapper.writeValueAsString(new PostSaveBootstrapDto(categoryOptions, displayNameOf(authentication)))));
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
     * 화면에 보여줄 이름. 로그인 아이디는 이메일이므로 닉네임을 들고 다니는 principal이면
     * 그쪽을 쓴다(옛 세션에는 없을 수 있어 이름으로 물러선다 — layout/navbar와 같은 규칙).
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
     * 게시글 읽기 화면. 편집은 같은 경로에서 상태만 전환한다.
     * <p>
     * 관리 버튼 노출 여부는 브라우저가 추정하지 않고 서버가 계산한 값을 그대로 쓴다
     * (익명 요청이면 {@code authentication}이 null이거나 익명 토큰이며, 두 경우 모두 false).
     */
    @GetMapping("/posts/update/{id}")
    public String postsUpdate(@PathVariable Long id, Authentication authentication, Model model) {
        PostViewDto post = postService.findByIdForView(id, authentication);
        CommentPageDto commentPage = commentService.findInitialPageForView(id, authentication);
        model.addAttribute("post", post);
        model.addAttribute("comments", commentPage.comments());
        model.addAttribute("relatedPosts", postService.findRelated(post.category(), id, 5));
        // 댓글 영역은 Vue 아일랜드로 렌더링된다. canManage는 서버만 판정할 수 있으므로(공개
        // REST 응답에는 없는 화면 전용 필드), 초기 렌더에서 그대로 JSON으로 내려 이후 목록
        // 갱신은 클라이언트가 이 값을 들고 낙관적으로 처리하게 한다. 최초 페이지는 최대
        // PAGE_SIZE개만 담고, 전체 개수·다음 페이지 존재 여부를 함께 내려 "더 보기"가
        // 이어받게 한다(개선 보고서 "댓글 전체 로딩").
        model.addAttribute("commentsJson",
                JsonHtmlEmbedding.escapeForHtmlScript(objectMapper.writeValueAsString(commentPage)));

        // 게시글 읽기·편집 영역도 Vue 아일랜드(src/vue/post-edit)로 렌더링된다. 분류 선택지는
        // post-update.html이 예전에 th:each/th:if로 걸러내던 것과 같은 규칙(CategoryPolicy)을
        // 그대로 써서, "관리자가 아니면 NOTICE 숨김·이미 공지인 글은 유지" 동작이 갈라지지 않게 한다.
        List<CategoryOptionDto> categoryOptions = CategoryPolicy.availableCategoriesFor(authentication, post.category())
                .stream()
                .map(c -> new CategoryOptionDto(c.name(), c.getTitle()))
                .toList();
        boolean authenticated = OwnershipPolicy.isAuthenticated(authentication);
        model.addAttribute("postEditInitialJson", JsonHtmlEmbedding.escapeForHtmlScript(
                objectMapper.writeValueAsString(new PostEditBootstrapDto(post, categoryOptions, authenticated))));

        // 댓글 아일랜드가 "입력창을 보여줄지"를 정하는 값. 글쓰기 화면과 같은 규칙을 쓴다.
        // 한 번만 조회해 두 속성에 함께 쓴다 — 예전에는 같은 조회를 두 번 했다.
        String writeBlockReason = writeBlockReasonOf(authentication);
        model.addAttribute("canWriteComment", authenticated && writeBlockReason == null);
        model.addAttribute("writeBlockReason", writeBlockReason);

        model.addAttribute("pageTitle", post.title());
        // 검색·링크 미리보기용(F08). 대표 경로는 쿼리 없이 글 번호로만 정한다.
        model.addAttribute("pageDescription", excerpt(post.content()));
        model.addAttribute("canonicalPath", "/posts/update/" + post.id());
        model.addAttribute("ogType", "article");
        return "post/post-update";
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
