package com.kraft.user.session;

import com.kraft.user.domain.Role;
import com.kraft.user.domain.User;
import com.kraft.user.domain.UserRepository;
import com.kraft.user.service.SessionRevoker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

/**
 * 비밀번호 변경·재설정·탈퇴 커밋 직후의 빠른 경로가 실패해도, 영속 태스크가 남아 주기 작업이
 * 결국 세션 폐기를 완수하는지 검증한다(B06).
 */
@SpringBootTest
class SessionRevocationWorkerTest {

    @Autowired
    private SessionRevocationStore store;

    @Autowired
    private SessionRevocationWorker worker;

    @Autowired
    private SessionRevocationTaskRepository taskRepository;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private SessionRevoker sessionRevoker;

    private User user;

    @BeforeEach
    void setUp() {
        taskRepository.deleteAll();
        userRepository.deleteAll();
        String unique = UUID.randomUUID().toString().substring(0, 8);
        user = userRepository.save(User.builder()
                .name("revoke-" + unique)
                .email("revoke-" + unique + "@example.com")
                .password("encoded")
                .role(Role.USER)
                .build());
    }

    @Test
    @DisplayName("첫 시도(빠른 경로)가 실패해도 태스크가 남아, 다음 주기 재시도에서 최종적으로 완료한다")
    void failedFastPathIsRetriedByScheduledDrainUntilItSucceeds() {
        willThrow(new RuntimeException("세션 저장소 장애"))
                .given(sessionRevoker).revokeAll(String.valueOf(user.getId()), user.getId());

        Long taskId = store.enqueue(user, user.getEmail());
        worker.attemptNow(taskId);

        SessionRevocationTask afterFirstAttempt = taskRepository.findById(taskId).orElseThrow();
        assertThat(afterFirstAttempt.getStatus()).isEqualTo(SessionRevocationTaskStatus.PENDING);
        assertThat(afterFirstAttempt.getAttempts()).isEqualTo(1);

        // 다음 주기에는 세션 저장소가 복구되었다고 가정한다.
        org.mockito.Mockito.reset(sessionRevoker);

        worker.drainScheduled();

        SessionRevocationTask done = taskRepository.findById(taskId).orElseThrow();
        assertThat(done.getStatus()).isEqualTo(SessionRevocationTaskStatus.DONE);
        verify(sessionRevoker).revokeAll(String.valueOf(user.getId()), user.getId());
    }

    @Test
    @DisplayName("재시도 기회를 다 쓰면 FAILED로 끝나고 더는 집히지 않는다")
    void exhaustedRetriesEndAsFailed() {
        willThrow(new RuntimeException("영구 장애")).given(sessionRevoker).revokeAll(anyString(), any());
        int maxAttempts = (int) ReflectionTestUtils.getField(store, "maxAttempts");

        Long taskId = store.enqueue(user, user.getEmail());
        for (int i = 0; i < maxAttempts; i++) {
            worker.drainScheduled();
        }

        SessionRevocationTask failed = taskRepository.findById(taskId).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(SessionRevocationTaskStatus.FAILED);
        assertThat(failed.getAttempts()).isEqualTo(maxAttempts);

        // 더 돌려도 FAILED는 PENDING이 아니므로 집히지 않는다 — 시도 횟수가 그대로다.
        worker.drainScheduled();
        assertThat(taskRepository.findById(taskId).orElseThrow().getAttempts()).isEqualTo(maxAttempts);
    }

    @Test
    @DisplayName("처리 도중 중단되어 PROCESSING으로 남은 태스크는 소유권이 비워진 채 다시 대기열로 돌아온다")
    void stuckTaskIsRequeuedWithOwnershipReleased() {
        Long taskId = store.enqueue(user, user.getEmail());
        assertThat(store.claimBatch(10, "stuck-owner")).hasSize(1);
        assertThat(taskRepository.findById(taskId).orElseThrow().getStatus())
                .isEqualTo(SessionRevocationTaskStatus.PROCESSING);

        int requeued = store.requeueStuck(LocalDateTime.now().plusMinutes(1));

        assertThat(requeued).isEqualTo(1);
        SessionRevocationTask task = taskRepository.findById(taskId).orElseThrow();
        assertThat(task.getStatus()).isEqualTo(SessionRevocationTaskStatus.PENDING);
        assertThat(task.getOwnerToken()).isNull();
    }

    @Test
    @DisplayName("탈퇴 후 같은 이메일로 재가입한 새 계정이 있어도, 태스크는 원래 계정의 회원 번호로만 폐기를 시도한다")
    void staleTaskUsesTheOriginalUserIdNotTheNewAccount() {
        String email = user.getEmail();
        Long taskId = store.enqueue(user, email);

        // 실제 탈퇴처럼 계정의 이메일을 먼저 익명 주소로 바꿔 커밋하고(원래 이메일 자리를
        // 비워야 email_hash 유니크 제약과 부딪히지 않는다), 그 뒤 같은 이메일로 새 계정이
        // 재가입했다고 가정한다. 세션 principal이 이제 회원 id라(BE-04) revokeAll은 애초에
        // 이메일이 아니라 원래 계정의 불변 id로만 조회하므로, 재가입한 새 계정의 세션과
        // 섞일 여지가 구조적으로 없다.
        user.withdraw("withdrawn-" + user.getId() + "@kraft.invalid", "탈퇴한 사용자", "encoded");
        userRepository.save(user);
        userRepository.save(User.builder()
                .name("new-account")
                .email(email)
                .password("encoded")
                .role(Role.USER)
                .build());

        worker.attemptNow(taskId);

        verify(sessionRevoker).revokeAll(String.valueOf(user.getId()), user.getId());
    }

    /**
     * O02: 이메일 키 교체(rekey) 창에서 이 워커가 옛 키로 암호화된 email_snapshot을 복호화
     * 하려다 죽지 않도록, application-rekey.yml이 이 플래그를 끈다. OutboxMailWorker의
     * whenDisabled_drainDoesNothing과 같은 패턴이다.
     */
    @Test
    @DisplayName("O02: enabled가 false면 예약 실행과 attemptNow 모두 아무 것도 처리하지 않는다")
    void whenDisabled_nothingIsProcessed() {
        ReflectionTestUtils.setField(worker, "enabled", false);
        try {
            Long taskId = store.enqueue(user, user.getEmail());

            worker.attemptNow(taskId);
            worker.drainScheduled();

            SessionRevocationTask task = taskRepository.findById(taskId).orElseThrow();
            assertThat(task.getStatus()).isEqualTo(SessionRevocationTaskStatus.PENDING);
            assertThat(task.getAttempts()).isZero();
        } finally {
            ReflectionTestUtils.setField(worker, "enabled", true);
        }
    }
}
