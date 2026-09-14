package com.kraft.post.web;

import com.kraft.comment.dto.CommentViewDto;
import com.kraft.comment.service.CommentService;
import com.kraft.post.domain.Category;
import com.kraft.post.dto.CategoryOptionDto;
import com.kraft.post.dto.PostEditBootstrapDto;
import com.kraft.post.dto.PostsPageResponseDto;
import com.kraft.post.dto.PostViewDto;
import com.kraft.post.service.CategoryPolicy;
import com.kraft.post.service.PostService;
import com.kraft.shared.security.OwnershipPolicy;
import com.kraft.shared.web.PageWindow;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@RequiredArgsConstructor
@Controller
public class PostPageController {

    private final PostService postService;
    private final CommentService commentService;
    private final ObjectMapper objectMapper;

    @GetMapping("/")
    public String index(@PageableDefault(size = 10) Pageable pageable,
                         @RequestParam(required = false) String q,
                         @RequestParam(required = false) Category category,
                         Model model) {
        PostsPageResponseDto postsPage = postService.findAllDesc(pageable, q, category);
        model.addAttribute("posts", postsPage.content());
        model.addAttribute("postsPage", postsPage);
        model.addAttribute("pageWindow", PageWindow.of(postsPage.page(), postsPage.totalPages()));
        model.addAttribute("popularPosts", postService.findPopular(5));
        model.addAttribute("q", q);
        model.addAttribute("category", category);
        model.addAttribute("categories", Category.values());
        model.addAttribute("pageTitle", "전체 게시글");
        return "index";
    }

    @GetMapping("/posts/save")
    public String postsSave(Model model) {
        model.addAttribute("pageTitle", "글쓰기");
        return "post/post-save";
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
        List<CommentViewDto> comments = commentService.findByPostIdForView(id, authentication);
        model.addAttribute("post", post);
        model.addAttribute("comments", comments);
        // 댓글 영역은 Vue 아일랜드로 렌더링된다. canManage는 서버만 판정할 수 있으므로(공개
        // REST 응답에는 없는 화면 전용 필드), 초기 렌더에서 그대로 JSON으로 내려 이후 목록
        // 갱신은 클라이언트가 이 값을 들고 낙관적으로 처리하게 한다.
        model.addAttribute("commentsJson", objectMapper.writeValueAsString(comments));

        // 게시글 읽기·편집 영역도 Vue 아일랜드(src/vue/post-edit)로 렌더링된다. 분류 선택지는
        // post-update.html이 예전에 th:each/th:if로 걸러내던 것과 같은 규칙(CategoryPolicy)을
        // 그대로 써서, "관리자가 아니면 NOTICE 숨김·이미 공지인 글은 유지" 동작이 갈라지지 않게 한다.
        List<CategoryOptionDto> categoryOptions = CategoryPolicy.availableCategoriesFor(authentication, post.category())
                .stream()
                .map(c -> new CategoryOptionDto(c.name(), c.getTitle()))
                .toList();
        boolean authenticated = OwnershipPolicy.isAuthenticated(authentication);
        model.addAttribute("postEditInitialJson",
                objectMapper.writeValueAsString(new PostEditBootstrapDto(post, categoryOptions, authenticated)));

        model.addAttribute("pageTitle", post.title());
        return "post/post-update";
    }

}
