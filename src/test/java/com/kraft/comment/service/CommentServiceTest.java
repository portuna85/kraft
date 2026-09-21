package com.kraft.comment.service;

import com.kraft.comment.domain.Comment;
import com.kraft.comment.domain.CommentRepository;
import com.kraft.comment.dto.CommentPageDto;
import com.kraft.comment.dto.CommentSaveRequestDto;
import com.kraft.comment.dto.CommentUpdateRequestDto;
import com.kraft.post.domain.Post;
import com.kraft.post.domain.PostRepository;
import com.kraft.user.domain.EmailHasher;
import com.kraft.user.domain.Role;
import com.kraft.user.domain.User;
import com.kraft.user.domain.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * {@link CommentService} 단위 테스트. {@link PostService}와 동일한 방식으로 Mockito만
 * 사용해 {@link PostRepository}, {@link UserRepository}, {@link CommentRepository}를 모킹한다.
 */
@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private PostRepository postRepository;

    @Mock
    private UserRepository userRepository;

    private CommentService commentService;

    @BeforeEach
    void setUp() {
        commentService = new CommentService(commentRepository, postRepository, userRepository);
    }

    private static User userWithEmail(String email, Long id) {
        User user = User.builder().name("tester").email(email).password("encoded").role(Role.USER).build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private static Post postOf(Long id) {
        Post post = Post.builder().title("게시글").content("내용").user(null).build();
        ReflectionTestUtils.setField(post, "id", id);
        return post;
    }

    private static Comment commentOf(User owner, Long id) {
        Comment comment = Comment.builder().content("원래 댓글").post(postOf(1L)).user(owner).build();
        ReflectionTestUtils.setField(comment, "id", id);
        return comment;
    }

    private static Comment replyOf(User owner, Long id, Comment parent) {
        Comment reply = Comment.builder().content("답글").post(parent.getPost()).user(owner).parent(parent).build();
        ReflectionTestUtils.setField(reply, "id", id);
        return reply;
    }

    private static Authentication authOf(String email, Role role) {
        return new UsernamePasswordAuthenticationToken(
                email, null, List.of(new SimpleGrantedAuthority(role.getKey())));
    }

    @Test
    @DisplayName("save: 게시글과 회원이 모두 존재하면 댓글을 저장하고 ID를 반환한다")
    void save_whenPostAndUserExist_savesCommentAndReturnsId() {
        Post post = postOf(1L);
        User user = userWithEmail("tester@example.com", 1L);
        given(postRepository.findById(1L)).willReturn(Optional.of(post));
        given(userRepository.findByEmailHash(EmailHasher.sha512Hex("tester@example.com"))).willReturn(Optional.of(user));
        Comment saved = commentOf(user, 100L);
        given(commentRepository.save(any(Comment.class))).willReturn(saved);

        Long id = commentService.save(1L, "tester@example.com", new CommentSaveRequestDto("댓글 내용", null));

        assertThat(id).isEqualTo(100L);
    }

    @Test
    @DisplayName("save: 게시글이 없으면 IllegalArgumentException")
    void save_whenPostNotFound_throwsIllegalArgumentException() {
        given(postRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.save(999L, "tester@example.com", new CommentSaveRequestDto("내용", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("해당 게시글이 없습니다");

        verify(commentRepository, never()).save(any());
    }

    @Test
    @DisplayName("save: 이메일 인증 전(GUEST) 회원이면 AccessDeniedException이고 저장되지 않는다")
    void save_whenUserIsGuest_throwsAccessDeniedExceptionAndDoesNotSave() {
        User guest = User.builder().name("tester").email("guest@example.com").password("encoded").role(Role.GUEST).build();
        ReflectionTestUtils.setField(guest, "id", 1L);
        given(postRepository.findById(1L)).willReturn(Optional.of(postOf(1L)));
        given(userRepository.findByEmailHash(EmailHasher.sha512Hex("guest@example.com"))).willReturn(Optional.of(guest));

        assertThatThrownBy(() -> commentService.save(1L, "guest@example.com", new CommentSaveRequestDto("내용", null)))
                .isInstanceOf(AccessDeniedException.class);

        verify(commentRepository, never()).save(any());
    }

    @Test
    @DisplayName("save: 회원이 없으면 IllegalArgumentException")
    void save_whenUserNotFound_throwsIllegalArgumentException() {
        given(postRepository.findById(1L)).willReturn(Optional.of(postOf(1L)));
        given(userRepository.findByEmailHash(EmailHasher.sha512Hex("nobody@example.com"))).willReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.save(1L, "nobody@example.com", new CommentSaveRequestDto("내용", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("존재하지 않는 회원");

        verify(commentRepository, never()).save(any());
    }

    /**
     * 2단계 댓글: parentId가 있으면 그 댓글을 부모로 저장한다. 실제로 저장을 호출한 인자의
     * parent가 정확히 그 댓글인지까지 확인한다 — id만 맞고 엉뚱한 엔티티가 실려 가면 안 된다.
     */
    @Test
    @DisplayName("save: parentId가 있으면 그 댓글을 부모로 하는 답글로 저장한다")
    void save_withParentId_savesAsReplyToThatComment() {
        Post post = postOf(1L);
        User author = userWithEmail("tester@example.com", 1L);
        Comment parent = commentOf(author, 100L);
        given(postRepository.findById(1L)).willReturn(Optional.of(post));
        given(userRepository.findByEmailHash(EmailHasher.sha512Hex("tester@example.com"))).willReturn(Optional.of(author));
        given(commentRepository.findById(100L)).willReturn(Optional.of(parent));
        Comment savedReply = replyOf(author, 200L, parent);
        given(commentRepository.save(any(Comment.class))).willReturn(savedReply);

        Long id = commentService.save(1L, "tester@example.com", new CommentSaveRequestDto("답글 내용", 100L));

        assertThat(id).isEqualTo(200L);
        var captor = org.mockito.ArgumentCaptor.forClass(Comment.class);
        verify(commentRepository).save(captor.capture());
        assertThat(captor.getValue().getParent()).isSameAs(parent);
    }

    @Test
    @DisplayName("save: 답글에 다시 답글을 달려고 하면 거절한다(3단계 금지)")
    void save_replyToAReply_isRejected() {
        Post post = postOf(1L);
        User author = userWithEmail("tester@example.com", 1L);
        Comment topLevel = commentOf(author, 100L);
        Comment existingReply = replyOf(author, 200L, topLevel);
        given(postRepository.findById(1L)).willReturn(Optional.of(post));
        given(userRepository.findByEmailHash(EmailHasher.sha512Hex("tester@example.com"))).willReturn(Optional.of(author));
        given(commentRepository.findById(200L)).willReturn(Optional.of(existingReply));

        assertThatThrownBy(() -> commentService.save(1L, "tester@example.com", new CommentSaveRequestDto("답글의 답글", 200L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("답글에는 답글을 달 수 없습니다");

        verify(commentRepository, never()).save(any());
    }

    @Test
    @DisplayName("save: 다른 게시글의 댓글을 parentId로 지정하면 거절한다")
    void save_parentFromAnotherPost_isRejected() {
        User author = userWithEmail("tester@example.com", 1L);
        Comment parentOnAnotherPost = Comment.builder().content("다른 글의 댓글").post(postOf(2L)).user(author).build();
        ReflectionTestUtils.setField(parentOnAnotherPost, "id", 300L);
        given(postRepository.findById(1L)).willReturn(Optional.of(postOf(1L)));
        given(userRepository.findByEmailHash(EmailHasher.sha512Hex("tester@example.com"))).willReturn(Optional.of(author));
        given(commentRepository.findById(300L)).willReturn(Optional.of(parentOnAnotherPost));

        assertThatThrownBy(() -> commentService.save(1L, "tester@example.com", new CommentSaveRequestDto("답글", 300L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("다른 게시글의 댓글에는 답글을 달 수 없습니다");

        verify(commentRepository, never()).save(any());
    }

    /** 2단계 댓글: 페이지에 실린 최상위 댓글의 답글이 배치로 함께 채워지는지 본다. */
    @Test
    @DisplayName("F13/2단계: findInitialPageForView는 각 최상위 댓글에 그 답글을 채워 돌려준다")
    void findInitialPageForView_attachesRepliesToEachTopLevelComment() {
        User owner = userWithEmail("owner@example.com", 1L);
        Comment topLevel = commentOf(owner, 100L);
        Comment reply = replyOf(owner, 200L, topLevel);
        given(commentRepository.findPageByPostIdAsc(1L, null, PageRequest.of(0, 21))).willReturn(List.of(topLevel));
        given(commentRepository.findRepliesByParentIdIn(eq(List.of(100L)), any(PageRequest.class))).willReturn(List.of(reply));
        given(commentRepository.countByPostId(1L)).willReturn(2L);

        CommentPageDto result = commentService.findInitialPageForView(1L, authOf("owner@example.com", Role.USER));

        assertThat(result.comments()).hasSize(1);
        assertThat(result.comments().get(0).replies()).hasSize(1);
        assertThat(result.comments().get(0).replies().get(0).id()).isEqualTo(200L);
        assertThat(result.comments().get(0).replies().get(0).parentId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("update: 작성자 본인이면 내용이 변경된다")
    void update_whenAuthor_updatesContent() {
        User owner = userWithEmail("owner@example.com", 1L);
        Comment comment = commentOf(owner, 100L);
        given(commentRepository.findById(100L)).willReturn(Optional.of(comment));
        given(userRepository.findByEmailHash(EmailHasher.sha512Hex("owner@example.com"))).willReturn(Optional.of(owner));

        Long id = commentService.update(100L, new CommentUpdateRequestDto("수정된 댓글", null),
                authOf("owner@example.com", Role.USER));

        assertThat(id).isEqualTo(100L);
        assertThat(comment.getContent()).isEqualTo("수정된 댓글");
    }

    /**
     * B12: 화면이 받아간 버전과 지금 버전이 같으면 저장을 허용한다 — PostService.validateVersion과
     * 같은 계약.
     */
    @Test
    @DisplayName("B12: 받아간 버전과 현재 버전이 같으면 저장을 허용한다")
    void update_whenVersionMatches_updatesContent() {
        User owner = userWithEmail("owner@example.com", 1L);
        Comment comment = commentOf(owner, 100L);
        ReflectionTestUtils.setField(comment, "version", 5L);
        given(commentRepository.findById(100L)).willReturn(Optional.of(comment));
        given(userRepository.findByEmailHash(EmailHasher.sha512Hex("owner@example.com"))).willReturn(Optional.of(owner));

        commentService.update(100L, new CommentUpdateRequestDto("수정된 댓글", 5L),
                authOf("owner@example.com", Role.USER));

        assertThat(comment.getContent()).isEqualTo("수정된 댓글");
    }

    /**
     * B12: 화면이 받아간 버전이 지금 버전과 다르면(그 사이 다른 곳에서 먼저 저장됨)
     * ObjectOptimisticLockingFailureException을 던지고 내용은 바뀌지 않는다 —
     * ApiExceptionHandler가 이를 409로 변환한다.
     */
    @Test
    @DisplayName("B12: 받아간 버전이 현재 버전과 다르면 충돌로 거절하고 내용은 바뀌지 않는다")
    void update_whenVersionMismatches_throwsOptimisticLockingFailureAndDoesNotModify() {
        User owner = userWithEmail("owner@example.com", 1L);
        Comment comment = commentOf(owner, 100L);
        ReflectionTestUtils.setField(comment, "version", 5L);
        given(commentRepository.findById(100L)).willReturn(Optional.of(comment));
        given(userRepository.findByEmailHash(EmailHasher.sha512Hex("owner@example.com"))).willReturn(Optional.of(owner));

        assertThatThrownBy(() -> commentService.update(100L, new CommentUpdateRequestDto("수정된 댓글", 4L),
                authOf("owner@example.com", Role.USER)))
                .isInstanceOf(org.springframework.orm.ObjectOptimisticLockingFailureException.class);

        assertThat(comment.getContent()).isEqualTo("원래 댓글");
    }

    @Test
    @DisplayName("update: 작성자가 아니면 AccessDeniedException이고 내용은 변경되지 않는다")
    void update_whenNotAuthor_throwsAccessDeniedExceptionAndDoesNotModify() {
        User owner = userWithEmail("owner@example.com", 1L);
        Comment comment = commentOf(owner, 100L);
        User intruder = userWithEmail("intruder@example.com", 2L);
        given(commentRepository.findById(100L)).willReturn(Optional.of(comment));
        given(userRepository.findByEmailHash(EmailHasher.sha512Hex("intruder@example.com"))).willReturn(Optional.of(intruder));

        assertThatThrownBy(() -> commentService.update(100L, new CommentUpdateRequestDto("해킹", null),
                authOf("intruder@example.com", Role.USER)))
                .isInstanceOf(AccessDeniedException.class);

        assertThat(comment.getContent()).isEqualTo("원래 댓글");
    }

    @Test
    @DisplayName("update: ROLE_ADMIN이면 작성자가 아니어도 수정할 수 있다")
    void update_whenAdmin_updatesContentEvenIfNotAuthor() {
        User owner = userWithEmail("owner@example.com", 1L);
        Comment comment = commentOf(owner, 100L);
        User admin = userWithEmail("admin@example.com", 2L);
        given(commentRepository.findById(100L)).willReturn(Optional.of(comment));
        given(userRepository.findByEmailHash(EmailHasher.sha512Hex("admin@example.com"))).willReturn(Optional.of(admin));

        commentService.update(100L, new CommentUpdateRequestDto("관리자 수정", null),
                authOf("admin@example.com", Role.ADMIN));

        assertThat(comment.getContent()).isEqualTo("관리자 수정");
    }

    @Test
    @DisplayName("update: 정지된 작성자는 자신의 댓글도 수정할 수 없다")
    void update_whenAuthorIsSuspended_throwsAccessDeniedExceptionAndDoesNotModify() {
        User owner = userWithEmail("owner@example.com", 1L);
        owner.suspendUntil(java.time.LocalDateTime.now().plusDays(1), "규정 위반");
        Comment comment = commentOf(owner, 100L);
        given(commentRepository.findById(100L)).willReturn(Optional.of(comment));
        given(userRepository.findByEmailHash(EmailHasher.sha512Hex("owner@example.com"))).willReturn(Optional.of(owner));

        assertThatThrownBy(() -> commentService.update(100L, new CommentUpdateRequestDto("수정 시도", null),
                authOf("owner@example.com", Role.USER)))
                .isInstanceOf(AccessDeniedException.class);

        assertThat(comment.getContent()).isEqualTo("원래 댓글");
    }

    @Test
    @DisplayName("delete: 작성자가 아니면 AccessDeniedException이고 delete가 호출되지 않는다")
    void delete_whenNotAuthor_throwsAccessDeniedExceptionAndDoesNotDelete() {
        User owner = userWithEmail("owner@example.com", 1L);
        Comment comment = commentOf(owner, 100L);
        given(commentRepository.findById(100L)).willReturn(Optional.of(comment));

        assertThatThrownBy(() -> commentService.delete(100L, authOf("intruder@example.com", Role.USER)))
                .isInstanceOf(AccessDeniedException.class);

        verify(commentRepository, never()).delete(any());
        verify(commentRepository, never()).deleteAllByParentId(any());
    }

    /**
     * 2단계 댓글: DB에 cascade를 걸지 않았으므로(Comment.parent 주석 참고) 최상위 댓글을
     * 지우기 전에 그 답글을 먼저 명시적으로 지워야 FK 위반이 나지 않는다.
     */
    @Test
    @DisplayName("delete: 작성자 본인이면 답글을 먼저 지우고 나서 자신을 삭제한다")
    void delete_whenAuthor_deletesRepliesFirstThenSelf() {
        User owner = userWithEmail("owner@example.com", 1L);
        Comment comment = commentOf(owner, 100L);
        given(commentRepository.findById(100L)).willReturn(Optional.of(comment));

        commentService.delete(100L, authOf("owner@example.com", Role.USER));

        var inOrder = org.mockito.Mockito.inOrder(commentRepository);
        inOrder.verify(commentRepository).deleteAllByParentId(100L);
        inOrder.verify(commentRepository).delete(comment);
    }

    @Test
    @DisplayName("F13: findInitialPageForView는 21개 중 20개만 반환하고 hasMore=true, totalCount는 별도로 담는다")
    void findInitialPageForView_capsAtPageSizeAndReportsHasMore() {
        User owner = userWithEmail("owner@example.com", 1L);
        List<Comment> twentyOne = IntStream.rangeClosed(1, 21)
                .mapToObj(i -> commentOf(owner, (long) i))
                .toList();
        given(commentRepository.findPageByPostIdAsc(1L, null, PageRequest.of(0, 21))).willReturn(twentyOne);
        given(commentRepository.countByPostId(1L)).willReturn(30L);

        CommentPageDto result = commentService.findInitialPageForView(1L, authOf("owner@example.com", Role.USER));

        assertThat(result.comments()).hasSize(20);
        assertThat(result.hasMore()).isTrue();
        assertThat(result.totalCount()).isEqualTo(30L);
    }

    @Test
    @DisplayName("F13: findNextPageForView는 afterId 커서를 그대로 리포지토리에 전달한다")
    void findNextPageForView_passesAfterIdCursorToRepository() {
        given(commentRepository.findPageByPostIdAsc(1L, 20L, PageRequest.of(0, 21))).willReturn(List.of());
        given(commentRepository.countByPostId(1L)).willReturn(20L);

        CommentPageDto result = commentService.findNextPageForView(1L, 20L, authOf("owner@example.com", Role.USER));

        assertThat(result.comments()).isEmpty();
        assertThat(result.hasMore()).isFalse();
        verify(commentRepository).findPageByPostIdAsc(1L, 20L, PageRequest.of(0, 21));
    }
}
