package com.kraft.user.controller;

import com.kraft.user.domain.User;
import com.kraft.user.dto.JoinRequest;
import com.kraft.user.dto.UserJoinRequest;
import com.kraft.user.repository.UserRepository;
import com.kraft.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import static com.kraft.user.domain.User.*;

@Controller
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @GetMapping("/join")
    public String joinForm() {
        return "user/join"; // templates/user/join.html
    }

    @PostMapping("/join")
    public String join(@ModelAttribute JoinRequest request) {
        userService.join(request);
        return "redirect:/login"; // 회원가입 성공 후 로그인 페이지로 이동
    }

    /**
     * 회원가입 폼 화면 (GET)
     */
    @GetMapping("/signup")
    public String showSignupForm() {
        return "join"; // templates/join.html
    }

    /**
     * 회원가입 처리 (POST)
     */
    @PostMapping("/signup")
    public String processSignup(@ModelAttribute UserJoinRequest request) {
        // 이메일, 닉네임 중복 검사 (선택)
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("이미 존재하는 이메일입니다.");
        }

        if (userRepository.existsByNickname(request.getNickname())) {
            throw new IllegalArgumentException("이미 존재하는 닉네임입니다.");
        }

        // User 엔티티 생성 및 저장
        User newUser = builder()
                .email(request.getEmail())
                .username(request.getUsername())
                .nickname(request.getNickname())
                .password(passwordEncoder.encode(request.getPassword()))  // 비밀번호 암호화
                .role(Role.USER)  // 기본 권한은 일반 사용자
                .build();

        userRepository.save(newUser);

        return "redirect:/login"; // 회원가입 후 로그인 페이지로 이동
    }
}
