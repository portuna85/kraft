package com.kraft.book.web;

import com.kraft.book.config.auth.dto.SessionUser;
import com.kraft.book.service.PostsService;
import com.kraft.book.web.dto.PostsResponseDto;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@RequiredArgsConstructor
@Controller
public class IndexController {

    private final PostsService postsService;
    private final HttpSession httpSession;

    @GetMapping(value = "/", produces = "text/html; charset=UTF-8")
    public String index(Model model) {
        model.addAttribute("posts", postsService.findAllDesc());
        SessionUser user = (SessionUser) httpSession.getAttribute("user");

        if (user != null) {
            model.addAttribute("userName", user.getName());
        }
        return "index"; // returns the index.html file located in src/main/resources/templates
    }

    @GetMapping("/posts/save")
    public String postsSave() {
        return "posts-save"; // returns the posts-save.html file located in src/main/resources/templates
    }

    @GetMapping("/posts/update/{id}")
    public String postsUpdate(@PathVariable Long id, Model model) {
        PostsResponseDto dto = postsService.findById(id);
        model.addAttribute("post", dto);
        return "posts-update"; // returns the posts-update.html file located in src/main/resources/templates
    }

}
