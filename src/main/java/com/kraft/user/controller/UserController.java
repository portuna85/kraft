package com.kraft.user.controller;

import com.kraft.user.domain.User;
import com.kraft.user.dto.UserJoinRequest;
import com.kraft.user.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import static com.kraft.user.domain.User.*;

@Controller
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * 회원가입 폼 - DTO 바인딩 및 초기값 설정
     */
    @GetMapping("/join")
    public String joinForm(Model model) {
        model.addAttribute("userJoinRequest", new UserJoinRequest());
        return "user/join";
    }

    /**
     * 회원가입 처리 - 유효성 검사 포함
     */
    @PostMapping("/join")
    public String join(@Valid @ModelAttribute("userJoinRequest") UserJoinRequest request,
                       BindingResult bindingResult,
                       Model model) {

        // 유효성 검사 실패 시 다시 폼으로
        if (bindingResult.hasErrors()) {
            return "user/join";
        }

        // 이메일 중복 체크
        if (userRepository.existsByEmail(request.getEmail())) {
            bindingResult.rejectValue("email", "duplicate", "이미 사용 중인 이메일입니다.");
            return "user/join";
        }

        // 닉네임 중복 체크
        if (userRepository.existsByNickname(request.getNickname())) {
            bindingResult.rejectValue("nickname", "duplicate", "이미 사용 중인 닉네임입니다.");
            return "user/join";
        }

        // 회원 정보 저장
        User newUser = User.builder()
                .email(request.getEmail())
                .username(request.getUsername())
                .nickname(request.getNickname())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.USER)
                .build();

        userRepository.save(newUser);

        return "redirect:/login?success"; // 로그인 페이지로 리다이렉트 + 성공 메시지
    }
}
