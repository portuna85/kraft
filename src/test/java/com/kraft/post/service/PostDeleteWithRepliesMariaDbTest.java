package com.kraft.post.service;

import com.kraft.comment.domain.Comment;
import com.kraft.comment.domain.CommentRepository;
import com.kraft.post.domain.Post;
import com.kraft.post.domain.PostLikeRepository;
import com.kraft.post.domain.PostRepository;
import com.kraft.support.TestAuthentication;
import com.kraft.user.domain.Role;
import com.kraft.user.domain.User;
import com.kraft.user.domain.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;

import java.time.LocalDateTime;


import com.kraft.support.MariaDbIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 답글이 있는 게시글 삭제와 자기참조 FK의 회귀 테스트. 게시글 삭제는 소프트 삭제이므로 자기참조 FK 순서는 보관 기간 뒤의 영구 삭제({@code PostService.purge})에서 확인한다.
 * H2는 {@code FK_COMMENTS_PARENT} 위반을 문장 끝에서만 검사해 삭제 순서 문제를 재현하지 못하므로(CommentRepositoryTest), 실제 InnoDB로 답글이 달린 게시글을 두 경로(작성자 삭제, 보관 기간이 지난 글의 PostPurger 영구 삭제)로 지워 FK 위반이 나지 않는지 확인한다. Docker가 없으면 건너뛴다.
 */
class PostDeleteWithRepliesMariaDbTest extends MariaDbIntegrationTest {

    @Autowired
    private PostService postService;

    @Autowired
    private PostPurger postPurger;

    @Autowired
    private PostModerationService postModerationService;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private PostLikeRepository postLikeRepository;

    @Autowired
    private UserRepository userRepository;

    private User author;
    private Authentication authorAuth;
    private Authentication adminAuth;
    private Long commenterId;

    @BeforeEach
    void setUp() {
        postLikeRepository.deleteAll();
        commentRepository.deleteAll();
        postRepository.deleteAll();
        userRepository.deleteAll();

        author = userRepository.save(User.builder()
                .name("reply-author").email("reply-author@example.com").password("encoded").role(Role.USER).build());
        User admin = userRepository.save(User.builder()
                .name("reply-admin").email("reply-admin@example.com").password("encoded").role(Role.ADMIN).build());
        User commenter = userRepository.save(User.builder()
                .name("reply-commenter").email("reply-commenter@example.com").password("encoded").role(Role.USER)
                .build());
        authorAuth = TestAuthentication.of(author);
        adminAuth = TestAuthentication.of(admin);
        this.commenterId = commenter.getId();
    }

    private Post seedPostWithParentsAndReplies() {
        Post post = postRepository.save(Post.builder().title("제목").content("내용").user(author).build());
        User commenter = userRepository.findById(commenterId).orElseThrow();
        for (int i = 0; i < 3; i++) {
            Comment parent = commentRepository.save(
                    Comment.builder().content("부모" + i).post(post).user(commenter).build());
            for (int j = 0; j < 2; j++) {
                commentRepository.save(
                        Comment.builder().content("답글" + i + "-" + j).post(post).user(commenter).parent(parent)
                                .build());
            }
        }
        return post;
    }

    /** 보관 기간이 이미 지난 것으로 보도록 미래 시각을 기준으로 영구 삭제한다. */
    private static LocalDateTime afterRetention() {
        return LocalDateTime.now().plusDays(1);
    }

    @Test
    @DisplayName("회귀: 작성자가 답글이 여러 개 달린 게시글을 삭제해도 소프트 삭제만 되고, 영구 삭제도 FK 위반이 나지 않는다")
    void authorDelete_withParentsAndReplies_succeedsWithoutForeignKeyViolation() {
        Post post = seedPostWithParentsAndReplies();
        Long postId = post.getId();

        assertThatCode(() -> postService.delete(postId, authorAuth)).doesNotThrowAnyException();

        // 소프트 삭제: 행과 댓글이 그대로 남는다.
        assertThat(postRepository.findById(postId).orElseThrow().isDeleted()).isTrue();
        assertThat(commentRepository.countByPostId(postId)).isEqualTo(9);

        // 영구 삭제: 자기참조 FK(FK_COMMENTS_PARENT) 순서와 FOR UPDATE 경로를 실제 InnoDB로 확인한다.
        assertThat(postService.purge(postId, afterRetention())).isTrue();
        assertThat(postRepository.findById(postId)).isEmpty();
        assertThat(commentRepository.countByPostId(postId)).isZero();
    }

    @Test
    @DisplayName("PostPurger: 보관 기간이 지난 삭제 글만 영구 삭제하고, 복구된 글과 보이는 글은 남긴다")
    void purger_removesOnlyExpiredDeletedPosts() {
        Post expired = seedPostWithParentsAndReplies();
        Post restored = seedPostWithParentsAndReplies();
        Post visible = seedPostWithParentsAndReplies();
        postService.delete(expired.getId(), authorAuth);
        postService.delete(restored.getId(), authorAuth);
        postModerationService.restore(restored.getId());

        int purged = postPurger.purgeDeletedBefore(afterRetention());

        assertThat(purged).isEqualTo(1);
        assertThat(postRepository.findById(expired.getId())).isEmpty();
        assertThat(postRepository.findById(restored.getId())).isPresent();
        assertThat(postRepository.findById(visible.getId())).isPresent();
        assertThat(commentRepository.countByPostId(restored.getId())).isEqualTo(9);
    }
}
