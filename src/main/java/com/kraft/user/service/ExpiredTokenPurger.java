package com.kraft.user.service;

import com.kraft.user.domain.EmailVerificationTokenRepository;
import com.kraft.user.domain.PasswordResetTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 만료된 이메일 인증·비밀번호 재설정 토큰을 지운다. 별도 빈이고 {@code REQUIRES_NEW}인 이유는 트랜잭션 경계다 — 만료 토큰을
 * 지운 직후 예외를 던지는 호출자의 롤백이 삭제까지 되돌려 만료 토큰이 쌓였다.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class ExpiredTokenPurger {

    private final EmailVerificationTokenRepository tokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;

    @Value("${app.verification.purge-enabled:true}")
    private boolean enabled;

    /** 토큰 하나를 자기 트랜잭션에서 지운다(호출자의 성패와 무관하게 커밋). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void purge(Long tokenId) {
        tokenRepository.deleteById(tokenId);
    }

    /** 재설정 토큰 하나를 자기 트랜잭션에서 지운다(만료 링크를 눌렀을 때 — 뒤이은 예외가 삭제를 롤백하지 않게). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void purgePasswordResetToken(Long tokenId) {
        passwordResetTokenRepository.deleteById(tokenId);
    }

    /** 아무도 누르지 않아 남은 만료 토큰을 주기적으로 치운다({@link #purge}는 링크를 눌렀을 때만 동작한다). */
    @Scheduled(initialDelayString = "${app.verification.purge-initial-delay-ms:900000}",
            fixedDelayString = "${app.verification.purge-interval-ms:86400000}")
    @Transactional
    public void purgeExpired() {
        if (!enabled) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        int deleted = tokenRepository.deleteByExpiresAtBefore(now);
        if (deleted > 0) {
            log.info("만료된 이메일 인증 토큰 {}개를 정리했습니다.", deleted);
        }

        int deletedResets = passwordResetTokenRepository.deleteByExpiresAtBefore(now);
        if (deletedResets > 0) {
            log.info("만료된 비밀번호 재설정 토큰 {}개를 정리했습니다.", deletedResets);
        }
    }
}
