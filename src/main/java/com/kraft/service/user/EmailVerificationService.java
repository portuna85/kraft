package com.kraft.service.user;

import com.kraft.domain.user.EmailHasher;
import com.kraft.domain.user.EmailVerificationToken;
import com.kraft.domain.user.EmailVerificationTokenRepository;
import com.kraft.domain.user.Role;
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
        User user = userRepository.findByEmailHash(EmailHasher.sha512Hex(email))
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
     * 유효하며, 인증 메일이 도착하지 않았다면 {@link #resend}로 다시 요청할 수 있다.
     * <p>
     * {@code @Transactional}을 명시하지 않으면 클래스 레벨의 {@code readOnly = true}를 그대로
     * 물려받는다. 이 메서드가 내부에서 {@code sendVerificationEmail(email)}을 호출하는 것은
     * self-invocation이라 Spring 프록시를 거치지 않으므로, 그 메서드에 붙은
     * {@code @Transactional}(쓰기 가능)이 무시되고 바깥의 읽기 전용 트랜잭션이 그대로 적용된다.
     * H2는 읽기 전용 트랜잭션에서도 INSERT를 관대하게 허용해 로컬에서는 드러나지 않았지만,
     * MariaDB는 엄격히 거부한다(Docker 검증 중 실제로 재현). 여기 {@code @Transactional}을 명시해
     * 바깥 트랜잭션 자체를 쓰기 가능하게 만들어 해결한다.
     */
    @Transactional
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
            throw new IllegalArgumentException("인증 링크가 만료되었습니다. 다시 요청해 주세요.");
        }

        userService.promoteToUser(verificationToken.getUser().getId());
        tokenRepository.delete(verificationToken);
    }

    /**
     * 사용자가 명시적으로 재발송을 요청했을 때 호출한다. 회원가입 직후의
     * {@link #sendVerificationEmailSafely}와 달리 메일 발송 실패를 흡수하지 않고 그대로
     * 전파한다 — 사용자가 결과를 기대하고 누른 버튼이므로 실패를 조용히 감추면 안 된다.
     * 이미 인증된(Role이 GUEST가 아닌) 계정은 재발송 대상이 아니므로 거부한다. 재발송 전
     * 기존 토큰을 지워, 같은 사용자에 대해 유효한 토큰이 여러 개 동시에 쌓이지 않게 한다.
     */
    @Transactional
    public void resend(String email) {
        User user = userRepository.findByEmailHash(EmailHasher.sha512Hex(email))
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다. email=" + email));

        if (user.getRole() != Role.GUEST) {
            throw new IllegalArgumentException("이미 인증된 계정입니다.");
        }

        tokenRepository.deleteByUserId(user.getId());
        sendVerificationEmail(email);
    }
}
