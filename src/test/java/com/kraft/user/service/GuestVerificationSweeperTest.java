package com.kraft.user.service;

import com.kraft.user.domain.EmailVerificationTokenRepository;
import com.kraft.user.domain.Role;
import com.kraft.user.domain.User;
import com.kraft.user.domain.UserRepository;
import com.kraft.user.mail.OutboxMailRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * B08의 최후 수단을 검증한다. 가입 트랜잭션의 최종 커밋 실패는 실제로 재현하기 어려우므로
 * (커밋 성공 후 어떤 흔적도 남지 않는 실패라 스파이로 흉내 낼 지점이 없다), 그 결과 상태 —
 * "GUEST 계정은 있는데 토큰도 아웃박스 메일도 하나도 없다" — 를 직접 만들어 스윕이 그 상태를
 * 찾아 복구하는지 확인한다.
 */
@SpringBootTest
class GuestVerificationSweeperTest {

    @Autowired
    private GuestVerificationSweeper sweeper;

    @Autowired
    private EmailVerificationService emailVerificationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailVerificationTokenRepository tokenRepository;

    @Autowired
    private OutboxMailRepository outboxMailRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        tokenRepository.deleteAll();
        outboxMailRepository.deleteAll();
        userRepository.deleteAll();
        // sweeper는 싱글턴 빈이라 O07 테스트가 enabled를 꺼 둔 채로 남기면 실행 순서에 따라
        // 다른 테스트까지 영향을 받는다 — 매번 켜진 상태로 시작한다(PostImageCleanerTest와 같은 관례).
        ReflectionTestUtils.setField(sweeper, "enabled", true);
    }

    @Test
    @DisplayName("B08: 가입 후 인증 메일이 한 번도 큐에 들어가지 못한 GUEST 계정을 찾아 다시 큐에 넣는다")
    void sweep_recoversGuestMissingVerificationMail() {
        User stale = saveGuest(LocalDateTime.now().minusMinutes(20));

        sweeper.sweep();

        assertThat(tokenRepository.findAll()).anyMatch(t -> t.getUser().getId().equals(stale.getId()));
        assertThat(outboxMailRepository.findAll()).anyMatch(m -> m.getUser().getId().equals(stale.getId()));
    }

    @Test
    @DisplayName("B08: 가입한 지 얼마 안 된 계정은(유예시간 안) 건드리지 않는다")
    void sweep_ignoresRecentlyCreatedGuests() {
        User recent = saveGuest(LocalDateTime.now());

        sweeper.sweep();

        assertThat(tokenRepository.findAll()).noneMatch(t -> t.getUser().getId().equals(recent.getId()));
        assertThat(outboxMailRepository.findAll()).noneMatch(m -> m.getUser().getId().equals(recent.getId()));
    }

    @Test
    @DisplayName("B08: 이미 정상적으로 메일이 나간 계정은 다시 건드리지 않는다")
    void sweep_leavesAccountsThatAlreadyHaveAMailAlone() {
        User already = saveGuest(LocalDateTime.now().minusMinutes(20));

        // 정상 경로로 한 번 보낸 것과 같은 상태를 만든다 — 토큰·아웃박스 행이 이미 있으므로
        // 스윕의 조회 대상이 아니어야 한다.
        emailVerificationService.sendVerificationEmail(already.getEmail());
        long tokenCountBefore = tokenRepository.count();
        long mailCountBefore = outboxMailRepository.count();

        sweeper.sweep();

        assertThat(tokenRepository.count()).isEqualTo(tokenCountBefore);
        assertThat(outboxMailRepository.count()).isEqualTo(mailCountBefore);
    }

    /**
     * O07: rekey 프로파일이 이 스위치를 끈다 — 아직 옛 키로 남은 GUEST 행이 섞이면 email
     * 복호화가 엔티티 로딩 시점에 실패하므로, 키 교체 중에는 아예 조회 자체가 돌면 안 된다.
     */
    @Test
    @DisplayName("O07: 스위치를 끄면 저장소를 건드리지 않고 그대로 돌아간다")
    void sweep_whenDisabled_doesNothing() {
        User stale = saveGuest(LocalDateTime.now().minusMinutes(20));
        ReflectionTestUtils.setField(sweeper, "enabled", false);

        sweeper.sweep();

        assertThat(tokenRepository.findAll()).noneMatch(t -> t.getUser().getId().equals(stale.getId()));
        assertThat(outboxMailRepository.findAll()).noneMatch(m -> m.getUser().getId().equals(stale.getId()));
    }

    private User saveGuest(LocalDateTime createdAt) {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        User user = userRepository.save(User.builder()
                .name("sweep-" + unique)
                .email("sweep-" + unique + "@example.com")
                .password("encoded")
                .role(Role.GUEST)
                .build());
        jdbcTemplate.update("UPDATE users SET created_at = ? WHERE id = ?", createdAt, user.getId());
        return user;
    }
}
