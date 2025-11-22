package com.kraft.service;

import com.kraft.config.auth.dto.SessionUser;
import com.kraft.domain.user.User;
import com.kraft.web.dto.user.LoginRequestDto;
import com.kraft.common.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;

    public SessionUser login(LoginRequestDto requestDto) {
        User user = userService.findByName(requestDto.getName());

        if (!passwordEncoder.matches(requestDto.getPassword(), user.getPassword())) {
            log.warn("로그인 실패: 잘못된 비밀번호 - name={}", requestDto.getName());
            throw UnauthorizedException.invalidCredentials();
        }

        SessionUser sessionUser = new SessionUser(user);
        log.info("로그인 성공: userId={}, name={}", sessionUser.id(), sessionUser.name());
        return sessionUser;
    }

    public SessionUser login(String name, String rawPassword) {
        User user = userService.findByName(name);

        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            log.warn("로그인 실패: 잘못된 비밀번호 - name={}", name);
            throw UnauthorizedException.invalidCredentials();
        }

        SessionUser sessionUser = new SessionUser(user);
        log.info("로그인 성공: userId={}, name={}", sessionUser.id(), sessionUser.name());
        return sessionUser;
    }
}

