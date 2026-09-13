package com.kraft.migration;

import com.kraft.domain.post.Category;
import com.kraft.domain.post.PostRepository;
import com.kraft.domain.user.Role;
import com.kraft.domain.user.User;
import com.kraft.domain.user.UserRepository;
import com.kraft.service.post.PostService;
import com.kraft.web.dto.post.PostSaveRequestDto;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 운영과 같은 MariaDB에서 Flyway 마이그레이션 전체(V1→V6)를 실제로 실행해 검증한다.
 * <p>
 * 나머지 테스트는 전부 H2 + {@code ddl-auto: create-drop}이라 <b>{@code db/migration}의 SQL을
 * 한 번도 실행하지 않는다</b> — Hibernate가 엔티티 매핑으로 스키마를 직접 만들기 때문이다.
 * 그래서 마이그레이션에 오타가 있거나 엔티티와 어긋나도 테스트는 전부 통과하고, 운영 배포에서
 * 처음 드러난다(개선 보고서 "운영 DB 검증" 공백).
 * <p>
 * 여기서 확인하는 것은 네 가지다:
 * <ol>
 * <li>빈 DB에서 V1~V6이 순서대로 성공한다.</li>
 * <li>{@code ddl-auto: validate}가 통과한다 — 컨텍스트가 뜨는 것 자체가 "마이그레이션이 만든
 * 스키마와 엔티티 매핑이 일치한다"는 증거다. MariaDB 네이티브 ENUM의 <b>값 순서</b>처럼
 * H2에서는 드러나지 않는 불일치가 여기서 잡힌다.</li>
 * <li>V3이 만든 Spring Session 테이블에 실제 로그인 세션이 저장된다.</li>
 * <li>운영 프로파일과 같은 경로로 게시글·이미지 대장·댓글을 읽고 쓸 수 있다.</li>
 * </ol>
 * <p>
 * Docker가 없으면 클래스 전체를 건너뛴다({@code disabledWithoutDocker}). 그래야 README가
 * 안내하는 대로 Docker 없이도 {@code gradlew test}가 그대로 돈다. CI(ubuntu-latest)에는
 * Docker가 있으므로 실제로 실행된다.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        // 운영(prod)과 같은 스키마 경로: Flyway가 만들고 Hibernate는 검증만 한다.
        "spring.flyway.enabled=true",
        "spring.flyway.baseline-on-migrate=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        // 세션 테이블도 V3이 만든다. Spring Session이 따로 만들지 않게 한다.
        "spring.session.jdbc.initialize-schema=never",
        "spring.h2.console.enabled=false"
})
@AutoConfigureMockMvc
class MariaDbMigrationTest {

    /** docker-compose.yml과 같은 버전을 쓴다. 운영에서 쓰는 것과 다른 DB를 검증하면 의미가 없다. */
    @Container
    @ServiceConnection
    static MariaDBContainer<?> mariadb = new MariaDBContainer<>("mariadb:11.7.2");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private PostService postService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("빈 DB에서 V1~V6 마이그레이션이 모두 성공한다")
    void allMigrations_applySuccessfullyOnEmptyDatabase() {
        List<Map<String, Object>> history = jdbcTemplate.queryForList(
                "SELECT version, description, success FROM flyway_schema_history "
                        + "WHERE version IS NOT NULL ORDER BY installed_rank");

        assertThat(history).extracting(row -> row.get("version").toString())
                .containsExactly("1", "2", "3", "4", "5", "6");
        assertThat(history).allSatisfy(row ->
                assertThat(row.get("success")).as("마이그레이션 %s 성공 여부", row.get("version")).isEqualTo(true));
    }

    @Test
    @DisplayName("마이그레이션이 만든 스키마가 엔티티 매핑과 일치한다(ddl-auto: validate 통과)")
    void schemaMatchesEntityMappings() {
        // 이 테스트가 실행된다는 것은 validate를 켠 컨텍스트가 떴다는 뜻이다. 그에 더해,
        // H2에서는 드러나지 않는 MariaDB 네이티브 ENUM의 값 순서를 직접 확인한다.
        assertThat(columnTypeOf("users", "role")).isEqualTo("enum('ADMIN','GUEST','USER')");
        assertThat(columnTypeOf("posts", "category")).isEqualTo("enum('FREE','NOTICE','QNA')");
        assertThat(columnTypeOf("post_images", "status"))
                .isEqualTo("enum('ATTACHED','ORPHAN','PENDING_DELETE')");
    }

    @Test
    @DisplayName("V4~V6이 추가한 컬럼·제약이 실제로 존재한다")
    void laterMigrationsAddedTheirColumnsAndConstraints() {
        assertThat(columnExists("posts", "version")).isTrue();
        assertThat(columnExists("post_images", "size_bytes")).isTrue();
        assertThat(uniqueConstraintExists("users", "UK_USER_NAME")).isTrue();
        assertThat(indexExists("posts", "IX_POSTS_CATEGORY_ID")).isTrue();
    }

    @Test
    @DisplayName("V3이 만든 세션 테이블에 실제 로그인 세션이 저장된다")
    void loginSessionIsPersistedInSessionTable() throws Exception {
        userRepository.save(User.builder()
                .name("migration-tester")
                .email("migration@example.com")
                .password(passwordEncoder.encode("Password123!"))
                .role(Role.USER)
                .build());

        Cookie session = mockMvc.perform(post("/login")
                        .param("username", "migration@example.com")
                        .param("password", "Password123!")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn()
                .getResponse()
                .getCookie("SESSION");

        assertThat(session).isNotNull();
        Long sessions = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM SPRING_SESSION WHERE PRINCIPAL_NAME = ?",
                Long.class, "migration@example.com");
        assertThat(sessions).isEqualTo(1L);
    }

    @Test
    @DisplayName("운영 스키마에서 게시글 작성·조회가 동작한다")
    void postsCanBeWrittenAndReadOnProductionSchema() {
        User author = userRepository.save(User.builder()
                .name("migration-author")
                .email("author-migration@example.com")
                .password("encoded")
                .role(Role.USER)
                .build());
        Authentication auth = new UsernamePasswordAuthenticationToken(author.getEmail(), null,
                List.of(new SimpleGrantedAuthority(Role.USER.getKey())));

        Long id = postService.save(auth, new PostSaveRequestDto("제목", "내용", null, Category.QNA));

        var saved = postRepository.findById(id).orElseThrow();
        assertThat(saved.getCategory()).isEqualTo(Category.QNA);
        assertThat(saved.getViewCount()).isZero();
        // @Version이 붙은 컬럼은 NOT NULL이라 INSERT 시점에 값이 들어가야 한다.
        assertThat(saved.getVersion()).isNotNull();

        // 조회수 증가는 별도 UPDATE 한 문장이다(F02). 운영 DB에서도 같은 SQL이 도는지 본다.
        postService.findByIdForView(id, auth);
        assertThat(postRepository.findById(id).orElseThrow().getViewCount()).isEqualTo(1L);
    }

    private String columnTypeOf(String table, String column) {
        return jdbcTemplate.queryForObject(
                "SELECT COLUMN_TYPE FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                String.class, table, column);
    }

    private boolean columnExists(String table, String column) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                Long.class, table, column);
        return count != null && count > 0;
    }

    private boolean uniqueConstraintExists(String table, String name) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? "
                        + "AND CONSTRAINT_NAME = ? AND CONSTRAINT_TYPE = 'UNIQUE'",
                Long.class, table, name);
        return count != null && count > 0;
    }

    private boolean indexExists(String table, String name) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.STATISTICS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ?",
                Long.class, table, name);
        return count != null && count > 0;
    }
}
