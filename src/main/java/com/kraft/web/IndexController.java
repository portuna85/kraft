package com.kraft.web;

import com.kraft.domain.post.Category;
import com.kraft.service.comment.CommentService;
import com.kraft.service.post.PostService;
import com.kraft.service.user.EmailVerificationService;
import com.kraft.web.dto.post.PostsPageResponseDto;
import com.kraft.web.support.PageWindow;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@RequiredArgsConstructor
@Controller
public class IndexController {

    private final PostService postService;
    private final CommentService commentService;
    private final EmailVerificationService emailVerificationService;

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
        model.addAttribute("post", postService.findByIdForView(id, authentication));
        model.addAttribute("comments", commentService.findByPostIdForView(id, authentication));
        model.addAttribute("pageTitle", "게시글 읽기");
        return "post/post-update";
    }

    @GetMapping("/signup")
    public String signup(Model model) {
        model.addAttribute("pageTitle", "회원가입");
        return "user/signup";
    }

    @GetMapping("/login")
    public String login(Model model) {
        model.addAttribute("pageTitle", "로그인");
        return "user/login";
    }

    /**
     * 이메일로 발송된 인증 링크를 클릭했을 때 호출된다. AJAX가 아니라 브라우저가 직접 이동하는
     * 링크이므로 JSON이 아니라 결과 화면을 렌더링한다.
     */
    @GetMapping("/users/verify")
    public String verifyEmail(@RequestParam String token, Model model) {
        try {
            emailVerificationService.verify(token);
            model.addAttribute("success", true);
        } catch (IllegalArgumentException e) {
            model.addAttribute("success", false);
            model.addAttribute("message", e.getMessage());
        }
        model.addAttribute("pageTitle", "이메일 인증");
        return "user/verify-result";
    }
}
