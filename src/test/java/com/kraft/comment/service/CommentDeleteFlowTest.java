package com.kraft.comment.service;

import com.kraft.comment.domain.Comment;
import com.kraft.comment.domain.CommentRepository;
import com.kraft.comment.dto.CommentDeleteResultDto;
import com.kraft.post.domain.Post;
import com.kraft.post.domain.PostRepository;
import com.kraft.support.TestAuthentication;
import com.kraft.user.domain.Role;
import com.kraft.user.domain.User;
import com.kraft.user.domain.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.Authentication;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 댓글 삭제를 실제 DB로 검증한다. 단위 테스트(CommentServiceTest)는
 * 답글 수 조회를 모킹하지만, 여기서는 실제 답글 행이 DB에 살아남는지·{@code deleted_at}이
 * 실제로 저장되는지를 본다.
 */
@SpringBootTest
class CommentDeleteFlowTest {

    @Autowired
    private CommentService commentService;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private UserRepository userRepository;

    private User author;
    private User replier;
    private Post post;

    @BeforeEach
    void setUp() {
        author = saveUser("author");
        replier = saveUser("replier");
        post = postRepository.save(Post.builder().title("글").content("내용").user(author).build());
    }

    private User saveUser(String prefix) {
        String unique = prefix + "-" + UUID.randomUUID();
        return userRepository.save(User.builder()
                .name(unique)
                .email(unique + "@example.com")
                .password("encoded")
                .role(Role.USER)
                .build());
    }

    private static Authentication authOf(User user) {
        return TestAuthentication.of(user);
    }

    @Test
    @DisplayName("답글이 있는 최상위 댓글을 지우면 행은 남고 내용만 비우며, 답글은 그대로 남는다")
    void deletingParentWithReplies_softDeletesAndKeepsReply() {
        Comment parent = commentRepository.save(
                Comment.builder().content("최상위 댓글").post(post).user(author).build());
        Comment reply = commentRepository.save(
                Comment.builder().content("남의 답글").post(post).user(replier).parent(parent).build());

        CommentDeleteResultDto result = commentService.delete(parent.getId(), authOf(author));

        assertThat(result.softDeleted()).isTrue();
        Comment reloadedParent = commentRepository.findById(parent.getId()).orElseThrow();
        assertThat(reloadedParent.isDeleted()).isTrue();
        assertThat(reloadedParent.getContent()).isEmpty();
        // 답글은 지워지지 않는다 — 이 개선 이전에는 부모와 함께 하드 삭제됐다.
        assertThat(commentRepository.findById(reply.getId())).isPresent();
    }

    @Test
    @DisplayName("답글이 없는 최상위 댓글을 지우면 지금처럼 행 자체가 사라진다")
    void deletingParentWithoutReplies_hardDeletes() {
        Comment parent = commentRepository.save(
                Comment.builder().content("답글 없는 댓글").post(post).user(author).build());

        CommentDeleteResultDto result = commentService.delete(parent.getId(), authOf(author));

        assertThat(result.softDeleted()).isFalse();
        assertThat(commentRepository.findById(parent.getId())).isEmpty();
    }

    @Test
    @DisplayName("답글 자신을 지우면(답글에는 답글이 없다) 지금처럼 행 자체가 사라진다")
    void deletingReplyItself_alwaysHardDeletes() {
        Comment parent = commentRepository.save(
                Comment.builder().content("최상위 댓글").post(post).user(author).build());
        Comment reply = commentRepository.save(
                Comment.builder().content("답글").post(post).user(replier).parent(parent).build());

        CommentDeleteResultDto result = commentService.delete(reply.getId(), authOf(replier));

        assertThat(result.softDeleted()).isFalse();
        assertThat(commentRepository.findById(reply.getId())).isEmpty();
        assertThat(commentRepository.findById(parent.getId())).isPresent();
    }
}
