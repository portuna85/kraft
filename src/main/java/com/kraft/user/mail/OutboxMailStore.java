package com.kraft.user.mail;

import com.kraft.user.domain.EmailVerificationTokenRepository;
import com.kraft.user.domain.PasswordResetTokenRepository;
import com.kraft.user.domain.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 아웃박스의 DB 쪽만 담당한다. <b>여기에는 SMTP 호출이 하나도 없다</b> — 그것이 이 클래스가
 * 따로 있는 이유다.
 * <p>
 * 발송 한 통은 "집기 → 보내기 → 결과 적기" 세 단계로 나뉘는데, 가운데 단계만 트랜잭션 밖에
 * 있어야 한다. 그러려면 앞뒤 단계가 각자 <b>독립된 짧은 트랜잭션</b>이어야 하므로 전부
 * {@code REQUIRES_NEW}로 연다. 작업자({@code OutboxMailWorker})가 큰 트랜잭션 하나로 감싸면
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

    /**
     * 보낼 메일을 대기열에 넣는다. <b>호출한 쪽의 트랜잭션에 참여한다</b>(REQUIRES_NEW가 아니다) —
     * 인증 토큰 저장과 이 행은 함께 커밋되거나 함께 사라져야 한다. 토큰만 남고 메일이 없거나,
     * 그 반대가 되면 안 된다.
     */
    @Transactional
    public void enqueue(User user, String token, OutboxMailKind kind) {
        outboxMailRepository.save(OutboxMail.builder().user(user).token(token).kind(kind).build());
    }

    /**
     * 보낼 것들을 집어 SENDING으로 바꾸고 id를 돌려준다.
     * <p>
     * 예전에는 평범한 SELECT 후 엔티티 상태만 바꿨는데, 그 사이에는 아무 잠금도 없어 예약
     * 실행과 {@code drainAsync}가 동시에 돌면 둘 다 같은 PENDING 행을 집어 중복 발송할 수
     * 있었다. 지금은 {@code SELECT ... FOR UPDATE SKIP LOCKED}로 행을 먼저 잠그고, 그 잠금이
     * 유지되는 같은 트랜잭션 안에서 곧바로 UPDATE로 SENDING을 확정한다. 두 번째 선점 시도는
     * 이미 잠긴 행을 건너뛰므로 겹치는 id를 돌려받지 않는다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<Long> claimBatch(int batchSize, String ownerToken) {
        List<Long> ids = outboxMailRepository.selectPendingIdsForUpdateSkipLocked(batchSize);
        if (ids.isEmpty()) {
            return ids;
        }
        outboxMailRepository.markSendingByIds(ids, LocalDateTime.now(), ownerToken);
        return ids;
    }

    /**
     * 보내는 데 필요한 값만 평범한 문자열로 꺼낸다. 여기서 꺼내 두어야 SMTP를 부를 때
     * 영속성 컨텍스트도 커넥션도 필요 없다 — 지연 로딩된 회원 이메일을 그 시점에 읽으면
     * 트랜잭션이 다시 열린다.
     * <p>
     * 발송 직전 토큰이 여전히 유효한지도 함께 확인한다. 탈퇴·재발급으로 토큰 테이블의 행이
     * 이미 지워졌다면 이 아웃박스 행은 더 이상 보낼 이유가 없는 옛 링크다 — 재시도하지 않고
     * 즉시 FAILED로 남긴다(개선 보고서 "메일 임대·재시도·실행량 제한").
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<PendingMail> load(Long id) {
        return outboxMailRepository.findById(id).flatMap(mail -> {
            if (!isTokenStillValid(mail)) {
                mail.markStale("토큰이 재발급되어 더 이상 유효하지 않습니다.");
                log.info("옛 토큰의 아웃박스 메일을 건너뛰었습니다. outboxMailId={}", mail.getId());
                return Optional.empty();
            }
            return Optional.of(new PendingMail(mail.getId(), mail.getUser().getEmail(), mail.getToken(), mail.getKind()));
        });
    }

    private boolean isTokenStillValid(OutboxMail mail) {
        return switch (mail.getKind()) {
            case VERIFY_EMAIL -> emailVerificationTokenRepository.findByToken(mail.getToken()).isPresent();
            case PASSWORD_RESET -> passwordResetTokenRepository.findByToken(mail.getToken()).isPresent();
        };
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSent(Long id) {
        outboxMailRepository.findById(id).ifPresent(OutboxMail::markSent);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long id, String error) {
        outboxMailRepository.findById(id).ifPresent(mail -> mail.markFailed(error, maxAttempts));
    }

    /**
     * 발송 도중 프로세스가 죽으면 그 메일은 SENDING인 채로 남아 아무도 다시 집지 않는다.
     * 오래된 것은 PENDING으로 되돌려 재시도 대상에 넣는다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int requeueStuck(LocalDateTime threshold) {
        List<OutboxMail> stuck =
                outboxMailRepository.findByStatusAndUpdatedAtBefore(OutboxMailStatus.SENDING, threshold);
        stuck.forEach(mail -> mail.markFailed("발송 도중 중단되어 다시 대기열에 넣었습니다.", maxAttempts));
        return stuck.size();
    }

    /** 이 회원에게 이 종류의 메일을 마지막으로 만든 시각. 요청 제한에 쓴다. */
    @Transactional(readOnly = true)
    public Optional<LocalDateTime> lastQueuedAt(Long userId, OutboxMailKind kind) {
        return outboxMailRepository.findFirstByUserIdAndKindOrderByIdDesc(userId, kind).map(OutboxMail::getCreatedAt);
    }

    /** 보관 기한이 지난 SENT/FAILED 행을 지운다. 지운 행 수를 돌려준다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long deleteOldTerminal(LocalDateTime threshold) {
        return outboxMailRepository.deleteByStatusInAndUpdatedAtBefore(
                List.of(OutboxMailStatus.SENT, OutboxMailStatus.FAILED), threshold);
    }

    /** 발송에 필요한 값만 담은 꾸러미. 엔티티를 트랜잭션 밖으로 들고 나가지 않으려는 것이다. */
    public record PendingMail(Long id, String to, String token, OutboxMailKind kind) {
    }
}
