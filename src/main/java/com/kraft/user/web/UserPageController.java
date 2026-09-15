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

    /** 비밀번호 재설정 링크를 요청하는 화면. 로그인 화면에서 들어온다. */
    @GetMapping("/forgot-password")
    public String forgotPassword(Model model) {
        model.addAttribute("pageTitle", "비밀번호 찾기");
        return "user/forgot-password";
    }

    /**
     * 메일의 링크가 여는 화면. 토큰은 여기서 검사하지 않는다 — 화면을 그리는 것만으로 토큰을
     * 쓴 셈이 되면 안 되고(메일 미리보기·링크 검사기가 대신 눌러 버린다), 판정은 새 비밀번호와
     * 함께 오는 저장 요청에서 한 번만 한다.
     */
    @GetMapping("/users/password-reset")
    public String passwordReset(@RequestParam String token, Model model) {
        model.addAttribute("resetToken", token);
        model.addAttribute("pageTitle", "비밀번호 재설정");
        return "user/password-reset";
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
