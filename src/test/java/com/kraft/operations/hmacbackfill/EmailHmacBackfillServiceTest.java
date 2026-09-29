package com.kraft.operations.hmacbackfill;

import com.kraft.user.domain.EmailEncryption;
import com.kraft.user.domain.EmailHasher;
import com.kraft.user.domain.Role;
import com.kraft.user.domain.User;
import com.kraft.user.domain.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 이메일 HMAC 이중 기록(P0-3 1단계)과 기존 행 백필을 실제 DB 행으로 검증한다. */
@SpringBootTest
class EmailHmacBackfillServiceTest {

    private static final String KEY = "backfill-key-0123456789";

    @Autowired
    private EmailHmacBackfillService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        List.of("comments", "post_likes", "post_images", "posts",
                        "email_verification_tokens", "outbox_mails", "session_revocation_tasks", "users")
                .forEach(table -> jdbcTemplate.update("DELETE FROM " + table));
    }

    /** HMAC 도입 전 방식으로 만든 행(email_hmac NULL)을 SQL로 직접 심는다. */
    private long legacyUser(String email, String hashOverride) {
        String name = "bf-" + UUID.randomUUID().toString().substring(0, 8);
        jdbcTemplate.update("INSERT INTO users (name, email, email_hash, password, role, version, "
                        + "failed_login_attempts, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, 0, 0, NOW(), NOW())",
                name, EmailEncryption.encryptor(KEY).encrypt(email),
                hashOverride != null ? hashOverride : EmailHasher.sha512Hex(email), "encoded", Role.USER.name());
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE name = ?", Long.class, name);
    }

    private String hmacOf(long id) {
        return jdbcTemplate.queryForObject("SELECT email_hmac FROM users WHERE id = ?", String.class, id);
    }

    @Test
    @DisplayName("엔티티를 저장하면 email_hmac이 함께 기록된다")
    void newUser_getsHmacAlongsideHash() {
        User saved = userRepository.save(User.builder().name("dual-" + UUID.randomUUID().toString().substring(0, 8))
                .email("dual@example.com").password("x").role(Role.USER).build());

        assertThat(hmacOf(saved.getId())).isEqualTo(EmailHasher.hmacHex("dual@example.com"));
    }

    @Test
    @DisplayName("NULL인 기존 행만 채우고, 다시 실행하면 아무것도 바꾸지 않는다")
    void backfill_fillsNullRows_andIsRerunSafe() {
        long a = legacyUser("a@example.com", null);
        long b = legacyUser("b@example.com", null);
        assertThat(hmacOf(a)).isNull();

        EmailHmacBackfillService.Result first = service.backfill(KEY);

        assertThat(first.filled()).isEqualTo(2);
        assertThat(first.complete()).isTrue();
        assertThat(hmacOf(a)).isEqualTo(EmailHasher.hmacHex("a@example.com"));
        assertThat(hmacOf(b)).isEqualTo(EmailHasher.hmacHex("b@example.com"));

        EmailHmacBackfillService.Result second = service.backfill(KEY);
        assertThat(second.filled()).isZero();
        assertThat(second.complete()).isTrue();
    }

    @Test
    @DisplayName("email_hash와 어긋난 행을 만나면 멈추고 그 행을 채우지 않는다")
    void backfill_stopsOnHashMismatch() {
        long bad = legacyUser("bad@example.com", EmailHasher.sha512Hex("someone-else@example.com"));

        assertThatThrownBy(() -> service.backfill(KEY)).isInstanceOf(IllegalStateException.class);
        assertThat(hmacOf(bad)).isNull();
    }

    @Test
    @DisplayName("키가 틀리면 복호화 실패로 멈춘다")
    void backfill_stopsOnWrongKey() {
        legacyUser("c@example.com", null);

        assertThatThrownBy(() -> service.backfill("some-other-key-0123456789"))
                .isInstanceOf(IllegalStateException.class);
    }
}
