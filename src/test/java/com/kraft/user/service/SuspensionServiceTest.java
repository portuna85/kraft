package com.kraft.user.service;

import com.kraft.shared.exception.NotFoundException;
import com.kraft.user.domain.Role;
import com.kraft.user.domain.User;
import com.kraft.user.domain.UserRepository;
import com.kraft.user.dto.SuspendedUserDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 정지 회원 목록과 해제를 실제 DB로 검증한다.
 * <p>
 * 이 화면에서 지켜야 할 것은 "지금 정지 중인 사람만 보인다"이다. 만료된 정지가 목록에 남으면
 * 관리자가 이미 풀린 계정을 또 풀려고 하게 된다.
 */
@SpringBootTest
class SuspensionServiceTest {

    @Autowired
    private SuspensionService suspensionService;

    @Autowired
    private UserRepository userRepository;

    private User suspended;

    @BeforeEach
    void setUp() {
        suspended = saveUser("suspended", LocalDateTime.now().plusDays(3), "욕설·비방 신고 처리(7일)");
    }

    private User saveUser(String prefix, LocalDateTime until, String reason) {
        String unique = prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
        User user = User.builder()
                .name(unique)
                .email(unique + "@example.com")
                .password("encoded")
                .role(Role.USER)
                .build();
        if (until != null) {
            user.suspendUntil(until, reason);
        }
        return userRepository.save(user);
    }

    private List<SuspendedUserDto> currentList() {
        return suspensionService.findSuspended(PageRequest.of(0, 50)).getContent();
    }

    @Test
    @DisplayName("지금 정지 중인 회원만 목록에 나온다")
    void findSuspended_listsOnlyCurrentlySuspendedUsers() {
        User expired = saveUser("expired", LocalDateTime.now().minusMinutes(1), "이미 지난 정지");
        User normal = saveUser("normal", null, null);

        List<String> names = currentList().stream().map(SuspendedUserDto::name).toList();

        assertThat(names).contains(suspended.getName())
                // 만료된 정지는 이미 풀린 것과 같다. 목록에 남으면 또 풀려고 하게 된다.
                .doesNotContain(expired.getName())
                .doesNotContain(normal.getName());
    }

    @Test
    @DisplayName("목록은 기한과 사유를 함께 준다 — 풀지 말지 판단할 근거다")
    void findSuspended_includesDeadlineAndReason() {
        assertThat(currentList())
                .filteredOn(view -> view.name().equals(suspended.getName()))
                .singleElement()
                .satisfies(view -> {
                    assertThat(view.suspendedUntil()).isNotNull();
                    assertThat(view.suspensionReason()).contains("욕설·비방");
                });
    }

    @Test
    @DisplayName("해제하면 곧바로 목록에서 빠지고 다시 글을 쓸 수 있다")
    void lift_removesFromListAndRestoresWriteAccess() {
        suspensionService.lift(suspended.getId());

        User reloaded = userRepository.findById(suspended.getId()).orElseThrow();
        assertThat(reloaded.isSuspended()).isFalse();
        assertThat(currentList().stream().map(SuspendedUserDto::name)).doesNotContain(suspended.getName());
        // 사유는 지우지 않는다 — 무슨 일이 있었는지는 남아야 한다.
        assertThat(reloaded.getSuspensionReason()).contains("욕설·비방");
    }

    @Test
    @DisplayName("정지 중이 아닌 계정을 풀려고 하면 그렇게 알려준다")
    void lift_whenNotSuspended_isRejected() {
        User normal = saveUser("normal", null, null);

        assertThatThrownBy(() -> suspensionService.lift(normal.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("정지 중인 계정이 아닙니다");
    }

    @Test
    @DisplayName("없는 회원을 풀려고 하면 그렇게 알려준다")
    void lift_whenUserNotFound_isRejected() {
        assertThatThrownBy(() -> suspensionService.lift(999_999L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("존재하지 않는 회원");
    }
}
