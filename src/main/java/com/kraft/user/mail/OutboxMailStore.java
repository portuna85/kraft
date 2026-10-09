package com.kraft.user.mail;

import com.kraft.shared.transaction.StuckRequeue;
import com.kraft.shared.web.FixedWindowRateLimiter;
import com.kraft.user.domain.EmailHasher;
import com.kraft.user.domain.EmailVerificationTokenRepository;
import com.kraft.user.domain.PasswordResetTokenRepository;
import com.kraft.user.domain.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 아웃박스의 DB 쪽만 담당한다(SMTP 호출 없음). "집기 → 보내기 → 결과 적기" 중 가운데만 트랜잭션 밖이어야
 * 하므로 앞뒤 단계는 각자 독립된 짧은 트랜잭션({@code REQUIRES_NEW})이다. 작업자가 큰 트랜잭션으로 감싸면
 * 분리한 의미가 없어진다.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class OutboxMailStore {

    private final OutboxMailRepository outboxMailRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;

    @Value("${app.mail.max-attempts:5}")
    private int maxAttempts;

    /** 같은 회원에게 같은 종류의 안내 메일을 다시 큐에 넣기까지의 최소 간격. */
    static final Duration NOTICE_COOLDOWN = Duration.ofHours(1);

    /** 종류별 전체 시간당 안내 메일 상한 — 분산 IP 공격에도 SMTP 쿼터를 지킨다. */
    private static final int NOTICE_BUDGET_PER_HOUR = 200;

    private final FixedWindowRateLimiter noticeBudget =
            new FixedWindowRateLimiter("notice-mail-budget", NOTICE_BUDGET_PER_HOUR, Duration.ofHours(1).toMillis());

    /** 메일을 대기열에 넣는다. 호출자의 트랜잭션에 참여한다 — 토큰 저장과 함께 커밋되거나 사라져야 한다. */
    @Transactional
    public void enqueue(User user, String token, OutboxMailKind kind) {
        outboxMailRepository.save(OutboxMail.builder().user(user).token(token).kind(kind).build());
    }

    /**
     * 보낼 것들을 집어 SENDING으로 바꾸고 id를 돌려준다. {@code FOR UPDATE SKIP LOCKED}로 잠근 같은
     * 트랜잭션에서 UPDATE하므로 동시에 돌아도 같은 행을 두 번 집지 않는다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<Long> claimBatch(int batchSize, String ownerToken) {
        LocalDateTime now = LocalDateTime.now();
        List<Long> ids = outboxMailRepository.selectPendingIdsForUpdateSkipLocked(now, batchSize);
        if (ids.isEmpty()) {
            return ids;
        }
        outboxMailRepository.markSendingByIds(ids, now, ownerToken);
        return ids;
    }

    /**
     * 발송에 필요한 값만 평범한 값으로 꺼낸다(SMTP 때 영속성 컨텍스트·커넥션이 필요 없게). 토큰이 이미
     * 재발급·만료됐으면 옛 링크라 재시도 없이 FAILED로 남긴다. {@code ownerToken}이 지금도 소유자일 때만
     * 꺼내며, 재큐잉({@link #requeueStuck}) 뒤 늦게 온 이전 워커는 빈 값을 받는다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<PendingMail> load(Long id, String ownerToken) {
        return outboxMailRepository.findByIdAndOwnerTokenAndStatus(id, ownerToken, OutboxMailStatus.SENDING)
                .flatMap(mail -> {
                    if (!isTokenStillValid(mail)) {
                        mail.markStale("토큰이 재발급되었거나 만료되어 더 이상 유효하지 않습니다.");
                        log.info("옛 토큰의 아웃박스 메일을 건너뛰었습니다. outboxMailId={}", mail.getId());
                        return Optional.empty();
                    }
                    return Optional.of(new PendingMail(
                            mail.getId(), mail.getUser().getEmail(), mail.getToken(), mail.getKind(), ownerToken));
                });
    }

    private boolean isTokenStillValid(OutboxMail mail) {
        // 토큰이 없는 종류(로그인 시도 경고 등)는 만료 개념이 없다.
        if (mail.getToken() == null) {
            return true;
        }
        // outbox의 토큰은 평문이고 조회 테이블은 해시라 같은 함수로 변환해 비교한다.
        String tokenHash = EmailHasher.sha512Hex(mail.getToken());
        return switch (mail.getKind()) {
            case VERIFY_EMAIL -> emailVerificationTokenRepository.findByTokenHash(tokenHash)
                    .filter(token -> !token.isExpired()).isPresent();
            case PASSWORD_RESET -> passwordResetTokenRepository.findByTokenHash(tokenHash)
                    .filter(token -> !token.isExpired()).isPresent();
            case LOGIN_ATTEMPTS_WARNING, ACCOUNT_EXISTS -> true;
        };
    }

    /** {@code ownerToken}과 SENDING이 유지될 때만 반영한다 — 재선점·재큐잉된 행의 결과를 덮지 않는다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSent(Long id, String ownerToken) {
        outboxMailRepository.findSendingByIdAndOwnerTokenForUpdate(id, ownerToken).ifPresentOrElse(
                OutboxMail::markSent,
                () -> log.info("이미 다른 워커가 재선점했거나 상태가 바뀐 메일이라 발송 성공을 반영하지 않습니다. outboxMailId={}", id));
    }

    /** {@link #markSent}와 같은 조건으로 실패를 반영한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long id, String error, String ownerToken) {
        outboxMailRepository.findSendingByIdAndOwnerTokenForUpdate(id, ownerToken).ifPresentOrElse(
                mail -> mail.markFailed(error, maxAttempts),
                () -> log.info("이미 다른 워커가 재선점했거나 상태가 바뀐 메일이라 발송 실패를 반영하지 않습니다. outboxMailId={}", id));
    }

    /**
     * 발송 도중 죽어 SENDING으로 남은 오래된 메일을 재시도 대상으로 되돌린다. {@code ownerToken}도 비워,
     * 뒤늦게 살아난 원래 워커의 결과가 새 소유자의 처리를 덮지 못하게 한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int requeueStuck(LocalDateTime threshold) {
        return StuckRequeue.run(
                page -> outboxMailRepository.findByStatusAndUpdatedAtBefore(OutboxMailStatus.SENDING, threshold, page),
                mail -> {
                    mail.markFailed("발송 도중 중단되어 다시 대기열에 넣었습니다.", maxAttempts);
                    mail.releaseOwnership();
                });
    }

    /** 이 회원에게 이 종류의 메일을 마지막으로 만든 시각. 요청 제한에 쓴다. */
    @Transactional(readOnly = true)
    public Optional<LocalDateTime> lastQueuedAt(Long userId, OutboxMailKind kind) {
        return outboxMailRepository.findFirstByUserIdAndKindOrderByIdDesc(userId, kind).map(OutboxMail::getCreatedAt);
    }

    /**
     * 수신자가 유발하지 않은 안내 메일(계정 존재·로그인 시도 경고)을 쿨다운과 시간당 예산 안에서만 넣는다
     * (남의 메일함 폭격·SMTP 쿼터 소진 방지). 호출자의 응답은 큐잉 여부와 무관하게 같아야 한다.
     *
     * @return 실제로 큐에 넣었으면 {@code true}
     */
    @Transactional
    public boolean enqueueNotice(User user, OutboxMailKind kind) {
        if (outboxMailRepository.findFirstByUserIdAndKindOrderByIdDesc(user.getId(), kind)
                .map(OutboxMail::getCreatedAt)
                .filter(last -> LocalDateTime.now().isBefore(last.plus(NOTICE_COOLDOWN)))
                .isPresent()) {
            return false;
        }
        if (!noticeBudget.tryAcquire(kind.name())) {
            log.warn("안내 메일 시간당 예산을 넘어 큐잉하지 않았습니다. kind={}", kind);
            return false;
        }
        enqueue(user, null, kind);
        return true;
    }

    @Scheduled(fixedDelayString = "${app.auth.rate-limit.report-interval-ms:600000}")
    public void reportNoticeBudget() {
        noticeBudget.reportAndCleanup(System.currentTimeMillis());
    }

    /** 보관 기한이 지난 SENT/FAILED 행을 지운다. 지운 행 수를 돌려준다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long deleteOldTerminal(LocalDateTime threshold) {
        return outboxMailRepository.deleteByStatusInAndUpdatedAtBefore(
                List.of(OutboxMailStatus.SENT, OutboxMailStatus.FAILED), threshold);
    }

    /** 발송에 필요한 값만 담은 꾸러미(엔티티를 트랜잭션 밖으로 내보내지 않는다). */
    public record PendingMail(Long id, String to, String token, OutboxMailKind kind, String ownerToken) {
    }
}
