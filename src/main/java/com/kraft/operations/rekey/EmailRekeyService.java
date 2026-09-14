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
 * 저장된 모든 이메일을 옛 키에서 새 키로 다시 암호화한다.
 *
 * <h3>왜 필요한가</h3>
 * 키가 유출되면 바꿔야 한다. 그런데 {@code users.email}은 키로 암호화되어 있고 조회에 쓰는
 * {@code email_hash}는 <b>키를 쓰지 않는다</b>. 그래서 키만 갈아 끼우면 <b>로그인은 계속 되는데
 * 저장된 이메일만 전부 읽을 수 없는</b> 상태가 되고, 그 사실은 한참 뒤에야 드러난다.
 *
 * <h3>왜 엔티티가 아니라 {@link JdbcTemplate}인가</h3>
 * 컨텍스트의 {@code EmailAttributeConverter}는 <b>새 키</b>로 만들어진다. 엔티티로 읽는 순간
 * 아직 변환되지 않은 행에서 복호화 예외가 난다. 이 도구는 암호문을 <b>문자열 그대로</b> 읽고 써야 한다.
 *
 * <h3>한 행을 처리하는 절차</h3>
 * <ol>
 * <li>새 키로 복호화해 본다 — 되면 이미 변환된 행이므로 건너뛴다(<b>재실행 안전</b>)</li>
 * <li>옛 키로 복호화한다 — 실패하면 그 id를 알리고 <b>멈춘다</b></li>
 * <li>평문의 SHA-512가 그 행의 {@code email_hash}와 같은지 확인한다</li>
 * <li>새 키로 암호화해 UPDATE 한다</li>
 * </ol>
 * 3번이 이 도구의 핵심이다. {@code email_hash}는 키와 무관해 교체 전후로 변하지 않는 불변값이라,
 * 이것과 대조하면 "평문이 온전히 살아남았는가"를 <b>주소를 한 번도 꺼내 보지 않고</b> 증명할 수 있다.
 * <p>
 * 행마다 즉시 커밋한다(오토커밋). 전체를 한 트랜잭션으로 묶는 것보다 중단에 강하다 — 중간에
 * 프로세스가 죽어도 1번 덕분에 다시 돌리면 남은 것만 처리한다. 키 교체 도중 중단은 <b>일어날
 * 일</b>이라고 보고 설계했다.
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

                if (rekeyRow(id, (String) row.get("email"), (String) row.get("email_hash"), from, to)) {
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

    /**
     * 두 키가 같으면 전 계정을 같은 키로 다시 쓰는 무의미한 작업이 된다. 옛 키 환경변수를
     * 깜빡하고 실행했을 때 조용히 "성공"으로 끝나는 것이 가장 나쁜 결과라, 아무것도 하지 않고 막는다.
     */
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
                "SELECT id, email, email_hash FROM users WHERE id > ? ORDER BY id LIMIT " + BATCH_SIZE,
                lastId);
    }

    /** @return 이 행을 실제로 변환했으면 true, 이미 새 키로 되어 있어 건너뛰었으면 false */
    private boolean rekeyRow(long id, String cipher, String emailHash, TextEncryptor from, TextEncryptor to) {
        if (decryptOrNull(to, cipher) != null) {
            return false;
        }

        String plain = decryptOrNull(from, cipher);
        if (plain == null) {
            // 여기서 멈추지 않으면 나머지를 조용히 덮어써 원인을 영영 알 수 없게 된다.
            throw new IllegalStateException(
                    "어느 키로도 복호화되지 않는 행이 있습니다. 옛 키가 맞는지 확인하세요. userId=" + id);
        }
        if (!EmailHasher.sha512Hex(plain).equals(emailHash)) {
            throw new IllegalStateException(
                    "복호화한 값이 저장된 email_hash와 일치하지 않습니다. userId=" + id);
        }

        jdbcTemplate.update("UPDATE users SET email = ? WHERE id = ?", to.encrypt(plain), id);
        return true;
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
     * @param converted   이번 실행에서 새 키로 다시 쓴 행
     * @param alreadyDone 이미 새 키였던 행. 재실행이라면 여기에 쌓인다
     */
    public record Result(int converted, int alreadyDone) {

        public int total() {
            return converted + alreadyDone;
        }
    }
}
