package com.kraft.user.service;

import com.kraft.shared.exception.BusinessValidationException;
import com.kraft.shared.transaction.AfterCommit;
import com.kraft.user.domain.EmailHasher;
import com.kraft.user.domain.EmailMasker;
import com.kraft.user.domain.EmailPolicy;
import com.kraft.user.domain.PasswordResetToken;
import com.kraft.user.domain.PasswordResetTokenRepository;
import com.kraft.user.domain.User;
import com.kraft.user.domain.UserRepository;
import com.kraft.user.mail.OutboxMailKind;
import com.kraft.user.mail.OutboxMailStore;
import com.kraft.user.mail.OutboxMailWorker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 비밀번호 찾기. {@link #request}는 어떤 경우에도(미가입 주소, 쿨다운에 걸린 주소) 똑같이 조용히 끝난다 — 응답이
 * 갈리면 가입 여부를 확인하는 도구(계정 열거)가 된다. 토큰은 30분짜리 1회용이며(링크가 곧 계정 접근 권한),
 * 재발급하면 옛 링크가 무효가 되고 쓴 토큰은 지운다. 재설정이 끝나면 비밀번호 변경과 같은 규칙으로 그 계정의
 * 모든 세션을 폐기한다.
 */
@Slf4j
@RequiredArgsConstructor
@Service
@Transactional(readOnly = true)
public class PasswordResetService {

    static final Duration TOKEN_TTL = Duration.ofMinutes(30);

    /** 같은 계정으로 메일을 연달아 요청하는 것을 막는다. 인증 메일 재발송과 같은 간격이다. */
    private static final Duration REQUEST_COOLDOWN = Duration.ofSeconds(60);

    private final PasswordResetTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final OutboxMailStore outboxMailStore;
    private final OutboxMailWorker outboxMailWorker;
    private final ExpiredTokenPurger expiredTokenPurger;
    /** 해시 계산을 끝낸 뒤 토큰 소비와 비밀번호 변경만 짧은 트랜잭션으로 묶는 데 쓴다. */
    private final TransactionTemplate transactionTemplate;

    /** 재설정 링크를 보낸다. 미가입 주소나 제한에 걸린 요청은 아무 일도 하지 않고 조용히 끝난다. */
    @Transactional
    public void request(String email) {
        email = EmailPolicy.normalize(email);
        User user = userRepository.findByEmailHmac(EmailHasher.hmacHex(email)).orElse(null);
        if (user == null) {
            // 가입되지 않은 주소. 로그에도 존재 여부만 남기고 주소는 가린다.
            log.info("가입되지 않은 주소로 비밀번호 재설정을 요청했습니다. email={}", EmailMasker.mask(email));
            return;
        }
        // 계정 행을 잠가 쿨다운 검사와 재발급을 직렬화한다(동시 요청이 둘 다 통과하는 경쟁 방지).
        user = userRepository.findByIdForUpdate(user.getId()).orElse(user);

        if (requestedTooRecently(user.getId())) {
            log.info("비밀번호 재설정 요청이 너무 잦아 보내지 않았습니다. userId={}", user.getId());
            return;
        }

        // 재발급하면 옛 링크는 무효가 된다.
        tokenRepository.deleteByUserId(user.getId());

        // 평문 token은 이 메서드를 벗어나지 않는다(테이블에는 해시, 평문은 발송될 때까지 outbox에만).
        String token = UUID.randomUUID().toString();
        tokenRepository.save(PasswordResetToken.builder()
                .tokenHash(EmailHasher.sha512Hex(token))
                .user(user)
                .expiresAt(LocalDateTime.now().plus(TOKEN_TTL))
                .build());

        // 토큰과 대기열 행은 같은 트랜잭션에서 커밋된다.
        outboxMailStore.enqueue(user, token, OutboxMailKind.PASSWORD_RESET);
        AfterCommit.run(outboxMailWorker::drainAsync);
    }

    /**
     * 링크의 토큰으로 새 비밀번호를 정한다. 요청 단계와 달리 실패 이유(만료·잘못된 링크)는 분명히 알려준다.
     * 소비(삭제)를 변경보다 먼저, 조건부로 해 같은 토큰이 동시에 들어와도 행 잠금으로 한 요청만 성공한다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void reset(String token, String newPassword) {
        String tokenHash = EmailHasher.sha512Hex(token);
        PasswordResetToken resetToken = tokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new BusinessValidationException("유효하지 않은 재설정 링크입니다. 다시 요청해 주세요."));

        if (resetToken.isExpired()) {
            // 같은 트랜잭션에서 지우면 아래 예외와 함께 삭제도 롤백되므로 별도 트랜잭션에서 지운다.
            expiredTokenPurger.purgePasswordResetToken(resetToken.getId());
            throw new BusinessValidationException("재설정 링크가 만료되었습니다. 다시 요청해 주세요.");
        }

        // BCrypt는 트랜잭션 밖에서 계산한다. 토큰이 유효한 요청만 이 비용을 낸다.
        String encodedPassword = userService.encodeNewPassword(newPassword);
        Long tokenId = resetToken.getId();
        Long userId = resetToken.getUser().getId();

        transactionTemplate.executeWithoutResult(status -> {
            int consumed = tokenRepository.deleteByIdAndTokenHash(tokenId, tokenHash);
            if (consumed == 0) {
                throw new BusinessValidationException("이미 사용되었거나 유효하지 않은 재설정 링크입니다. 다시 요청해 주세요.");
            }
            userService.resetPasswordEncoded(userId, encodedPassword);
        });
    }

    private boolean requestedTooRecently(Long userId) {
        return outboxMailStore.lastQueuedAt(userId, OutboxMailKind.PASSWORD_RESET)
                .map(last -> LocalDateTime.now().isBefore(last.plus(REQUEST_COOLDOWN)))
                .orElse(false);
    }
}
