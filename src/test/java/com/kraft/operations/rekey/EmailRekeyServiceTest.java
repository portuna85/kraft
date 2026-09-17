package com.kraft.operations.rekey;

import com.kraft.user.domain.EmailEncryption;
import com.kraft.user.domain.EmailHasher;
import com.kraft.user.domain.Role;
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

/**
 * 이메일 암호화 키 교체를 실제 DB 행으로 검증한다 (개선 보고서 "자격 증명 관리" 공백).
 * <p>
 * 지금까지 키를 바꿀 방법이 아예 없었다. {@code application-prod.yml}은 "운영 중 절대
 * 변경하지 않는다"고 적혀 있었지만, <b>키가 유출되면 바꿔야 한다</b>. 손으로 바꾸면
 * {@code email_hash}는 키를 쓰지 않으므로 로그인은 계속 되고 저장된 이메일만 전부 깨진다.
 * <p>
 * 옛 키로 만든 암호문을 {@link JdbcTemplate}으로 직접 심는다. 컨텍스트의 컨버터가 어떤 키로
 * 만들어졌는지와 무관하게 검증되므로, 프로파일을 흔들 필요가 없다.
 */
@SpringBootTest
class EmailRekeyServiceTest {

    private static final String OLD_KEY = "old-key-0123456789";
    private static final String NEW_KEY = "new-key-9876543210";

    @Autowired
    private EmailRekeyService emailRekeyService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 엔티티가 아니라 SQL로 비운다. {@code userRepository.deleteAll()}은 행을 <b>읽어서</b>
     * 지우는데, 그 읽기가 컨텍스트의 키로 복호화를 시도해 옛 키·새 키로 암호화된 행에서 깨진다.
     * 자식 테이블을 먼저 지우는 것은 FK 때문이다.
     */
    @BeforeEach
    void setUp() {
        List.of("comments", "post_likes", "post_images", "posts",
                        "email_verification_tokens", "outbox_mails", "users")
                .forEach(table -> jdbcTemplate.update("DELETE FROM " + table));
    }

    /** 옛 키로 암호화된 행을 만든다. 엔티티로 저장하면 컨텍스트의 키가 쓰이므로 직접 넣는다. */
    private long givenUserEncryptedWithOldKey(String email) {
        String name = "rekey-" + UUID.randomUUID().toString().substring(0, 8);
        jdbcTemplate.update("INSERT INTO users (name, email, email_hash, password, role, version, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, 0, NOW(), NOW())",
                name,
                EmailEncryption.encryptor(OLD_KEY).encrypt(email),
                EmailHasher.sha512Hex(email),
                "encoded",
                Role.USER.name());
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE name = ?", Long.class, name);
    }

    private String storedCipher(long id) {
        return jdbcTemplate.queryForObject("SELECT email FROM users WHERE id = ?", String.class, id);
    }

    private String storedHash(long id) {
        return jdbcTemplate.queryForObject("SELECT email_hash FROM users WHERE id = ?", String.class, id);
    }

    @Test
    @DisplayName("옛 키로 저장된 이메일을 새 키로 다시 암호화한다")
    void rewritesEveryRowWithTheNewKey() {
        String email = "rekey-target@example.com";
        long id = givenUserEncryptedWithOldKey(email);
        String before = storedCipher(id);

        EmailRekeyService.Result result = emailRekeyService.rekeyAll(OLD_KEY, NEW_KEY);

        assertThat(result.converted()).isEqualTo(1);
        assertThat(storedCipher(id)).as("저장된 암호문 자체가 바뀌어야 한다").isNotEqualTo(before);
        assertThat(EmailEncryption.encryptor(NEW_KEY).decrypt(storedCipher(id)))
                .as("새 키로 원래 주소가 그대로 나와야 한다").isEqualTo(email);
    }

