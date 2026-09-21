package com.kraft.user.session;

import com.kraft.user.domain.User;
import com.kraft.user.service.SessionRevoker;
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
 * 세션 폐기 태스크의 DB 쪽만 담당한다. 아웃박스 메일과 달리 실제 폐기 작업({@link SessionRevoker})도
 * 그 자체가 DB(세션 저장소) 작업이라 SMTP처럼 트랜잭션 밖으로 뺄 이유는 없다 — 그래서 claim과
 * 처리를 분리하는 것은 동시 선점 방지 목적일 뿐이다.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class SessionRevocationStore {

    private final SessionRevocationTaskRepository taskRepository;
    private final SessionRevoker sessionRevoker;

    @Value("${app.session-revocation.max-attempts:5}")
    private int maxAttempts;

    /**
     * 비밀번호 변경·재설정·탈퇴와 <b>같은 트랜잭션</b> 안에서 태스크를 만든다(REQUIRES_NEW가
     * 아니다) — DB 변경과 이 태스크는 함께 커밋되거나 함께 사라져야 한다.
     */
    @Transactional
    public Long enqueue(User user, String emailSnapshot) {
        return taskRepository.save(SessionRevocationTask.builder()
                .user(user)
                .emailSnapshot(emailSnapshot)
                .build()).getId();
    }

    /** 커밋 직후 빠른 경로 전용. 방금 만든 태스크 하나만 PENDING일 때 선점한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Long> claimSpecific(Long id, String ownerToken) {
        int updated = taskRepository.markProcessingIfPending(id, LocalDateTime.now(), ownerToken);
        return updated == 1 ? Optional.of(id) : Optional.empty();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<Long> claimBatch(int batchSize, String ownerToken) {
        List<Long> ids = taskRepository.selectPendingIdsForUpdateSkipLocked(batchSize);
        if (ids.isEmpty()) {
            return ids;
        }
        taskRepository.markProcessingByIds(ids, LocalDateTime.now(), ownerToken);
        return ids;
    }

    /**
     * {@code ownerToken}이 지금도 이 태스크의 소유자와 같을 때만 처리한다. 정체 재큐잉이 소유권을
     * 넘긴 뒤에는 원래 소유자가 뒤늦게 이 메서드를 불러도 아무 일도 하지 않는다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processOne(Long id, String ownerToken) {
        taskRepository.findByIdAndOwnerTokenAndStatus(id, ownerToken, SessionRevocationTaskStatus.PROCESSING)
                .ifPresentOrElse(task -> {
                    try {
                        sessionRevoker.revokeAll(task.getEmailSnapshot(), task.getUser().getId());
                        task.markDone();
                    } catch (RuntimeException e) {
                        log.warn("세션 폐기 태스크 처리에 실패했습니다. taskId={}", id, e);
                        task.markFailed(e.getMessage(), maxAttempts);
                    }
                }, () -> log.info("이미 다른 워커가 재선점했거나 끝난 태스크라 건너뜁니다. taskId={}", id));
    }

    /**
     * 처리 도중 프로세스가 죽으면 그 태스크는 PROCESSING인 채로 남아 아무도 다시 집지 않는다.
     * 오래된 것은 PENDING으로 되돌리고 소유권 표시도 비운다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int requeueStuck(LocalDateTime threshold) {
        List<SessionRevocationTask> stuck =
                taskRepository.findByStatusAndUpdatedAtBefore(SessionRevocationTaskStatus.PROCESSING, threshold);
        stuck.forEach(task -> {
            task.markFailed("처리 도중 중단되어 다시 대기열에 넣었습니다.", maxAttempts);
            task.releaseOwnership();
        });
        return stuck.size();
    }

    /** 보관 기한이 지난 DONE/FAILED 행을 지운다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long deleteOldTerminal(LocalDateTime threshold) {
        return taskRepository.deleteByStatusInAndUpdatedAtBefore(
                List.of(SessionRevocationTaskStatus.DONE, SessionRevocationTaskStatus.FAILED), threshold);
    }
}
