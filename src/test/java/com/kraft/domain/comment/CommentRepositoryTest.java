package com.kraft.domain.comment;

import com.kraft.domain.post.Post;
import com.kraft.domain.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@DataJpaTest
class CommentRepositoryTest {

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User author;
    private Post post;
    private Comment parentComment;

    @BeforeEach
    void setUp() {
        // 사용자 생성
        author = User.of("testUser", "password", "test@example.com");
        entityManager.persist(author);

        // 게시글 생성
        post = Post.builder()
                .title("Test Post")
                .content("Test Content")
                .author(author)
                .build();
        entityManager.persist(post);

        // 부모 댓글 생성
        parentComment = Comment.builder()
                .content("Parent Comment")
                .post(post)
                .author(author)
                .build();
        entityManager.persist(parentComment);

        entityManager.flush();
    }

    @Test
    @DisplayName("게시글의 모든 댓글을 조회할 수 있다")
    void findByPostIdWithAuthor() {
        // given
        Comment comment2 = Comment.builder()
                .content("Comment 2")
                .post(post)
                .author(author)
                .build();
        entityManager.persist(comment2);
        entityManager.flush();

        // when
        List<Comment> comments = commentRepository.findByPostIdWithAuthor(post.getId());

        // then
        assertThat(comments).hasSize(2);
        assertThat(comments.get(0).getContent()).isEqualTo("Parent Comment");
    }

    @Test
    @DisplayName("게시글의 부모 댓글만 조회할 수 있다")
    void findParentCommentsByPostId() {
        // given
        Comment reply = Comment.builder()
                .content("Reply Comment")
                .post(post)
                .author(author)
                .parent(parentComment)
                .build();
        entityManager.persist(reply);
        entityManager.flush();

        // when
        List<Comment> parentComments = commentRepository.findParentCommentsByPostId(post.getId());

        // then
        assertThat(parentComments).hasSize(1);
        assertThat(parentComments.get(0).getContent()).isEqualTo("Parent Comment");
    }

    @Test
    @DisplayName("부모 댓글을 페이징 조회할 수 있다")
    void findParentCommentsByPostIdWithPaging() {
        // given
        for (int i = 0; i < 5; i++) {
            Comment comment = Comment.builder()
                    .content("Comment " + i)
                    .post(post)
                    .author(author)
                    .build();
            entityManager.persist(comment);
        }
        entityManager.flush();

        // when
        Page<Comment> page = commentRepository.findParentCommentsByPostId(
                post.getId(),
                PageRequest.of(0, 3)
        );

        // then
        assertThat(page.getContent()).hasSize(3);
        assertThat(page.getTotalElements()).isEqualTo(6); // 기존 1개 + 5개
        assertThat(page.getTotalPages()).isEqualTo(2);
    }

    @Test
    @DisplayName("특정 댓글의 답글을 조회할 수 있다")
    void findRepliesByParentId() {
        // given
        Comment reply1 = Comment.builder()
                .content("Reply 1")
                .post(post)
                .author(author)
                .parent(parentComment)
                .build();
        Comment reply2 = Comment.builder()
                .content("Reply 2")
                .post(post)
                .author(author)
                .parent(parentComment)
                .build();
        entityManager.persist(reply1);
        entityManager.persist(reply2);
        entityManager.flush();

        // when
        List<Comment> replies = commentRepository.findRepliesByParentId(parentComment.getId());

        // then
        assertThat(replies).hasSize(2);
    }

    @Test
    @DisplayName("특정 사용자의 댓글을 조회할 수 있다")
    void findByAuthorIdWithPost() {
        // given
        User anotherUser = User.of("another", "password", "another@example.com");
        entityManager.persist(anotherUser);

        Comment comment = Comment.builder()
                .content("Another Comment")
                .post(post)
                .author(anotherUser)
                .build();
        entityManager.persist(comment);
        entityManager.flush();

        // when
        List<Comment> comments = commentRepository.findByAuthorIdWithPost(anotherUser.getId());

        // then
        assertThat(comments).hasSize(1);
        assertThat(comments.get(0).getContent()).isEqualTo("Another Comment");
    }

    @Test
    @DisplayName("게시글의 댓글 수를 조회할 수 있다")
    void countByPostId() {
        // given
        Comment comment2 = Comment.builder()
                .content("Comment 2")
                .post(post)
                .author(author)
                .build();
        Comment reply = Comment.builder()
                .content("Reply")
                .post(post)
                .author(author)
                .parent(parentComment)
                .build();
        entityManager.persist(comment2);
        entityManager.persist(reply);
        entityManager.flush();

        // when
        long count = commentRepository.countByPostId(post.getId());

        // then
        assertThat(count).isEqualTo(3); // 부모 1개, 일반 1개, 답글 1개
    }
}

