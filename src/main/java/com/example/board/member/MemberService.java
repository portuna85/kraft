package com.example.board.member;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MemberService {
    private final MemberRepository repo;
    private final PasswordEncoder encoder;

    @Transactional
    public void register(String email, String username, String rawPassword) {
        if (email == null || email.isBlank()
                || username == null || username.isBlank()
                || rawPassword == null || rawPassword.isBlank()) {
            throw new IllegalArgumentException("필수 값이 비었습니다.");
        }

        if (repo.existsByEmail(email)) {
            throw new DuplicateEmailException(email);
        }

        Member m = new Member();
        m.setEmail(email);
        m.setUsername(username);
        m.setPasswordHash(encoder.encode(rawPassword));
        m.setRole(Role.USER);
        repo.save(m);
    }
}