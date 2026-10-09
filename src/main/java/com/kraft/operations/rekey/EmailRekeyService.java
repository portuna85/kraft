package com.kraft.operations.rekey;

import com.kraft.user.domain.EmailEncryption;
import com.kraft.user.domain.EmailHasher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * 저장된 모든 이메일을 옛 키에서 새 키로 다시 암호화한다. 키만 갈아 끼우면 로그인은 되는데 저장된 이메일을
 * 읽을 수 없게 된다({@code email_hmac}는 별도 pepper로 만들어 키와 무관하다).
 * <p>
 * 엔티티가 아니라 {@link JdbcTemplate}을 쓴다 — 컨텍스트의 {@code EmailAttributeConverter}는 새 키로 만들어져,
 * 아직 변환되지 않은 행을 엔티티로 읽으면 복호화 예외가 난다. 암호문을 문자열 그대로 읽고 쓴다.
 * <p>
 * 한 행: ① 새 키로 복호화되면 이미 변환된 행이라 건너뛴다(재실행 안전) ② 옛 키로 복호화(실패하면 id를 알리고
 * 멈춘다) ③ 평문의 HMAC을 {@code email_hmac}와 대조(주소를 꺼내 보지 않고 온전함을 증명) ④ 새 키로 UPDATE.
 * 행마다 즉시 커밋하므로 중간에 죽어도 다시 돌리면 남은 것만 처리한다.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class EmailRekeyService {

    /** 한 번에 읽어 올 행 수. 회원이 많아도 메모리에 전부 올리지 않는다. */
    private static final int BATCH_SIZE = 200;

    private final JdbcTemplate jdbcTemplate;

    /**
     * @param oldKey 지금 DB에 저장된 암호문을 만든 키
     * @param newKey 앞으로 쓸 키
     * @throws IllegalArgumentException 키가 비었거나 둘이 같을 때
     * @throws IllegalStateException    어느 키로도 복호화되지 않거나 해시가 어긋나는 행을 만났을 때
     */
    public Result rekeyAll(String oldKey, String newKey) {
        requireUsableKeys(oldKey, newKey);

        TextEncryptor from = EmailEncryption.encryptor(oldKey);
        TextEncryptor to = EmailEncryption.encryptor(newKey);

        int converted = 0;
        int alreadyDone = 0;
        long lastId = 0;

        while (true) {
            List<Map<String, Object>> rows = nextBatch(lastId);
            if (rows.isEmpty()) {
                break;
            }
            for (Map<String, Object> row : rows) {
                long id = ((Number) row.get("id")).longValue();
                lastId = id;

                if (rekeyRow(id, (String) row.get("email"), (String) row.get("email_hmac"), from, to)) {
                    converted++;
                } else {
                    alreadyDone++;
                }
            }
            log.info("이메일 키 교체 진행 중 — 변환 {}건, 이미 변환됨 {}건 (마지막 id={})",
                    converted, alreadyDone, lastId);
        }

        log.info("이메일 키 교체 완료 — 변환 {}건, 이미 변환됨 {}건", converted, alreadyDone);

        return new Result(converted, alreadyDone);
    }

    /** 두 키가 같으면 무의미한 재작성이다 — 옛 키 환경변수를 빠뜨렸을 때 조용히 "성공"하지 않게 막는다. */
    private static void requireUsableKeys(String oldKey, String newKey) {
        if (!StringUtils.hasText(oldKey) || !StringUtils.hasText(newKey)) {
            throw new IllegalArgumentException("옛 키와 새 키를 모두 지정해야 합니다.");
        }
        if (oldKey.equals(newKey)) {
            throw new IllegalArgumentException("옛 키와 새 키가 같습니다. 교체할 것이 없습니다.");
        }
    }

    private List<Map<String, Object>> nextBatch(long lastId) {
        return jdbcTemplate.queryForList(
                "SELECT id, email, email_hmac FROM users WHERE id > ? ORDER BY id LIMIT " + BATCH_SIZE,
                lastId);
    }

    /** @return 이 행을 실제로 변환했으면 true, 이미 새 키로 되어 있어 건너뛰었으면 false */
    private boolean rekeyRow(long id, String cipher, String emailHmac, TextEncryptor from, TextEncryptor to) {
        String alreadyNew = decryptOrNull(to, cipher);
        if (alreadyNew != null) {
            // 이미 새 키로 읽히는 행도 email_hmac와 대조한다(복호화만 우연히 성공한 다른 내용을 건너뛰지 않게).
            if (!EmailHasher.hmacHex(alreadyNew).equals(emailHmac)) {
                throw new IllegalStateException(
                        "이미 새 키로 읽히지만 email_hmac와 일치하지 않는 행이 있습니다. userId=" + id);
            }
            return false;
        }

        String plain = decryptOrNull(from, cipher);
        if (plain == null) {
            // 멈추지 않으면 나머지를 덮어써 원인을 알 수 없게 된다.
            throw new IllegalStateException(
                    "어느 키로도 복호화되지 않는 행이 있습니다. 옛 키가 맞는지 확인하세요. userId=" + id);
        }
        if (!EmailHasher.hmacHex(plain).equals(emailHmac)) {
            throw new IllegalStateException(
                    "복호화한 값이 저장된 email_hmac와 일치하지 않습니다. userId=" + id);
        }

        jdbcTemplate.update("UPDATE users SET email = ? WHERE id = ?", to.encrypt(plain), id);
        return true;
    }

    /**
     * {@link #rekeyAll} 뒤 전체 행을 새 키로 다시 훑어 검증한다(rekeyAll의 대조는 이번에 건드린 행만 보증한다).
     *
     * @param newKey rekeyAll에서 쓴 새 키
     */
    public VerifyResult verifyAll(String newKey) {
        TextEncryptor to = EmailEncryption.encryptor(newKey);

        int verified = 0;
        int mismatched = 0;
        long lastId = 0;

        while (true) {
            List<Map<String, Object>> rows = nextBatch(lastId);
            if (rows.isEmpty()) {
                break;
            }
            for (Map<String, Object> row : rows) {
                long id = ((Number) row.get("id")).longValue();
                lastId = id;

                String plain = decryptOrNull(to, (String) row.get("email"));
                boolean ok = plain != null && EmailHasher.hmacHex(plain).equals(row.get("email_hmac"));
                if (ok) {
                    verified++;
                } else {
                    mismatched++;
                    log.warn("이메일 키 교체 검증 실패 — 새 키로 email_hmac와 일치하지 않습니다. userId={}", id);
                }
            }
        }

        log.info("이메일 키 교체 검증 완료 — 일치 {}건, 불일치 {}건", verified, mismatched);

        return new VerifyResult(verified, mismatched);
    }

    /** 복호화 실패는 이 도구에서 <b>정상적인 판정 수단</b>이므로 예외로 다루지 않는다. */
    private static String decryptOrNull(TextEncryptor encryptor, String cipher) {
        try {
            return encryptor.decrypt(cipher);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * @param converted   이번 실행에서 새 키로 다시 쓴 users 행
     * @param alreadyDone 이미 새 키였던 users 행. 재실행이라면 여기에 쌓인다
     */
    public record Result(int converted, int alreadyDone) {

        public int total() {
            return converted + alreadyDone;
        }
    }

    /**
     * @param verified   새 키로 복호화한 값이 email_hmac와 일치한 users 행
     * @param mismatched 새 키로 복호화되지 않거나 해시가 어긋난 users 행
     */
    public record VerifyResult(int verified, int mismatched) {

        public boolean allVerified() {
            return mismatched == 0;
        }
    }
}
