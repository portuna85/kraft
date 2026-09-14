package com.kraft.user.web;

import com.kraft.user.service.EmailVerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@RequiredArgsConstructor
@Controller
public class UserPageController {

    private final EmailVerificationService emailVerificationService;

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
