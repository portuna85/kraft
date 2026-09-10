package com.kraft.web;

import com.kraft.service.comment.CommentService;
import com.kraft.service.post.PostService;
import com.kraft.service.user.EmailVerificationService;
import com.kraft.web.dto.post.PostsPageResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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
    public String index(@PageableDefault(size = 10) Pageable pageable, Model model) {
        PostsPageResponseDto postsPage = postService.findAllDesc(pageable);
        model.addAttribute("posts", postsPage.content());
        model.addAttribute("postsPage", postsPage);
        return "index";
    }

    @GetMapping("/posts/save")
    public String postsSave() {
        return "post/post-save";
    }

    @GetMapping("/posts/update/{id}")
    public String postsUpdate(@PathVariable Long id, Model model) {
        model.addAttribute("post", postService.findById(id));
        model.addAttribute("comments", commentService.findByPostId(id));
        return "post/post-update";
    }

    @GetMapping("/signup")
    public String signup() {
        return "user/signup";
    }

    @GetMapping("/login")
    public String login() {
        return "user/login";
    }

    @GetMapping("/users/me/password")
    public String changePassword() {
        return "user/change-password";
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
        return "user/verify-result";
    }
}
