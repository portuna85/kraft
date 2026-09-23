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
import org.springframework.data.domain.PageRequest;

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
    @DisplayName("deleteAllByPostId: 해당 게시글의 댓글만 삭제하고 다른 게시글 댓글은 남긴다")
    void deleteAllByPostId_deletesOnlyCommentsOfGivenPost() {
        commentRepository.save(Comment.builder().content("삭제될 댓글").post(post).user(user).build());
        Comment untouched = commentRepository.save(Comment.builder().content("남을 댓글").post(otherPost).user(user).build());
        em.flush();
        em.clear();

        commentRepository.deleteAllByPostId(post.getId());
        em.flush();

        assertThat(commentRepository.countByPostId(post.getId())).isZero();
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
    @DisplayName("[회귀 방지] 답글이 있는 게시글은 답글을 먼저 지운 뒤에야 댓글 전체를 지울 수 있다")
    void deletePost_withReplies_requiresDeletingRepliesBeforeAllComments() {
        Comment parent = commentRepository.save(Comment.builder().content("부모").post(post).user(user).build());
        commentRepository.save(Comment.builder().content("답글").post(post).user(user).parent(parent).build());
        em.flush();
        em.clear();

        // PostService.delete()와 같은 순서 — 답글을 먼저 지우지 않고 deleteAllByPostId만
        // 실행하면 H2에서는 통과하더라도, 실제 운영 DB(MariaDB/InnoDB)에서는 부모 행이 자신의
        // 답글보다 먼저 삭제되며 FK_COMMENTS_PARENT 위반이 날 수 있다
        // (PostDeleteWithRepliesMariaDbTest에서 실제 MariaDB로 확인).
        commentRepository.deleteRepliesByPostId(post.getId());
        commentRepository.deleteAllByPostId(post.getId());
        postRepository.delete(post);
        em.flush();

        assertThat(postRepository.findById(post.getId())).isEmpty();
        assertThat(commentRepository.countByPostId(post.getId())).isZero();
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
    @DisplayName("F13: findPageByPostIdAsc: afterId가 null이면 처음부터 id 오름차순으로 pageable 개수만큼 반환한다")
    void findPageByPostIdAsc_withNullAfterId_returnsFromBeginning() {
        Comment first = commentRepository.save(Comment.builder().content("1").post(post).user(user).build());
        Comment second = commentRepository.save(Comment.builder().content("2").post(post).user(user).build());
        commentRepository.save(Comment.builder().content("3").post(post).user(user).build());
        em.flush();
        em.clear();

        List<Comment> page = commentRepository.findPageByPostIdAsc(post.getId(), null, PageRequest.of(0, 2));

        assertThat(page).extracting(Comment::getId).containsExactly(first.getId(), second.getId());
    }

    @Test
    @DisplayName("F13: findPageByPostIdAsc: afterId 이후 댓글만 id 오름차순으로 반환한다")
    void findPageByPostIdAsc_withAfterId_returnsOnlyLaterComments() {
        Comment first = commentRepository.save(Comment.builder().content("1").post(post).user(user).build());
        Comment second = commentRepository.save(Comment.builder().content("2").post(post).user(user).build());
        Comment third = commentRepository.save(Comment.builder().content("3").post(post).user(user).build());
        em.flush();
        em.clear();

        List<Comment> page = commentRepository.findPageByPostIdAsc(post.getId(), first.getId(), PageRequest.of(0, 10));

        assertThat(page).extracting(Comment::getId).containsExactly(second.getId(), third.getId());
    }

    /**
     * 2단계 댓글: DB에 cascade를 걸지 않았으므로(Comment.parent 주석 참고) 최상위 댓글을
     * 지우기 전에 답글을 먼저 이 메서드로 지워야 한다. 다른 부모의 답글은 건드리지 않는다.
     */
    @Test
    @DisplayName("2단계: deleteAllByParentId는 그 부모의 답글만 지우고 다른 부모의 답글은 남긴다")
    void deleteAllByParentId_deletesOnlyRepliesOfGivenParent() {
        Comment parentA = commentRepository.save(Comment.builder().content("부모A").post(post).user(user).build());
        Comment parentB = commentRepository.save(Comment.builder().content("부모B").post(post).user(user).build());
        commentRepository.save(Comment.builder().content("A의 답글").post(post).user(user).parent(parentA).build());
        Comment replyB = commentRepository.save(
                Comment.builder().content("B의 답글").post(post).user(user).parent(parentB).build());
        em.flush();
        em.clear();

        commentRepository.deleteAllByParentId(parentA.getId());
        em.flush();

        assertThat(commentRepository.findRepliesByParentIdAsc(parentA.getId(), null, PageRequest.of(0, 500))).isEmpty();
        assertThat(commentRepository.findById(replyB.getId())).isPresent();
    }

    /** 2단계 댓글: 최상위 댓글만 커서 페이지네이션 대상이어야 한다 — 답글이 섞여 나오면 안 된다. */
    @Test
    @DisplayName("2단계: findPageByPostIdAsc는 답글을 건너뛰고 최상위 댓글만 반환한다")
    void findPageByPostIdAsc_skipsReplies() {
        Comment topLevel = commentRepository.save(Comment.builder().content("최상위").post(post).user(user).build());
        commentRepository.save(Comment.builder().content("답글").post(post).user(user).parent(topLevel).build());
        em.flush();
        em.clear();

        List<Comment> page = commentRepository.findPageByPostIdAsc(post.getId(), null, PageRequest.of(0, 10));

        assertThat(page).extracting(Comment::getId).containsExactly(topLevel.getId());
    }

    @Test
    @DisplayName("2단계: findRepliesByParentIdAsc는 한 부모의 답글을 id 오름차순으로 가져오고, 다른 부모의 답글은 섞이지 않는다")
    void findRepliesByParentIdAsc_returnsOnlyThatParentsRepliesInOrder() {
        Comment parentA = commentRepository.save(Comment.builder().content("부모A").post(post).user(user).build());
        Comment parentB = commentRepository.save(Comment.builder().content("부모B").post(post).user(user).build());
        Comment replyA1 = commentRepository.save(
                Comment.builder().content("A-1").post(post).user(user).parent(parentA).build());
        commentRepository.save(Comment.builder().content("B-1").post(post).user(user).parent(parentB).build());
        Comment replyA2 = commentRepository.save(
                Comment.builder().content("A-2").post(post).user(user).parent(parentA).build());
        em.flush();
        em.clear();

        List<Comment> replies = commentRepository.findRepliesByParentIdAsc(parentA.getId(), null, PageRequest.of(0, 500));

        assertThat(replies).extracting(Comment::getId).containsExactly(replyA1.getId(), replyA2.getId());
        assertThat(replies.get(0).getUser().getName())
                .as("JOIN FETCH로 작성자가 함께 와야 지연로딩 예외가 없다").isEqualTo("tester");
    }

    /** COR-05: afterId 커서 이후의 답글만 가져온다 — "답글 더 보기"가 이 커서로 이어받는다. */
    @Test
    @DisplayName("COR-05: findRepliesByParentIdAsc는 afterId보다 큰 답글만 가져온다")
    void findRepliesByParentIdAsc_withAfterId_returnsOnlyLaterReplies() {
        Comment parent = commentRepository.save(Comment.builder().content("부모").post(post).user(user).build());
        Comment reply1 = commentRepository.save(
                Comment.builder().content("답글1").post(post).user(user).parent(parent).build());
        Comment reply2 = commentRepository.save(
                Comment.builder().content("답글2").post(post).user(user).parent(parent).build());
        em.flush();
        em.clear();

        List<Comment> replies = commentRepository.findRepliesByParentIdAsc(
                parent.getId(), reply1.getId(), PageRequest.of(0, 500));

        assertThat(replies).extracting(Comment::getId).containsExactly(reply2.getId());
    }

    /** 페이지 크기를 넘는 답글도 pageable로 잘라낼 수 있다 — "답글 더 보기"가 한 번에 내려주는 개수를 제한한다. */
    @Test
    @DisplayName("findRepliesByParentIdAsc는 pageable 크기를 넘는 답글은 잘라낸다")
    void findRepliesByParentIdAsc_capsResultsToPageableSize() {
        Comment parent = commentRepository.save(Comment.builder().content("부모").post(post).user(user).build());
        for (int i = 0; i < 5; i++) {
            commentRepository.save(Comment.builder().content("답글" + i).post(post).user(user).parent(parent).build());
        }
        em.flush();
        em.clear();

        List<Comment> replies = commentRepository.findRepliesByParentIdAsc(parent.getId(), null, PageRequest.of(0, 3));

        assertThat(replies).hasSize(3);
    }

    @Test
    @DisplayName("저장하면 BaseEntity의 createdAt이 자동으로 채워진다 (JpaConfig의 @EnableJpaAuditing)")
    void save_automaticallyPopulatesCreatedAtAuditField() {
        Comment saved = commentRepository.save(Comment.builder().content("댓글").post(post).user(user).build());
        em.flush();

        assertThat(saved.getCreatedAt()).isNotNull();
    }
}
