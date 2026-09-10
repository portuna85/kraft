package com.kraft.service.user;

import com.kraft.domain.user.EmailVerificationToken;
import com.kraft.domain.user.EmailVerificationTokenRepository;
import com.kraft.domain.user.User;
import com.kraft.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 회원가입 후 "GUEST → USER" 승격을 위한 이메일 인증 토큰 발급·검증을 담당한다.
 * 실제 이메일 발송은 {@link EmailSender}(프로파일별 구현체)에 위임한다.
 */
@Slf4j
@RequiredArgsConstructor
@Service
@Transactional(readOnly = true)
public class EmailVerificationService {

    private static final Duration TOKEN_TTL = Duration.ofHours(24);

    private final EmailVerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final EmailSender emailSender;

    @Value("${app.base-url}")
    private String baseUrl;

    @Transactional
    public void sendVerificationEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다. email=" + email));

        String token = UUID.randomUUID().toString();
        tokenRepository.save(EmailVerificationToken.builder()
                .token(token)
                .user(user)
                .expiresAt(LocalDateTime.now().plus(TOKEN_TTL))
                .build());

        String link = baseUrl + "/users/verify?token=" + token;
        emailSender.send(
                user.getEmail(),
                "[kraft] 이메일 인증을 완료해 주세요",
                "아래 링크를 클릭해 이메일 인증을 완료해 주세요:\n" + link
                        + "\n\n이 링크는 24시간 동안 유효합니다."
        );
    }

    /**
     * 회원가입 직후 호출하는 안전 버전. 메일 발송 실패(SMTP 오류 등)가 회원가입 응답 자체를
     * 실패시키지 않도록 예외를 여기서 흡수하고 로그만 남긴다 — 이미 생성된 회원 정보는 그대로
     * 유효하며, 인증 메일 재발송은 이번 범위에서는 별도로 제공하지 않는다.
     */
    public void sendVerificationEmailSafely(String email) {
        try {
            sendVerificationEmail(email);
        } catch (Exception e) {
            log.warn("인증 메일 발송에 실패했습니다. email={}", email, e);
        }
    }

    @Transactional
    public void verify(String token) {
        EmailVerificationToken verificationToken = tokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 인증 링크입니다."));

        if (verificationToken.isExpired()) {
            tokenRepository.delete(verificationToken);
            throw new IllegalArgumentException("인증 링크가 만료되었습니다. 인증 메일을 다시 요청해 주세요.");
        }

        userService.promoteToUser(verificationToken.getUser().getId());
        tokenRepository.delete(verificationToken);
    }
}
