package com.kraft.web.api;

import com.kraft.config.auth.LoginUser;
import com.kraft.config.auth.dto.SessionUser;
import com.kraft.service.AuthService;
import com.kraft.service.UserService;
import com.kraft.web.dto.user.*;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/users")
public class UserApiController {

    private final UserService userService;
    private final AuthService authService;
    private final HttpSession httpSession;

    @PostMapping("/signup")
    public ResponseEntity<SignupResponseDto> signup(@RequestBody @Valid SignupRequestDto requestDto) {
        Long userId = userService.register(requestDto);
        SignupResponseDto response = SignupResponseDto.of(userId, requestDto.getName(), requestDto.getEmail());

        log.info("회원가입 API 호출 성공: userId={}", userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDto> login(@RequestBody @Valid LoginRequestDto requestDto) {
        SessionUser sessionUser = authService.login(requestDto);
        httpSession.setAttribute("user", sessionUser);

        log.info("로그인 API 호출 성공: userId={}", sessionUser.id());
        return ResponseEntity.ok(LoginResponseDto.from(sessionUser));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        httpSession.invalidate();
        log.info("로그아웃 API 호출 성공");
        return ResponseEntity.ok().build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponseDto> getProfile(@LoginUser SessionUser sessionUser) {
        UserProfileResponseDto profile = userService.getProfile(sessionUser.id());
        return ResponseEntity.ok(profile);
    }

    @PatchMapping("/me/email")
    public ResponseEntity<UserProfileResponseDto> updateEmail(
            @LoginUser SessionUser sessionUser,
            @RequestBody @Valid UserProfileUpdateRequestDto requestDto
    ) {
        UserProfileResponseDto updated = userService.updateEmail(sessionUser.id(), requestDto.getEmail());
        return ResponseEntity.ok(updated);
    }

    @PatchMapping("/me/password")
    public ResponseEntity<Void> changePassword(
            @LoginUser SessionUser sessionUser,
            @RequestBody @Valid UserPasswordChangeRequestDto requestDto
    ) {
        userService.changePassword(sessionUser.id(), requestDto.getCurrentPassword(), requestDto.getNewPassword());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteAccount(@LoginUser SessionUser sessionUser) {
        userService.delete(sessionUser.id());
        httpSession.invalidate();
        return ResponseEntity.noContent().build();
    }
}
