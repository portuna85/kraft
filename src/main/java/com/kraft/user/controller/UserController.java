package com.kraft.user.controller;

import com.kraft.user.domain.User;
import com.kraft.user.dto.UserJoinRequest;
import com.kraft.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import static com.kraft.user.domain.User.*;

@Controller
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @GetMapping("/join")
    public String joinForm() {
        return "user/join";
    }

    @PostMapping("/join")
    public String join(@ModelAttribute UserJoinRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("이미 존재하는 이메일입니다.");
        }

        if (userRepository.existsByNickname(request.getNickname())) {
            throw new IllegalArgumentException("이미 존재하는 닉네임입니다.");
        }

        User newUser = User.builder()
                .email(request.getEmail())
                .username(request.getUsername())
                .nickname(request.getNickname())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.USER)
                .build();

        userRepository.save(newUser);

        return "redirect:/login";
    }
}
