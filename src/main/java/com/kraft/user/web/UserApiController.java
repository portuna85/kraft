package com.kraft.user.web;

import com.kraft.user.dto.ChangePasswordRequestDto;
import com.kraft.user.dto.SignUpRequestDto;
import com.kraft.user.service.EmailVerificationService;
import com.kraft.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
public class UserApiController {

    private final UserService userService;
    private final EmailVerificationService emailVerificationService;

    @PostMapping("/api/v1/users")
    public Long signUp(@Valid @RequestBody SignUpRequestDto requestDto) {
        Long id = userService.signUp(requestDto.name(), requestDto.email(), requestDto.password());
        emailVerificationService.sendVerificationEmailSafely(requestDto.email());
        return id;
    }

    @PutMapping("/api/v1/users/me/password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequestDto requestDto,
                                                Authentication authentication) {
        userService.changePassword(authentication.getName(), requestDto.currentPassword(), requestDto.newPassword());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/v1/users/me/verify-email/resend")
    public ResponseEntity<Void> resendVerificationEmail(Authentication authentication) {
        emailVerificationService.resend(authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
