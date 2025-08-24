package com.boardly.post.web.view;


import com.boardly.post.service.PostService;
import com.boardly.post.web.dto.PostCreateForm;
import com.boardly.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
@RequiredArgsConstructor
public class PostViewController {
    private final PostService postService;
    private final UserService userService;

    @GetMapping("/posts/new")
    public String createForm(Model model){
        model.addAttribute("form", new PostCreateForm(null,null));
        return "posts/new";
    }

    @PostMapping("/posts")
    public String create(@AuthenticationPrincipal(expression="username") String username,
                         @ModelAttribute("form") @Valid PostCreateForm form,
                         BindingResult bindingResult){
        if (bindingResult.hasErrors()) return "posts/new";
        var user = userService.findByUsername(username);
        Long id = postService.create(user, form.title(), form.content());
        return "redirect:/posts/%d?success=작성되었습니다".formatted(id);
    }
}