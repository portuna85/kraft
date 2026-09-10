package com.kraft.domain.comment;

import com.kraft.config.JpaConfig;
import com.kraft.domain.post.Post;
import com.kraft.domain.post.PostRepository;
import com.kraft.domain.user.Role;
import com.kraft.domain.user.User;
import com.kraft.domain.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link CommentRepository} 통합 테스트. {@code @DataJpaTest}는 Entity/Repository 관련 빈만
 * 스캔하므로, {@code @EnableJpaAuditing}이 선언된 {@link JpaConfig}는 별도로 {@code @Import}해야
 * 감사 필드({@code createdAt})가 실제로 채워지는지 검증할 수 있다(PostRepositoryTest와 동일 패턴).
 */
@DataJpaTest
@Import(JpaConfig.class)
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
    void findAllByPostIdAsc_는_postId로_필터링하고_오름차순_정렬한다() {
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
    void findAllByPostIdAsc_는_JOIN_FETCH로_작성자를_함께_조회한다() {
        commentRepository.save(Comment.builder().content("댓글").post(post).user(user).build());
        em.flush();
        em.clear();

        List<Comment> result = commentRepository.findAllByPostIdAsc(post.getId());

        assertThat(result.get(0).getUser().getName()).isEqualTo("tester");
    }

    @Test
    @DisplayName("저장하면 BaseEntity의 createdAt이 자동으로 채워진다 (JpaConfig의 @EnableJpaAuditing)")
    void 감사필드가_자동으로_채워진다() {
        Comment saved = commentRepository.save(Comment.builder().content("댓글").post(post).user(user).build());
        em.flush();

        assertThat(saved.getCreatedAt()).isNotNull();
    }
}
