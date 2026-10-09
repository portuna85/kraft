package com.kraft.user.mail;

import com.kraft.user.domain.Role;
import com.kraft.user.domain.User;
import com.kraft.user.domain.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** 안내 메일(계정 존재·로그인 경고)이 회원당 쿨다운 안에서 한 통만 큐에 들어가는지. */
@SpringBootTest
class OutboxMailStoreNoticeTest {

    @Autowired
    private OutboxMailStore outboxMailStore;

    @Autowired
    private OutboxMailRepository outboxMailRepository;

    @Autowired
    private UserRepository userRepository;

    private User newUser() {
        String unique = "notice-" + UUID.randomUUID().toString().substring(0, 8);
        return userRepository.save(User.builder()
                .name(unique).email(unique + "@example.com").password("encoded").role(Role.USER).build());
    }

    @Test
    @DisplayName("같은 회원에게 같은 종류를 반복해도 쿨다운 안에서는 한 통만 쌓인다")
    void enqueueNotice_withinCooldown_queuesOnce() {
        User user = newUser();

        boolean first = outboxMailStore.enqueueNotice(user, OutboxMailKind.ACCOUNT_EXISTS);
        boolean second = outboxMailStore.enqueueNotice(user, OutboxMailKind.ACCOUNT_EXISTS);
        boolean third = outboxMailStore.enqueueNotice(user, OutboxMailKind.ACCOUNT_EXISTS);

        assertThat(first).isTrue();
        assertThat(second).isFalse();
        assertThat(third).isFalse();
        assertThat(outboxMailRepository.findAll().stream()
                .filter(m -> m.getUser().getId().equals(user.getId())
                        && m.getKind() == OutboxMailKind.ACCOUNT_EXISTS)
                .count()).isEqualTo(1);
    }

    @Test
    @DisplayName("쿨다운은 종류별로 따로 센다")
    void enqueueNotice_cooldownIsPerKind() {
        User user = newUser();

        assertThat(outboxMailStore.enqueueNotice(user, OutboxMailKind.ACCOUNT_EXISTS)).isTrue();
        assertThat(outboxMailStore.enqueueNotice(user, OutboxMailKind.LOGIN_ATTEMPTS_WARNING)).isTrue();
    }
}
