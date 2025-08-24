package com.boardly.user.web.view;

import com.boardly.user.service.UserService;
import com.boardly.user.web.dto.SignUpForm;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
@RequiredArgsConstructor
public class UserViewController {
    private final UserService userService;

    @GetMapping("/signup")
    public String signUpForm(Model model){
        model.addAttribute("form", new SignUpForm(null,null,null,null));
        return "signup";
    }

    @PostMapping("/signup")
    public String signUpSubmit(@ModelAttribute("form") @Valid SignUpForm form,
                               BindingResult bindingResult){
        if (bindingResult.hasErrors()) return "signup";
        userService.register(form.username(), form.password(), form.nickname(), form.email());
        return "redirect:/login?success=가입이 완료되었습니다";
    }
}