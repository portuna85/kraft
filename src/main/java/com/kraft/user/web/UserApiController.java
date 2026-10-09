package com.kraft.user.web;

import com.kraft.shared.security.CurrentUser;
import com.kraft.shared.web.ResponseTimeFloor;
import com.kraft.user.domain.UserRepository;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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
    private final UserRepository userRepository;

    /** 가입·재설정 요청의 응답 시간 하한 — 가입된 주소와 아닌 주소의 처리 시간 격차를 가린다. 생성자 시그니처(테스트가 직접 만든다)를 바꾸지 않으려 필드 주입이다. */
    @Value("${app.auth.min-response-millis:250}")
    private long minResponseMillis;

    /** 로그인 사용자 대상 서비스는 이메일이 아니라 세션 principal의 불변 회원 id를 받는다. {@code CurrentUser.require}가 탈퇴 여부도 거른다. */
    private Long currentUserId(Authentication authentication) {
        return CurrentUser.require(authentication, userRepository).getId();
    }

    /** 이미 가입된 이메일이어도 신규 가입과 같은 응답(200)을 준다(계정 열거 방지). 새 계정에만 인증 메일을 보내고, 기존 계정에는 {@code UserService.signUp}이 안내 메일을 큐에 넣는다. */
    @PostMapping("/api/v1/users")
    public ResponseEntity<Void> signUp(@Valid @RequestBody SignUpRequestDto requestDto) {
        long start = System.nanoTime();
        boolean created = userService.signUp(requestDto.name(), requestDto.email(), requestDto.password());
        if (created) {
            emailVerificationService.sendVerificationEmailSafely(requestDto.email());
        }
        ResponseTimeFloor.await(start, minResponseMillis);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/api/v1/users/me/password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequestDto requestDto,
                                                Authentication authentication) {
        userService.changePassword(currentUserId(authentication), requestDto.currentPassword(), requestDto.newPassword());
        return ResponseEntity.noContent().build();
    }

    /** 회원 탈퇴. 글과 댓글은 남고 작성자만 익명이 되며({@code UserService.withdraw}), 이 계정의 모든 세션이 폐기된다. */
    @DeleteMapping("/api/v1/users/me")
    public ResponseEntity<Void> withdraw(@Valid @RequestBody WithdrawRequestDto requestDto,
                                          Authentication authentication) {
        userService.withdraw(currentUserId(authentication), requestDto.currentPassword());
        return ResponseEntity.noContent().build();
    }

    /** 비밀번호 재설정 링크를 요청한다. 미가입이든 제한에 걸렸든 항상 204다(응답이 갈리면 가입 여부가 드러난다). */
    @PostMapping("/api/v1/users/password-reset")
    public ResponseEntity<Void> requestPasswordReset(@Valid @RequestBody PasswordResetRequestDto requestDto) {
        long start = System.nanoTime();
        passwordResetService.request(requestDto.email());
        ResponseTimeFloor.await(start, minResponseMillis);
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
        emailVerificationService.resend(currentUserId(authentication));
        return ResponseEntity.noContent().build();
    }

    /** 세션 연장용 — 인증된 요청이면 Spring Session이 만료 시각을 미룬다. 오래 열린 글쓰기·편집 화면이 주기적으로 부르며, 상태 변경도 DB 조회도 없다. */
    @GetMapping("/api/v1/users/me/ping")
    public ResponseEntity<Void> ping() {
        return ResponseEntity.noContent().build();
    }
}
