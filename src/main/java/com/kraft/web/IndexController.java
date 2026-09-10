package com.kraft.web;

import com.kraft.service.comment.CommentService;
import com.kraft.service.post.PostService;
import com.kraft.web.dto.post.PostsPageResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@RequiredArgsConstructor
@Controller
public class IndexController {

    private final PostService postService;
    private final CommentService commentService;

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

    @GetMapping("/users/me/password")
    public String changePassword() {
        return "user/change-password";
    }
}
