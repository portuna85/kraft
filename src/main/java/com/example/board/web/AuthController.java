package com.example.board.web;

import com.example.board.member.MemberService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class AuthController {
    private final MemberService memberService;

    public AuthController(MemberService memberService) {
        this.memberService = memberService;
    }

    public static class SignupForm {
        @Email @NotBlank public String email;
        @NotBlank @Size(min = 2, max = 20) public String username;
        @NotBlank @Size(min = 6, max = 50) public String password;
    }

    @GetMapping("/login")
    public String loginPage() { return "login"; }

    @GetMapping("/signup")
    public String signupPage(Model model) {
        model.addAttribute("form", new SignupForm());
        return "signup";
    }

    @PostMapping("/signup")
    public String signup(@Valid @ModelAttribute("form") SignupForm form,  // ★ @Valid 추가
                         BindingResult binding,                           // ★ BindingResult 추가
                         Model model) {
        // 1) 폼 검증 실패 시 즉시 반환 (DB 조회 금지)
        if (binding.hasErrors()) {
            return "signup";
        }

        try {
            memberService.register(form.email, form.username, form.password);
            return "redirect:/login?registered";
        } catch (com.example.board.member.DuplicateEmailException e) {
            // 2) 중복 이메일이면 필드 에러로 반환
            binding.rejectValue("email", "duplicate", "이미 사용 중인 이메일입니다.");
            return "signup";
        } catch (IllegalArgumentException e) {
            // 3) 서비스 쪽 가드에 걸린 경우
            model.addAttribute("error", e.getMessage());
            return "signup";
        } catch (Exception e) {
            model.addAttribute("error", "회원가입에 실패했습니다.");
            return "signup";
        }
    }
}
