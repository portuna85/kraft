package com.kraft.domain.post;

import com.kraft.config.JpaConfig;
import com.kraft.domain.user.EmailAttributeConverter;
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
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({JpaConfig.class, EmailAttributeConverter.class})
class PostLikeRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private PostLikeRepository postLikeRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private UserRepository userRepository;

    private User author;
    private User liker;
    private Post post;

    @BeforeEach
    void setUp() {
        author = userRepository.save(
                User.builder().name("author").email("author@example.com").password("pw").role(Role.USER).build());
        liker = userRepository.save(
                User.builder().name("liker").email("liker@example.com").password("pw").role(Role.USER).build());
        post = postRepository.save(Post.builder().title("게시글").content("내용").user(author).build());
    }

    @Test
    @DisplayName("existsByPostIdAndUserId: 저장한 추천만 true, 다른 사용자는 false")
    void existsByPostIdAndUserId_returnsTrueOnlyForExistingLike() {
        postLikeRepository.save(PostLike.builder().post(post).user(liker).build());
        em.flush();
        em.clear();

        assertThat(postLikeRepository.existsByPostIdAndUserId(post.getId(), liker.getId())).isTrue();
        assertThat(postLikeRepository.existsByPostIdAndUserId(post.getId(), author.getId())).isFalse();
    }

    @Test
    @DisplayName("같은 사용자가 같은 게시글을 두 번 추천하면 유니크 제약 위반으로 실패한다")
    void save_duplicateLikeBySameUser_violatesUniqueConstraint() {
        postLikeRepository.save(PostLike.builder().post(post).user(liker).build());
        em.flush();

        assertThatThrownBy(() -> {
            postLikeRepository.save(PostLike.builder().post(post).user(liker).build());
            em.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("deleteByPostIdAndUserId: 해당 사용자의 추천만 지운다")
    void deleteByPostIdAndUserId_deletesOnlyTargetUserLike() {
        postLikeRepository.save(PostLike.builder().post(post).user(liker).build());
        em.flush();
        em.clear();

        postLikeRepository.deleteByPostIdAndUserId(post.getId(), liker.getId());
        em.flush();

        assertThat(postLikeRepository.existsByPostIdAndUserId(post.getId(), liker.getId())).isFalse();
    }

    @Test
    @DisplayName("countByPostId: 해당 게시글의 추천 수를 센다")
    void countByPostId_countsLikesForGivenPost() {
        postLikeRepository.save(PostLike.builder().post(post).user(liker).build());
        postLikeRepository.save(PostLike.builder().post(post).user(author).build());
        em.flush();
        em.clear();

        assertThat(postLikeRepository.countByPostId(post.getId())).isEqualTo(2L);
    }

    @Test
    @DisplayName("[회귀 방지] 추천이 있는 게시글도 추천을 먼저 지우면 FK 위반 없이 삭제할 수 있다")
    void deletePost_succeedsWithoutForeignKeyViolation_whenLikesDeletedFirst() {
        postLikeRepository.save(PostLike.builder().post(post).user(liker).build());
        em.flush();

        postLikeRepository.deleteAllByPostId(post.getId());
        postRepository.delete(post);
        em.flush();

        assertThat(postRepository.findById(post.getId())).isEmpty();
    }
}
