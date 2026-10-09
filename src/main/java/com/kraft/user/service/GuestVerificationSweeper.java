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
 * 가입 직후 인증 메일 대기열 등록이 누락된 계정의 최후 수단. {@code sendVerificationEmailSafely}가 토큰 저장 실패나 최종 커밋
 * 실패를 잡지 못하면 계정만 남고 메일이 한 번도 큐에 들어가지 못한다. 이 주기 작업이 유예시간이 지나도 인증 토큰도 아웃박스
 * 메일도 없는 GUEST를 찾아 {@link EmailVerificationService#sendVerificationEmail}을 대신 부른다(정상 재발송의 쿨다운과는
 * 무관하다).
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class GuestVerificationSweeper {

    private final UserRepository userRepository;
    private final EmailVerificationService emailVerificationService;

    /** rekey(이메일 키 교체) 중에는 꺼야 한다 — 아직 변환되지 않은(옛 키) 행을 엔티티로 읽는 순간 복호화가 실패한다({@code BackupRestoreRehearsalTest}가 실증). */
    @Value("${app.verification.sweep-enabled:true}")
    private boolean enabled;

    @Value("${app.verification.sweep-grace-minutes:10}")
    private int graceMinutes;

    @Value("${app.verification.sweep-batch-size:50}")
    private int batchSize;

    @Scheduled(initialDelayString = "${app.verification.sweep-initial-delay-ms:600000}",
            fixedDelayString = "${app.verification.sweep-interval-ms:1800000}")
    public void sweep() {
        if (!enabled) {
            return;
        }
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