    /**
     * 이것이 "로그인은 계속 된다"의 근거다. {@code email_hash}는 키를 쓰지 않으므로 교체가
     * 건드릴 이유가 없고, 실제로 건드리지 않아야 한다.
     */
    @Test
    @DisplayName("조회에 쓰는 email_hash는 한 글자도 바뀌지 않는다")
    void searchHashIsUntouched() {
        long id = givenUserEncryptedWithOldKey("hash-stays@example.com");
        String before = storedHash(id);

        emailRekeyService.rekeyAll(OLD_KEY, NEW_KEY);

        assertThat(storedHash(id)).isEqualTo(before);
    }

    /**
     * 키 교체 도중 프로세스가 죽는 것은 일어날 일이다. 다시 돌리면 남은 것만 처리해야 하고,
     * 이미 변환된 행을 옛 키로 읽으려다 멈춰서는 안 된다.
     */
    @Test
    @DisplayName("두 번 돌리면 두 번째는 이미 변환된 것으로 보고 건드리지 않는다")
    void rerunIsSafe() {
        long id = givenUserEncryptedWithOldKey("rerun@example.com");
        emailRekeyService.rekeyAll(OLD_KEY, NEW_KEY);
        String afterFirst = storedCipher(id);

        EmailRekeyService.Result second = emailRekeyService.rekeyAll(OLD_KEY, NEW_KEY);

        assertThat(second.converted()).isZero();
        assertThat(second.alreadyDone()).isEqualTo(1);
        assertThat(storedCipher(id)).isEqualTo(afterFirst);
    }

    @Test
    @DisplayName("일부만 변환된 상태에서 이어서 돌리면 남은 것만 처리한다")
    void resumesFromAPartialRun() {
        long done = givenUserEncryptedWithOldKey("already@example.com");
        jdbcTemplate.update("UPDATE users SET email = ? WHERE id = ?",
                EmailEncryption.encryptor(NEW_KEY).encrypt("already@example.com"), done);
        givenUserEncryptedWithOldKey("remaining@example.com");

        EmailRekeyService.Result result = emailRekeyService.rekeyAll(OLD_KEY, NEW_KEY);

        assertThat(result.converted()).isEqualTo(1);
        assertThat(result.alreadyDone()).isEqualTo(1);
        assertThat(result.total()).isEqualTo(2);
    }

