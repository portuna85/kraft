package com.kraft.support;

import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * 매 테스트 메서드 전에 관련 테이블을 FK 안전한 순서로 전부 비운다(개선 보고서 TST-01/TST-04).
 * <p>
 * H2가 컨텍스트마다 무작위 이름({@code jdbc:h2:mem:kraft-${random.uuid}})을 쓰게 되면서,
 * 같은 Spring 테스트 설정을 공유해 컨텍스트 캐시를 재사용하는 클래스들은 여전히 물리적으로
 * 같은 DB를 공유한다 — 그 자체는 의도한 동작이다(컨텍스트를 매번 새로 띄우면 스위트가
 * 훨씬 느려진다). 문제는 각 클래스가 {@code @BeforeEach}에서 자신이 아는 테이블만
 * {@code deleteAll()}해 왔다는 것이다 — 예전에는 고정된 이름 하나(kraft)를 스위트 전체가
 * 공유해, 컨텍스트가 캐시에서 밀려날 때(커넥션 풀이 닫히며 H2 인메모리 DB 자체가 사라짐)
 * 마침 전체가 우연히 초기화되는 일이 잦아 이 허술함이 드러나지 않았다. 컨텍스트마다 DB를
 * 진짜로 분리하자 그 "우연한 전체 초기화"가 사라지고, 한 클래스가 남긴 행(예: 답글이 달린
 * 게시글)을 다른 클래스가 {@code userRepository.deleteAll()}로 지우려다 FK 위반으로 실패하는
 * 사례가 실제로 나왔다.
 * <p>
 * 각 테스트 클래스의 정리 코드를 일일이 넓히는 대신, JUnit5 자동 감지
 * ({@code src/test/resources/junit-platform.properties}와
 * {@code META-INF/services/org.junit.jupiter.api.extension.Extension})로 모든 테스트에
 * 공통 적용한다 — 새 테스트 클래스가 어떤 테이블을 건드리든 이 안전망 밖으로 빠지지 않는다.
 * <p>
 * Spring 컨텍스트가 없는 테스트(순수 단위 테스트)나, MariaDB Testcontainers로 도는 테스트
 * (스스로 컨테이너를 관리하고 스키마 검증 자체가 테스트 대상이라 건드리면 안 된다)는
 * 건너뛴다.
 */
public class GlobalH2CleanupExtension implements BeforeEachCallback {

    /** 자식 → 부모 순서. comments는 자기 참조(답글)라 한 문장으로 전부 지우면 순서 문제가 없다. */
    private static final String[] TABLES_IN_DELETE_ORDER = {
            "comments", "post_likes", "post_images", "reports",
            "email_verification_tokens", "password_reset_tokens",
            "outbox_mails", "session_revocation_tasks", "posts", "users",
    };

    @Override
    public void beforeEach(ExtensionContext context) {
        ApplicationContext applicationContext;
        try {
            applicationContext = SpringExtension.getApplicationContext(context);
        } catch (IllegalStateException e) {
            // Spring 컨텍스트를 쓰지 않는 순수 단위 테스트다.
            return;
        }

        if (!usesH2(applicationContext)) {
            return;
        }

        JdbcTemplate jdbcTemplate;
        try {
            jdbcTemplate = applicationContext.getBean(JdbcTemplate.class);
        } catch (Exception e) {
            return;
        }

        for (String table : TABLES_IN_DELETE_ORDER) {
            try {
                jdbcTemplate.execute("DELETE FROM " + table);
            } catch (Exception e) {
                // 이 컨텍스트의 스키마에 그 테이블이 없거나(슬라이스 테스트) 아직 준비되지
                // 않았을 수 있다 — 그 테이블만 건너뛰고 계속한다.
            }
        }
    }

    private boolean usesH2(ApplicationContext applicationContext) {
        try {
            DataSource dataSource = applicationContext.getBean(DataSource.class);
            try (Connection connection = dataSource.getConnection()) {
                String url = connection.getMetaData().getURL();
                return url != null && url.startsWith("jdbc:h2:");
            }
        } catch (Exception e) {
            return false;
        }
    }
}
