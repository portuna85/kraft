package com.kraft.community.comment;

import static org.assertj.core.api.Assertions.assertThat;

import com.kraft.community.post.CommunityPost;
import com.kraft.community.post.CommunityPostRepository;
import com.kraft.community.user.CommunityUser;
import com.kraft.community.user.CommunityUserRepository;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 게시글 삭제 시 댓글이 함께 삭제되는지(V16 ON DELETE CASCADE)는 실제 FK 제약이 있는
 * DB에서만 재현된다 — 테스트 프로필은 Hibernate ddl-auto=create-drop으로 스키마를
 * 만들어(Flyway 미사용) FK가 없으므로, Flyway 마이그레이션이 실제로 적용되는
 * Testcontainers MariaDB로 검증한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Testcontainers
@DisplayName("게시글 삭제 시 댓글 cascade 삭제 (실 MariaDB)")
class CommunityCommentCascadeDeleteTest {

    @Container
    static MariaDBContainer<?> mariadb = new MariaDBContainer<>("mariadb:11.7")
            .withDatabaseName("kraft_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mariadb::getJdbcUrl);
        registry.add("spring.datasource.username", mariadb::getUsername);
        registry.add("spring.datasource.password", mariadb::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.mariadb.jdbc.Driver");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.defer-datasource-initialization", () -> "false");
        registry.add("spring.sql.init.mode", () -> "never");
    }

    @Autowired
    private CommunityUserRepository communityUserRepository;

    @Autowired
    private CommunityPostRepository communityPostRepository;

    @Autowired
    private CommunityCommentRepository communityCommentRepository;

    @Test
    @DisplayName("게시글을 삭제하면 그 게시글의 댓글도 모두 삭제된다")
    void deletingPost_cascadesToItsComments() {
        CommunityUser author = communityUserRepository.save(new CommunityUser(
                "google", "cascade-test-" + System.nanoTime(), "글쓴이", null, OffsetDateTime.now()));
        CommunityPost post = communityPostRepository.save(new CommunityPost(
                author.getId(), author.getNickname(), "제목", "내용",
                com.kraft.community.post.PostCategory.GENERAL, null, OffsetDateTime.now(), OffsetDateTime.now()));
        CommunityComment topLevel = communityCommentRepository.save(new CommunityComment(
                post.getId(), null, author.getId(), author.getNickname(), "댓글", OffsetDateTime.now()));
        communityCommentRepository.save(new CommunityComment(
                post.getId(), topLevel.getId(), author.getId(), author.getNickname(), "답글", OffsetDateTime.now()));

        assertThat(communityCommentRepository.findByPostId(post.getId(),
                org.springframework.data.domain.PageRequest.of(0, 10)).getTotalElements()).isEqualTo(2);

        communityPostRepository.delete(post);
        communityPostRepository.flush();

        assertThat(communityCommentRepository.findByPostId(post.getId(),
                org.springframework.data.domain.PageRequest.of(0, 10)).getTotalElements()).isZero();
    }
}
