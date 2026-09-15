package com.kraft.user.web;

import com.kraft.user.dto.ChangePasswordRequestDto;
import com.kraft.user.dto.PasswordResetConfirmDto;
import com.kraft.user.dto.PasswordResetRequestDto;
import com.kraft.user.dto.SignUpRequestDto;
import com.kraft.user.dto.WithdrawRequestDto;
import com.kraft.user.service.EmailVerificationService;
import com.kraft.user.service.PasswordResetService;
import com.kraft.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
public class UserApiController {

    private final UserService userService;
    private final EmailVerificationService emailVerificationService;
    private final PasswordResetService passwordResetService;

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

    /**
     * 회원 탈퇴. 글과 댓글은 남고 작성자만 익명이 된다({@code UserService.withdraw}).
     * 성공하면 이 계정의 모든 세션이 폐기된다.
     */
    @DeleteMapping("/api/v1/users/me")
    public ResponseEntity<Void> withdraw(@Valid @RequestBody WithdrawRequestDto requestDto,
                                          Authentication authentication) {
        userService.withdraw(authentication.getName(), requestDto.currentPassword());
        return ResponseEntity.noContent().build();
    }

    /**
     * 비밀번호 재설정 링크를 요청한다. 가입되지 않은 주소든 요청 제한에 걸렸든 <b>항상 204</b>다 —
     * 응답이 갈리면 그것만으로 가입 여부를 확인할 수 있게 된다({@code PasswordResetService}).
     */
    @PostMapping("/api/v1/users/password-reset")
    public ResponseEntity<Void> requestPasswordReset(@Valid @RequestBody PasswordResetRequestDto requestDto) {
        passwordResetService.request(requestDto.email());
        return ResponseEntity.noContent().build();
    }

    /** 메일로 받은 토큰으로 새 비밀번호를 정한다. 성공하면 이 계정의 모든 세션이 폐기된다. */
    @PostMapping("/api/v1/users/password-reset/confirm")
    public ResponseEntity<Void> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmDto requestDto) {
        passwordResetService.reset(requestDto.token(), requestDto.newPassword());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/v1/users/me/verify-email/resend")
    public ResponseEntity<Void> resendVerificationEmail(Authentication authentication) {
        emailVerificationService.resend(authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
