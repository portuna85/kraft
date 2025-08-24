package com.boardly.user.service;

import com.boardly.user.domain.Role;
import com.boardly.user.domain.User;
import com.boardly.user.infra.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public Long register(String username, String rawPassword, String nickname, String email) {
        userRepository.findByUsername(username).ifPresent(u -> {
            throw new IllegalArgumentException("이미 사용 중인 아이디입니다.");
        });
        User user = User.builder()
                .username(username)
                .password(passwordEncoder.encode(rawPassword))
                .nickname(nickname)
                .email(email)
                .role(Role.USER)
                .build();
        return userRepository.save(user).getId();
    }

    public User findByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다: " + username));
    }
}
