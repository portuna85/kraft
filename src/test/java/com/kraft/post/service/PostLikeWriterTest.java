package com.kraft.post.service;

import com.kraft.post.domain.Post;
import com.kraft.post.domain.PostLikeRepository;
import com.kraft.post.domain.PostRepository;
import com.kraft.user.domain.Role;
import com.kraft.user.domain.User;
import com.kraft.user.domain.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * B09: {@code insert}는 유니크 제약 위반이든 FK 위반이든 아무것도 삼키지 않고 그대로 던진다
 * (같은 트랜잭션 안에서 삼키면 rollback-only 때문에 {@code UnexpectedRollbackException}이
 * 난다). "삼켜도 되는 예외인가"는 {@code isDuplicateLikeConstraint}가 실제 DB 제약 이름으로
 * 판정하며, 그 판정을 실제 DB로 검증한다.
 */
@SpringBootTest
class PostLikeWriterTest {

    @Autowired
    private PostLikeWriter postLikeWriter;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private PostLikeRepository postLikeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User author;
    private User liker;
    private Post post;

    @BeforeEach
    void setUp() {
        postLikeRepository.deleteAll();
        postRepository.deleteAll();
        userRepository.deleteAll();

        author = userRepository.save(User.builder()
                .name("like-author").email("like-author@example.com").password("encoded").role(Role.USER).build());
        liker = userRepository.save(User.builder()
                .name("liker").email("liker@example.com").password("encoded").role(Role.USER).build());
        post = postRepository.save(Post.builder().title("제목").content("내용").user(author).build());
    }

    @Test
    @DisplayName("B09: 같은 (게시글, 사용자) 추천을 두 번 insert하면 유니크 제약 위반이 그대로 올라오고, isDuplicateLikeConstraint는 true다")
    void insert_duplicateLike_throwsAndIsRecognizedAsDuplicate() {
        postLikeWriter.insert(post, liker);

        assertThatThrownBy(() -> postLikeWriter.insert(post, liker))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(e -> assertThat(postLikeWriter.isDuplicateLikeConstraint(
                        (DataIntegrityViolationException) e)).isTrue());
        assertThat(postLikeRepository.countByPostId(post.getId())).isEqualTo(1L);
    }

    @Test
    @DisplayName("B09: 부모 게시글이 막 삭제된 뒤의 FK 위반은 그대로 올라오고, isDuplicateLikeConstraint는 false다")
    void insert_whenParentPostDeletedConcurrently_throwsAndIsNotRecognizedAsDuplicate() {
        // 다른 요청이 이 게시글을 동시에 지운 상황을 흉내 낸다. 추천 삭제 없이 게시글 행만
        // 직접 지워, insert가 참조할 부모 행이 사라지게 한다.
        jdbcTemplate.update("DELETE FROM posts WHERE id = ?", post.getId());

        assertThatThrownBy(() -> postLikeWriter.insert(post, liker))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(e -> assertThat(postLikeWriter.isDuplicateLikeConstraint(
                        (DataIntegrityViolationException) e)).isFalse());
    }

    @Test
    @DisplayName("B09: countByPostId는 방금 커밋된 추천을 곧바로 반영한다")
    void countByPostId_reflectsJustCommittedInsert() {
        postLikeWriter.insert(post, liker);

        assertThat(postLikeWriter.countByPostId(post.getId())).isEqualTo(1L);
    }
}
