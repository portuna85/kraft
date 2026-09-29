package com.kraft.operations.hmacbackfill;

import com.kraft.user.domain.EmailEncryption;
import com.kraft.user.domain.EmailHasher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 이중 기록 이전에 만들어진 행의 {@code users.email_hmac}을 채운다(P0-3 2단계).
 * <p>
 * 엔티티가 아니라 {@link JdbcTemplate}을 쓰는 이유는 {@code EmailRekeyService}와 같다 — 행마다
 * 즉시 커밋하고 {@code @PreUpdate}가 끼어들지 않게 하기 위해서다. 절차: 복호화 → 평문의 SHA-512가
 * 저장된 {@code email_hash}와 같은지 대조(키·평문이 온전한지 증명) → HMAC 계산 → NULL인 행에만 UPDATE.
 * 조건이 {@code email_hmac IS NULL}이라 재실행 안전하고, 앱이 그사이 새로 쓴 행을 덮지 않는다.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class EmailHmacBackfillService {

    private static final int BATCH_SIZE = 200;

    private final JdbcTemplate jdbcTemplate;

    public record Result(int filled, int remainingNull, int mismatched) {
        public boolean complete() {
            return remainingNull == 0 && mismatched == 0;
        }
    }

    /** @param encryptionKey 현재 {@code app.security.email-encryption-key} */
    public Result backfill(String encryptionKey) {
        if (!EmailHasher.hmacConfigured()) {
            throw new IllegalStateException("app.security.email-hash-pepper가 설정되지 않았습니다.");
        }
        TextEncryptor encryptor = EmailEncryption.encryptor(encryptionKey);

        int filled = 0;
        long lastId = 0;
        while (true) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT id, email, email_hash FROM users WHERE email_hmac IS NULL AND id > ? ORDER BY id LIMIT "
                            + BATCH_SIZE, lastId);
            if (rows.isEmpty()) {
                break;
            }
            for (Map<String, Object> row : rows) {
                long id = ((Number) row.get("id")).longValue();
                lastId = id;
                String plain = decryptOrNull(encryptor, (String) row.get("email"));
                if (plain == null) {
                    throw new IllegalStateException("복호화되지 않는 행이 있습니다. 키가 맞는지 확인하세요. userId=" + id);
                }
                if (!EmailHasher.sha512Hex(plain).equals(row.get("email_hash"))) {
                    throw new IllegalStateException("복호화한 값이 저장된 email_hash와 일치하지 않습니다. userId=" + id);
                }
                filled += jdbcTemplate.update(
                        "UPDATE users SET email_hmac = ? WHERE id = ? AND email_hmac IS NULL",
                        EmailHasher.hmacHex(plain), id);
            }
        }

        Integer remaining = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE email_hmac IS NULL", Integer.class);
        int mismatched = countMismatched(encryptor);
        Result result = new Result(filled, remaining == null ? 0 : remaining, mismatched);
        log.info("이메일 HMAC 백필 완료 — 채움 {}건, 남은 NULL {}건, 불일치 {}건", result.filled(), result.remainingNull(),
                result.mismatched());
        return result;
    }

    /** 전체 행을 다시 훑어 저장된 HMAC이 평문에서 다시 계산한 값과 같은지 확인한다. */
    private int countMismatched(TextEncryptor encryptor) {
        int mismatched = 0;
        long lastId = 0;
        while (true) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT id, email, email_hmac FROM users WHERE id > ? ORDER BY id LIMIT " + BATCH_SIZE, lastId);
            if (rows.isEmpty()) {
                return mismatched;
            }
            for (Map<String, Object> row : rows) {
                long id = ((Number) row.get("id")).longValue();
                lastId = id;
                String plain = decryptOrNull(encryptor, (String) row.get("email"));
                if (plain == null || !EmailHasher.hmacHex(plain).equals(row.get("email_hmac"))) {
                    mismatched++;
                    log.warn("이메일 HMAC 검증 실패 — 재계산한 값과 다릅니다. userId={}", id);
                }
            }
        }
    }

    private static String decryptOrNull(TextEncryptor encryptor, String cipher) {
        try {
            return encryptor.decrypt(cipher);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