    /**
     * 옛 키가 틀렸을 때 나머지를 조용히 덮어쓰면 원인을 영영 알 수 없게 된다. 멈추고,
     * 어느 행인지 알려야 한다 — 그러면서도 주소는 남기지 않는다.
     */
    @Test
    @DisplayName("어느 키로도 복호화되지 않는 행을 만나면 멈추고 그 id를 알린다")
    void stopsOnARowThatNeitherKeyCanRead() {
        long id = givenUserEncryptedWithOldKey("unreadable@example.com");
        jdbcTemplate.update("UPDATE users SET email = ? WHERE id = ?",
                EmailEncryption.encryptor("some-third-key").encrypt("unreadable@example.com"), id);

        assertThatThrownBy(() -> emailRekeyService.rekeyAll(OLD_KEY, NEW_KEY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("userId=" + id)
                .as("로그·예외에 주소가 새어 나가면 안 된다")
                .hasMessageNotContaining("unreadable@example.com");
    }

    /**
     * 해시 대조가 이 도구의 진짜 검증이다. 복호화가 "성공"해도 나온 값이 그 행의 주소가
     * 아니라면 데이터를 잘못 쓰는 것이다.
     */
    @Test
    @DisplayName("복호화한 값이 저장된 해시와 어긋나면 쓰지 않고 멈춘다")
    void stopsWhenThePlaintextDoesNotMatchTheStoredHash() {
        long id = givenUserEncryptedWithOldKey("hash-mismatch@example.com");
        jdbcTemplate.update("UPDATE users SET email_hash = ? WHERE id = ?",
                EmailHasher.sha512Hex("someone-else@example.com"), id);
        String before = storedCipher(id);

        assertThatThrownBy(() -> emailRekeyService.rekeyAll(OLD_KEY, NEW_KEY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("email_hash");

        assertThat(storedCipher(id)).as("멈췄으면 그 행은 그대로여야 한다").isEqualTo(before);
    }

    @Test
    @DisplayName("옛 키를 빠뜨리거나 새 키와 같으면 아무것도 하지 않고 거부한다")
    void refusesUnusableKeys() {
        long id = givenUserEncryptedWithOldKey("guard@example.com");
        String before = storedCipher(id);

        assertThatThrownBy(() -> emailRekeyService.rekeyAll("", NEW_KEY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("모두 지정");
        assertThatThrownBy(() -> emailRekeyService.rekeyAll(NEW_KEY, NEW_KEY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("같습니다");

        assertThat(storedCipher(id)).isEqualTo(before);
    }

    @Test
    @DisplayName("회원이 여럿이어도 전부 변환한다")
    void convertsEveryUser() {
        List<String> emails = List.of("a@example.com", "b@example.com", "c@example.com");
        emails.forEach(this::givenUserEncryptedWithOldKey);

        EmailRekeyService.Result result = emailRekeyService.rekeyAll(OLD_KEY, NEW_KEY);

        assertThat(result.converted()).isEqualTo(3);
        assertThat(jdbcTemplate.queryForList("SELECT email FROM users", String.class))
                .allSatisfy(cipher -> assertThat(EmailEncryption.encryptor(NEW_KEY).decrypt(cipher))
                        .isIn(emails));
    }

    /**
     * B13: 이미 새 키로 읽히는 행이라도 건너뛰기 전에 email_hash와 대조해야 한다 — 그렇지
     * 않으면 새 키로 우연히 복호화는 되지만 내용이 다른 행(수동 복구 실수 등)을 "이미 완료"로
     * 잘못 간주하고 조용히 지나친다.
     */
    @Test
    @DisplayName("B13: 이미 새 키로 읽히지만 email_hash와 어긋나는 행을 만나면 멈춘다")
    void stopsWhenAnAlreadyNewKeyRowDoesNotMatchTheStoredHash() {
        long id = givenUserEncryptedWithOldKey("already-new-but-wrong@example.com");
        jdbcTemplate.update("UPDATE users SET email = ? WHERE id = ?",
                EmailEncryption.encryptor(NEW_KEY).encrypt("someone-else@example.com"), id);

        assertThatThrownBy(() -> emailRekeyService.rekeyAll(OLD_KEY, NEW_KEY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이미 새 키로 읽히지만")
                .hasMessageContaining("userId=" + id);
    }

    /**
     * B13: rekeyAll이 끝난 뒤의 별도 검증 패스. rekeyAll 자체가 이미 행마다 해시를 대조하므로
     * 정상 실행 뒤에는 전부 일치해야 한다.
     */
    @Test
    @DisplayName("B13: 교체 완료 후 검증 패스는 전체 행이 새 키와 일치함을 확인한다")
    void verifyAllConfirmsEveryRowAfterASuccessfulRun() {
        List<String> emails = List.of("verify-a@example.com", "verify-b@example.com");
        emails.forEach(this::givenUserEncryptedWithOldKey);
        emailRekeyService.rekeyAll(OLD_KEY, NEW_KEY);

        EmailRekeyService.VerifyResult result = emailRekeyService.verifyAll(NEW_KEY);

        assertThat(result.verified()).isEqualTo(2);
        assertThat(result.mismatched()).isZero();
        assertThat(result.allVerified()).isTrue();
    }

    @Test
    @DisplayName("B13: 검증 패스는 새 키로 읽히지 않거나 해시가 어긋나는 행을 불일치로 센다")
    void verifyAllCountsRowsThatDoNotMatch() {
        long stillOldKey = givenUserEncryptedWithOldKey("never-converted@example.com");

        EmailRekeyService.VerifyResult result = emailRekeyService.verifyAll(NEW_KEY);

        assertThat(result.mismatched()).isEqualTo(1);
        assertThat(result.allVerified()).isFalse();
    }
}
