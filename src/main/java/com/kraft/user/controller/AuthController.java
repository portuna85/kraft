package com.kraft.user.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AuthController {

    /**
     * 로그인 폼 화면
     * error, logout, success 파라미터로 메시지를 표시함
     */
    @GetMapping("/login")
    public String loginPage(
            @RequestParam(value = "error", required = false) String error,
            @RequestParam(value = "logout", required = false) String logout,
            @RequestParam(value = "success", required = false) String success,
            Model model) {

        if (error != null) {
            model.addAttribute("errorMessage", "이메일 또는 비밀번호가 올바르지 않습니다.");
        }

        if (logout != null) {
            model.addAttribute("logoutMessage", "정상적으로 로그아웃되었습니다.");
        }

        if (success != null) {
            model.addAttribute("successMessage", "회원가입이 완료되었습니다. 로그인해주세요.");
        }

        return "user/login"; // templates/user/login.html
    }
}
