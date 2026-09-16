package com.kraft.comment.domain;

import com.kraft.config.JpaConfig;
import com.kraft.post.domain.Post;
import com.kraft.post.domain.PostRepository;
import com.kraft.user.domain.EmailAttributeConverter;
import com.kraft.user.domain.Role;
import com.kraft.user.domain.User;
import com.kraft.user.domain.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link CommentRepository} 통합 테스트. {@code @DataJpaTest}는 Entity/Repository 관련 빈만
 * 스캔하므로, {@code @EnableJpaAuditing}이 선언된 {@link JpaConfig}는 별도로 {@code @Import}해야
 * 감사 필드({@code createdAt})가 실제로 채워지는지 검증할 수 있다(PostRepositoryTest와 동일 패턴).
 */
@DataJpaTest
@Import({JpaConfig.class, EmailAttributeConverter.class})
class CommentRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private UserRepository userRepository;

    private User user;
    private Post post;
    private Post otherPost;

    @BeforeEach
    void setUp() {
        user = userRepository.save(
                User.builder().name("tester").email("tester@example.com").password("pw").role(Role.USER).build());
        post = postRepository.save(Post.builder().title("게시글").content("내용").user(user).build());
        otherPost = postRepository.save(Post.builder().title("다른 게시글").content("다른 내용").user(user).build());
    }

    @Test
    @DisplayName("findAllByPostIdAsc: 해당 게시글의 댓글만 id 오름차순으로 조회하고, 다른 게시글 댓글은 제외한다")
    void findAllByPostIdAsc_filtersByPostIdAndSortsAscending() {
        Comment first = commentRepository.save(Comment.builder().content("첫 댓글").post(post).user(user).build());
        Comment second = commentRepository.save(Comment.builder().content("둘째 댓글").post(post).user(user).build());
        commentRepository.save(Comment.builder().content("다른 게시글 댓글").post(otherPost).user(user).build());
        em.flush();
        em.clear();

        List<Comment> result = commentRepository.findAllByPostIdAsc(post.getId());

        assertThat(result).extracting(Comment::getId)
                .containsExactly(first.getId(), second.getId());
    }

    @Test
    @DisplayName("findAllByPostIdAsc: JOIN FETCH로 작성자가 함께 조회되어 지연로딩 예외가 없다")
    void findAllByPostIdAsc_fetchesAuthorEagerlyWithJoinFetch() {
        commentRepository.save(Comment.builder().content("댓글").post(post).user(user).build());
        em.flush();
        em.clear();

        List<Comment> result = commentRepository.findAllByPostIdAsc(post.getId());

        assertThat(result.get(0).getUser().getName()).isEqualTo("tester");
    }

    @Test
    @DisplayName("deleteAllByPostId: 해당 게시글의 댓글만 삭제하고 다른 게시글 댓글은 남긴다")
    void deleteAllByPostId_deletesOnlyCommentsOfGivenPost() {
        commentRepository.save(Comment.builder().content("삭제될 댓글").post(post).user(user).build());
        Comment untouched = commentRepository.save(Comment.builder().content("남을 댓글").post(otherPost).user(user).build());
        em.flush();
        em.clear();

        commentRepository.deleteAllByPostId(post.getId());
        em.flush();

        assertThat(commentRepository.findAllByPostIdAsc(post.getId())).isEmpty();
        assertThat(commentRepository.findById(untouched.getId())).isPresent();
    }

    @Test
    @DisplayName("[회귀 방지] 댓글이 있는 게시글도 댓글을 먼저 지우면 FK 위반 없이 삭제할 수 있다")
    void deletePost_succeedsWithoutForeignKeyViolation_whenCommentsDeletedFirst() {
        commentRepository.save(Comment.builder().content("댓글").post(post).user(user).build());
        em.flush();
        // 벌크 JPQL DELETE는 영속성 컨텍스트(1차 캐시)를 갱신하지 않는다 — DB에서는 이미 지워진
        // Comment를 세션이 여전히 "관리 중"으로 들고 있으면, 뒤이은 post 삭제 flush에서
        // Hibernate가 그 엔티티의 연관관계를 다시 점검하다 엉뚱한 오류를 낸다. 비워서 실제
        // 운영 코드(PostService.delete)처럼 남겨 둔 엔티티 없이 진행한다.
        em.clear();

        commentRepository.deleteAllByPostId(post.getId());
        postRepository.delete(post);
        em.flush();

        assertThat(postRepository.findById(post.getId())).isEmpty();
    }

    @Test
    @DisplayName("countByPostIdIn: 여러 게시글의 댓글 수를 postId → count 맵으로 한 번에 반환한다")
    void countByPostIdIn_returnsCommentCountMapByPostIds() {
        commentRepository.save(Comment.builder().content("댓글1").post(post).user(user).build());
        commentRepository.save(Comment.builder().content("댓글2").post(post).user(user).build());
        commentRepository.save(Comment.builder().content("다른글 댓글").post(otherPost).user(user).build());
        em.flush();
        em.clear();

        Map<Long, Long> counts = commentRepository.countByPostIdIn(List.of(post.getId(), otherPost.getId()));

        assertThat(counts.get(post.getId())).isEqualTo(2L);
        assertThat(counts.get(otherPost.getId())).isEqualTo(1L);
    }

    @Test
    @DisplayName("countByPostIdIn: 댓글이 없는 게시글 ID는 결과 맵에 아예 없다")
    void countByPostIdIn_excludesPostIdsWithoutCommentsFromMap() {
        Map<Long, Long> counts = commentRepository.countByPostIdIn(List.of(post.getId()));

        assertThat(counts).doesNotContainKey(post.getId());
    }

    @Test
    @DisplayName("countByPostIdIn: 빈 목록이면 쿼리 없이 빈 맵을 반환한다")
    void countByPostIdIn_returnsEmptyMap_whenPostIdsEmpty() {
        assertThat(commentRepository.countByPostIdIn(List.of())).isEmpty();
    }

    @Test
    @DisplayName("저장하면 BaseEntity의 createdAt이 자동으로 채워진다 (JpaConfig의 @EnableJpaAuditing)")
    void save_automaticallyPopulatesCreatedAtAuditField() {
        Comment saved = commentRepository.save(Comment.builder().content("댓글").post(post).user(user).build());
        em.flush();

        assertThat(saved.getCreatedAt()).isNotNull();
    }
}
