package com.kraft.book.web;

import com.kraft.book.config.auth.LoginUser;
import com.kraft.book.config.auth.dto.SessionUser;
import com.kraft.book.service.PostsService;
import com.kraft.book.web.dto.PostsResponseDto;
import com.kraft.book.web.dto.PostsSaveRequestDto;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@RequiredArgsConstructor
@Controller
public class IndexController {

    private final PostsService postsService;
    private final HttpSession httpSession;

    @GetMapping(value = "/", produces = "text/html; charset=UTF-8")
    public String index(Model model, @LoginUser SessionUser user) {
        model.addAttribute("posts", postsService.findAllDesc());

        if (user != null) {
            model.addAttribute("userName", user.getName());
        }
        return "index"; // returns the index.html file located in src/main/resources/templates
    }

    @GetMapping("/posts/save")
    public String postsSave() {
        return "posts-save"; // returns the posts-save.html file located in src/main/resources/templates
    }

    @PostMapping("/posts/save")
    public String savePost(@Valid @ModelAttribute("post") PostsSaveRequestDto dto,
                           BindingResult bindingResult, ch.qos.logback.core.model.Model model) {
        if (bindingResult.hasErrors()) {
            return "posts-save"; // 에러 메시지와 함께 다시 폼 반환
        }
        postsService.save(dto);
        return "redirect:/";
    }

    @GetMapping("/posts/update/{id}")
    public String postsUpdate(@PathVariable Long id, Model model) {
        PostsResponseDto dto = postsService.findById(id);
        model.addAttribute("post", dto);
        return "posts-update"; // returns the posts-update.html file located in src/main/resources/templates
    }

}
