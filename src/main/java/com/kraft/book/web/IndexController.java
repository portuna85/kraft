package com.kraft.book.web;

import com.kraft.book.config.auth.dto.SessionUser;
import com.kraft.book.service.PostsService;
import com.kraft.book.web.dto.PostsResponseDto;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class IndexController {
    private final PostsService postsService;

    public IndexController(PostsService postsService) {
        this.postsService = postsService;
    }

    @GetMapping("/")
    public String index(Model model,
                        @AuthenticationPrincipal OAuth2User principal) {
        model.addAttribute("posts", postsService.findAllDesc());
        if (principal != null) {
            model.addAttribute("userName", principal.getAttribute("name")); // 구글: name
        }
        return "index";
    }

    @GetMapping("/posts/save")
    public String postsSave() {
        return "posts-save";
    }

    @GetMapping("/posts/update/{id}")
    public String postsUpdate(@PathVariable Long id, Model model) {
        PostsResponseDto dto = postsService.findById(id);
        model.addAttribute("post", dto);

        return "posts-update";
    }
}
