package com.kraft.user.service;

import com.kraft.user.domain.EmailMasker;
import com.kraft.user.domain.User;
import com.kraft.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * B08의 최후 수단. 회원가입 직후 {@code UserApiController}가 {@code sendVerificationEmailSafely}를
 * 호출하지만, 그 트랜잭션의 토큰 저장이 실패하거나(리포지토리 오류) <b>최종 커밋 자체</b>가
 * 실패하면 계정만 남고 인증 메일은 대기열에 한 번도 들어가지 못한다 — 메서드 안 catch로는 커밋
 * 실패를 잡을 수 없다(B02·B06과 같은 종류의 문제). 사용자가 직접 "재발송"을 누르면 복구되지만,
 * 누르지 않으면 영원히 GUEST로 남는다.
 * <p>
 * 이 주기 작업이 가입 후 유예시간이 지나도록 인증 토큰도 아웃박스 메일도 하나도 없는 GUEST
 * 계정을 찾아 {@link EmailVerificationService#sendVerificationEmail}을 대신 호출한다. 이미
 * 정상적으로 메일이 나간 계정은 대상이 아니다(토큰이나 아웃박스 행이 있으므로 조회에 걸리지
 * 않는다) — 정상 재발송(resend)의 쿨다운 정책과는 무관하다.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class GuestVerificationSweeper {

    private final UserRepository userRepository;
    private final EmailVerificationService emailVerificationService;

    @Value("${app.verification.sweep-grace-minutes:10}")
    private int graceMinutes;

    @Value("${app.verification.sweep-batch-size:50}")
    private int batchSize;

    @Scheduled(initialDelayString = "${app.verification.sweep-initial-delay-ms:600000}",
            fixedDelayString = "${app.verification.sweep-interval-ms:1800000}")
    public void sweep() {
        LocalDateTime threshold = LocalDateTime.now().minus(Duration.ofMinutes(graceMinutes));
        List<User> missing = userRepository.findGuestsMissingVerificationMail(threshold, PageRequest.of(0, batchSize));
        for (User user : missing) {
            try {
                emailVerificationService.sendVerificationEmail(user.getEmail());
                log.info("가입 직후 대기열 등록이 누락된 계정에 인증 메일을 다시 큐에 넣었습니다. userId={}", user.getId());
            } catch (RuntimeException e) {
                log.warn("누락된 인증 메일 복구에 실패했습니다. userId={}, email={}",
                        user.getId(), EmailMasker.mask(user.getEmail()), e);
            }
        }
    }
}
