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
 * 만료된 이메일 인증 토큰과 비밀번호 재설정 토큰을 지운다.
 * <p>
 * 별도 빈으로 뺀 이유는 <b>트랜잭션 경계 때문</b>이다. 예전에는 {@code verify()}가 만료 토큰을
 * 지운 직후 {@code IllegalArgumentException}을 던졌는데, 쓰기 트랜잭션에서 런타임 예외가
 * 나가면 Spring의 기본 롤백 규칙에 따라 그 삭제까지 함께 롤백된다. 그래서 "만료된 토큰은
 * 지운다"는 정책이 실제로는 한 번도 지켜지지 않았고 만료 토큰이 계속 쌓였다
 * (개선 보고서 F08). 기존 단위 테스트는 {@code delete()} 호출 여부만 봤기 때문에 이것을 놓쳤다.
 * <p>
 * {@code REQUIRES_NEW}로 자기 트랜잭션을 열어 먼저 커밋하므로, 호출한 쪽이 이어서 예외를
 * 던져 롤백되더라도 삭제는 남는다.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class ExpiredTokenPurger {

    private final EmailVerificationTokenRepository tokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;

    @Value("${app.verification.purge-enabled:true}")
    private boolean enabled;

    /**
     * 토큰 하나를 자기 트랜잭션에서 지운다. 호출한 트랜잭션의 성패와 무관하게 커밋된다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void purge(Long tokenId) {
        tokenRepository.deleteById(tokenId);
    }

    /**
     * 비밀번호 재설정 토큰 하나를 자기 트랜잭션에서 지운다. 만료된 링크를 눌렀을 때 호출하는데,
     * 그 뒤에 "만료되었습니다" 예외가 이어지므로 같은 트랜잭션에서 지우면 F08과 똑같이 삭제가
     * 함께 롤백된다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void purgePasswordResetToken(Long tokenId) {
        passwordResetTokenRepository.deleteById(tokenId);
    }

    /**
     * 아무도 인증 링크를 누르지 않아 그대로 남은 만료 토큰을 주기적으로 치운다.
     * {@link #purge}는 "만료된 링크를 눌렀을 때"만 동작하므로, 이 배치가 없으면 방치된 토큰이
     * 계속 쌓인다.
     */
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

        // 재설정 링크도 아무도 누르지 않으면 그대로 남는다. 30분짜리라 더 빨리 쌓인다.
        int deletedResets = passwordResetTokenRepository.deleteByExpiresAtBefore(now);
        if (deletedResets > 0) {
            log.info("만료된 비밀번호 재설정 토큰 {}개를 정리했습니다.", deletedResets);
        }
    }
}
