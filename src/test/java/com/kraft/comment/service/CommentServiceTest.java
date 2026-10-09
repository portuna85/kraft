package com.kraft.comment.service;

import com.kraft.comment.domain.Comment;
import com.kraft.shared.exception.NotFoundException;
import com.kraft.comment.domain.CommentRepository;
import com.kraft.comment.dto.CommentPageDto;
import com.kraft.comment.dto.CommentSaveRequestDto;
import com.kraft.comment.dto.CommentUpdateRequestDto;
import com.kraft.comment.dto.CommentViewDto;
import com.kraft.post.domain.Post;
import com.kraft.post.domain.PostNotFoundException;
import com.kraft.post.domain.PostRepository;
import com.kraft.support.TestAuthentication;
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
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
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

    private static Authentication authOf(User user) {
        return TestAuthentication.of(user);
    }

    private static Authentication authOf(Long id, String email, Role role) {
        return TestAuthentication.of(id, email, role);
    }

    @Test
    @DisplayName("save: 게시글과 회원이 모두 존재하면 댓글을 저장하고 ID를 반환한다")
    void save_whenPostAndUserExist_savesCommentAndReturnsId() {
        Post post = postOf(1L);
        User user = userWithEmail("tester@example.com", 1L);
        given(postRepository.existsVisibleById(1L)).willReturn(true);
        given(postRepository.getReferenceById(1L)).willReturn(post);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        Comment saved = commentOf(user, 100L);
        given(commentRepository.saveAndFlush(any(Comment.class))).willReturn(saved);

        CommentViewDto result = commentService.save(1L, authOf(user),
                new CommentSaveRequestDto("댓글 내용", null));

        assertThat(result.id()).isEqualTo(100L);
    }

    @Test
    @DisplayName("save: 게시글이 없으면 IllegalArgumentException")
    void save_whenPostNotFound_throwsIllegalArgumentException() {
        given(postRepository.existsVisibleById(999L)).willReturn(false);

        assertThatThrownBy(() -> commentService.save(999L, authOf(1L, "tester@example.com", Role.USER), new CommentSaveRequestDto("내용", null)))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("해당 게시글이 없습니다");

        verify(commentRepository, never()).save(any());
    }

    @Test
    @DisplayName("save: 이메일 인증 전(GUEST) 회원이면 AccessDeniedException이고 저장되지 않는다")
    void save_whenUserIsGuest_throwsAccessDeniedExceptionAndDoesNotSave() {
        User guest = User.builder().name("tester").email("guest@example.com").password("encoded").role(Role.GUEST).build();
        ReflectionTestUtils.setField(guest, "id", 1L);
        given(postRepository.existsVisibleById(1L)).willReturn(true);
        given(postRepository.getReferenceById(1L)).willReturn(postOf(1L));
        given(userRepository.findById(1L)).willReturn(Optional.of(guest));

        assertThatThrownBy(() -> commentService.save(1L, authOf(guest), new CommentSaveRequestDto("내용", null)))
                .isInstanceOf(AccessDeniedException.class);

        verify(commentRepository, never()).save(any());
    }

    @Test
    @DisplayName("save: 회원이 없으면 NotFoundException")
    void save_whenUserNotFound_throwsIllegalArgumentException() {
        given(postRepository.existsVisibleById(1L)).willReturn(true);
        given(postRepository.getReferenceById(1L)).willReturn(postOf(1L));
        given(userRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.save(1L, authOf(999L, "nobody@example.com", Role.USER), new CommentSaveRequestDto("내용", null)))
                .isInstanceOf(NotFoundException.class)
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
        given(postRepository.existsVisibleById(1L)).willReturn(true);
        given(postRepository.getReferenceById(1L)).willReturn(post);
        given(userRepository.findById(1L)).willReturn(Optional.of(author));
        given(commentRepository.findById(100L)).willReturn(Optional.of(parent));
        Comment savedReply = replyOf(author, 200L, parent);
        given(commentRepository.saveAndFlush(any(Comment.class))).willReturn(savedReply);

        CommentViewDto result = commentService.save(1L, authOf(author),
                new CommentSaveRequestDto("답글 내용", 100L));

        assertThat(result.id()).isEqualTo(200L);
        var captor = org.mockito.ArgumentCaptor.forClass(Comment.class);
        verify(commentRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getParent()).isSameAs(parent);
    }

    @Test
    @DisplayName("save: 답글에 다시 답글을 달려고 하면 거절한다(3단계 금지)")
    void save_replyToAReply_isRejected() {
        Post post = postOf(1L);
        User author = userWithEmail("tester@example.com", 1L);
        Comment topLevel = commentOf(author, 100L);
        Comment existingReply = replyOf(author, 200L, topLevel);
        given(postRepository.existsVisibleById(1L)).willReturn(true);
        given(postRepository.getReferenceById(1L)).willReturn(post);
        given(userRepository.findById(1L)).willReturn(Optional.of(author));
        given(commentRepository.findById(200L)).willReturn(Optional.of(existingReply));

        assertThatThrownBy(() -> commentService.save(1L, authOf(author), new CommentSaveRequestDto("답글의 답글", 200L)))
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
        given(postRepository.existsVisibleById(1L)).willReturn(true);
        given(postRepository.getReferenceById(1L)).willReturn(postOf(1L));
        given(userRepository.findById(1L)).willReturn(Optional.of(author));
        given(commentRepository.findById(300L)).willReturn(Optional.of(parentOnAnotherPost));

        assertThatThrownBy(() -> commentService.save(1L, authOf(author), new CommentSaveRequestDto("답글", 300L)))
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
        given(commentRepository.countRepliesByParentIdIn(List.of(100L))).willReturn(Map.of(100L, 1L));
        given(commentRepository.findInitialRepliesGroupedByParentIdIn(List.of(100L), 20))
                .willReturn(Map.of(100L, List.of(reply)));
        given(commentRepository.countByPostId(1L)).willReturn(2L);

        CommentPageDto result = commentService.findInitialPageForView(1L, authOf(owner));

        assertThat(result.comments()).hasSize(1);
        assertThat(result.comments().get(0).replies()).hasSize(1);
        assertThat(result.comments().get(0).replies().get(0).id()).isEqualTo(200L);
        assertThat(result.comments().get(0).replies().get(0).parentId()).isEqualTo(100L);
    }

    /**
     * COR-05 회귀: 예전에는 한 페이지 전체(여러 부모 합산)에서 가져오는 답글 총량에 500이라는
     * 상한 하나를 뒀다 — 한 부모가 답글을 아주 많이 갖고 있으면 그 부모가 상한을 혼자 다 써서,
     * 같은 페이지의 다른 부모는 새로고침을 해도 자신의 답글에 영영 도달하지 못했다. 지금은
     * 부모마다 따로 조회하므로(부모별 최대 20개 + hasMoreReplies) 한 부모의 답글 수가 다른
     * 부모의 조회에 영향을 주지 않는다.
     */
    @Test
    @DisplayName("COR-05: 한 부모의 답글이 아주 많아도(옛 전역 상한을 혼자 넘는 규모) 다른 부모는 자신의 답글을 그대로 받는다")
    void findInitialPageForView_fetchesRepliesPerParentIndependently() {
        User owner = userWithEmail("owner@example.com", 1L);
        Comment parentA = commentOf(owner, 100L);
        Comment parentB = commentOf(owner, 101L);
        Comment replyB = replyOf(owner, 201L, parentB);
        given(commentRepository.findPageByPostIdAsc(1L, null, PageRequest.of(0, 21)))
                .willReturn(List.of(parentA, parentB));
        // A는 답글이 600개다 — 예전 전역 상한(500)을 혼자 넘는 규모.
        given(commentRepository.countRepliesByParentIdIn(List.of(100L, 101L)))
                .willReturn(Map.of(100L, 600L, 101L, 1L));
        List<Comment> firstTwentyOfA = IntStream.range(0, 20)
                .mapToObj(i -> replyOf(owner, 300L + i, parentA))
                .toList();
        given(commentRepository.findInitialRepliesGroupedByParentIdIn(List.of(100L, 101L), 20))
                .willReturn(Map.of(100L, firstTwentyOfA, 101L, List.of(replyB)));
        given(commentRepository.countByPostId(1L)).willReturn(622L);

        CommentPageDto result = commentService.findInitialPageForView(1L, authOf(owner));

        CommentViewDto viewA = result.comments().get(0);
        CommentViewDto viewB = result.comments().get(1);
        assertThat(viewA.replyCount()).isEqualTo(600L);
        assertThat(viewA.hasMoreReplies()).isTrue();
        assertThat(viewA.replies()).hasSize(20);
        // 핵심 주장: A가 답글을 아무리 많이 갖고 있어도 B는 자신의 답글(1개)을 그대로 받는다.
        assertThat(viewB.replyCount()).isEqualTo(1L);
        assertThat(viewB.hasMoreReplies()).isFalse();
        assertThat(viewB.replies()).hasSize(1);
    }

    @Test
    @DisplayName("COR-05: findRepliesPage는 afterId 이후의 답글을 페이지로 반환한다(답글 더 보기)")
    void findRepliesPage_returnsNextPageOfReplies() {
        User owner = userWithEmail("owner@example.com", 1L);
        Comment parent = commentOf(owner, 100L);
        Comment reply21 = replyOf(owner, 321L, parent);
        given(commentRepository.findRepliesByParentIdAsc(eq(100L), eq(320L), any(PageRequest.class)))
                .willReturn(List.of(reply21));

        CommentPageDto result = commentService.findRepliesPage(100L, 320L, authOf(owner));

        assertThat(result.comments()).extracting(CommentViewDto::id).containsExactly(321L);
        // A-BE-13: 답글 더 보기는 항상 후속 페이지라 전체 개수를 다시 세지 않는다.
        assertThat(result.totalCount()).isNull();
        assertThat(result.hasMore()).isFalse();
    }

    @Test
    @DisplayName("update: 작성자 본인이면 내용이 변경된다")
    void update_whenAuthor_updatesContent() {
        User owner = userWithEmail("owner@example.com", 1L);
        Comment comment = commentOf(owner, 100L);
        given(commentRepository.findById(100L)).willReturn(Optional.of(comment));
        given(userRepository.findById(1L)).willReturn(Optional.of(owner));

        CommentViewDto result = commentService.update(100L, new CommentUpdateRequestDto("수정된 댓글"), null,
                authOf(owner));

        assertThat(result.id()).isEqualTo(100L);
        assertThat(comment.getContent()).isEqualTo("수정된 댓글");
    }

    /**
     * B12: 화면이 받아간 버전과 지금 버전이 같으면 저장을 허용한다 — VersionCheck과
     * 같은 계약.
     */
    @Test
    @DisplayName("B12: 받아간 버전과 현재 버전이 같으면 저장을 허용한다")
    void update_whenVersionMatches_updatesContent() {
        User owner = userWithEmail("owner@example.com", 1L);
        Comment comment = commentOf(owner, 100L);
        ReflectionTestUtils.setField(comment, "version", 5L);
        given(commentRepository.findById(100L)).willReturn(Optional.of(comment));
        given(userRepository.findById(1L)).willReturn(Optional.of(owner));

        commentService.update(100L, new CommentUpdateRequestDto("수정된 댓글"), 5L,
                authOf(owner));

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
        given(userRepository.findById(1L)).willReturn(Optional.of(owner));

        assertThatThrownBy(() -> commentService.update(100L, new CommentUpdateRequestDto("수정된 댓글"), 4L,
                authOf(owner)))
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
        given(userRepository.findById(2L)).willReturn(Optional.of(intruder));

        assertThatThrownBy(() -> commentService.update(100L, new CommentUpdateRequestDto("해킹"), null,
                authOf(intruder)))
                .isInstanceOf(AccessDeniedException.class);

        assertThat(comment.getContent()).isEqualTo("원래 댓글");
    }

    @Test
    @DisplayName("update: ROLE_ADMIN이면 작성자가 아니어도 수정할 수 있다")
    void update_whenAdmin_updatesContentEvenIfNotAuthor() {
        User owner = userWithEmail("owner@example.com", 1L);
        Comment comment = commentOf(owner, 100L);
        User admin = User.builder().name("admin").email("admin@example.com").password("encoded").role(Role.ADMIN).build();
        ReflectionTestUtils.setField(admin, "id", 2L);
        given(commentRepository.findById(100L)).willReturn(Optional.of(comment));
        given(userRepository.findById(2L)).willReturn(Optional.of(admin));

        commentService.update(100L, new CommentUpdateRequestDto("관리자 수정"), null,
                authOf(admin));

        assertThat(comment.getContent()).isEqualTo("관리자 수정");
    }

    @Test
    @DisplayName("delete: 작성자가 아니면 AccessDeniedException이고 delete가 호출되지 않는다")
    void delete_whenNotAuthor_throwsAccessDeniedExceptionAndDoesNotDelete() {
        User owner = userWithEmail("owner@example.com", 1L);
        Comment comment = commentOf(owner, 100L);
        given(commentRepository.findById(100L)).willReturn(Optional.of(comment));

        assertThatThrownBy(() -> commentService.delete(100L, authOf(2L, "intruder@example.com", Role.USER)))
                .isInstanceOf(AccessDeniedException.class);

        verify(commentRepository, never()).delete(any());
        verify(commentRepository, never()).deleteAllByParentId(any());
    }

    /**
     * 답글이 없으면(또는 답글 자신이면) 지금까지처럼 행 자체를 지운다. 2단계 댓글: DB에
     * cascade를 걸지 않았으므로(Comment.parent 주석 참고) 최상위 댓글을 지우기 전에 그 답글을
     * 먼저 명시적으로 지워야 FK 위반이 나지 않는다.
     */
    @Test
    @DisplayName("delete: 답글이 없으면 행 자체를 지우고 A-BE-01 이벤트를 발행한다")
    void delete_whenNoReplies_hardDeletesAndPublishesEvent() {
        User owner = userWithEmail("owner@example.com", 1L);
        Comment comment = commentOf(owner, 100L);
        given(commentRepository.findById(100L)).willReturn(Optional.of(comment));
        given(commentRepository.countRepliesByParentIdIn(List.of(100L))).willReturn(Map.of());

        var result = commentService.delete(100L, authOf(owner));

        var inOrder = org.mockito.Mockito.inOrder(commentRepository);
        inOrder.verify(commentRepository).deleteAllByParentId(100L);
        inOrder.verify(commentRepository).delete(comment);
        assertThat(result.softDeleted()).isFalse();
        assertThat(result.id()).isEqualTo(100L);
    }

    /**
     * 답글이 있는 최상위 댓글을 지우면 남의 답글까지 함께 사라졌다. 행을
     * 지우지 않고 내용만 비운다.
     */
    @Test
    @DisplayName("delete: 답글이 있으면 행을 지우지 않고 소프트 삭제한다")
    void delete_whenHasReplies_softDeletesAndKeepsRow() {
        User owner = userWithEmail("owner@example.com", 1L);
        Comment comment = commentOf(owner, 100L);
        given(commentRepository.findById(100L)).willReturn(Optional.of(comment));
        given(commentRepository.countRepliesByParentIdIn(List.of(100L))).willReturn(Map.of(100L, 2L));

        var result = commentService.delete(100L, authOf(owner));

        assertThat(result.softDeleted()).isTrue();
        assertThat(comment.isDeleted()).isTrue();
        assertThat(comment.getContent()).isEmpty();
        verify(commentRepository, never()).delete(any());
    }

    @Test
    @DisplayName("delete: 이미 소프트 삭제된 댓글에 대한 재요청은 조용히 넘어간다")
    void delete_whenAlreadyDeleted_isNoOp() {
        User owner = userWithEmail("owner@example.com", 1L);
        Comment comment = commentOf(owner, 100L);
        comment.softDelete();
        given(commentRepository.findById(100L)).willReturn(Optional.of(comment));

        var result = commentService.delete(100L, authOf(owner));

        assertThat(result.softDeleted()).isTrue();
        verify(commentRepository, never()).delete(any());
        verify(commentRepository, never()).countRepliesByParentIdIn(any());
    }

    @Test
    @DisplayName("update: 삭제된 댓글은 수정할 수 없다")
    void update_whenDeleted_isRejected() {
        User owner = userWithEmail("owner@example.com", 1L);
        Comment comment = commentOf(owner, 100L);
        comment.softDelete();
        given(commentRepository.findById(100L)).willReturn(Optional.of(comment));
        given(userRepository.findById(any())).willReturn(Optional.of(owner));

        assertThatThrownBy(() -> commentService.update(100L, new CommentUpdateRequestDto("수정 시도"), null,
                authOf(owner)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("삭제된 댓글");
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

        CommentPageDto result = commentService.findInitialPageForView(1L, authOf(owner));

        assertThat(result.comments()).hasSize(20);
        assertThat(result.hasMore()).isTrue();
        assertThat(result.totalCount()).isEqualTo(30L);
    }

    @Test
    @DisplayName("F13: findNextPageForView는 afterId 커서를 그대로 리포지토리에 전달한다")
    void findNextPageForView_passesAfterIdCursorToRepository() {
        given(postRepository.existsVisibleById(1L)).willReturn(true);
        given(commentRepository.findPageByPostIdAsc(1L, 20L, PageRequest.of(0, 21))).willReturn(List.of());

        CommentPageDto result = commentService.findNextPageForView(1L, 20L, authOf(1L, "owner@example.com", Role.USER));

        assertThat(result.comments()).isEmpty();
        assertThat(result.hasMore()).isFalse();
        // A-BE-13: 후속 페이지는 전체 개수를 다시 세지 않는다(countByPostId를 부르지 않는다).
        assertThat(result.totalCount()).isNull();
        verify(commentRepository).findPageByPostIdAsc(1L, 20L, PageRequest.of(0, 21));
        verify(commentRepository, never()).countByPostId(any());
    }

    @Test
    @DisplayName("update: 소프트 삭제된 글 아래의 댓글은 작성자도 수정할 수 없다(글이 없는 것으로 답한다)")
    void update_whenPostDeleted_throwsPostNotFound() {
        User owner = userWithEmail("owner@example.com", 1L);
        Comment comment = commentOf(owner, 5L);
        ReflectionTestUtils.setField(comment.getPost(), "deletedAt", java.time.LocalDateTime.now());
        given(commentRepository.findById(5L)).willReturn(Optional.of(comment));

        assertThatThrownBy(() -> commentService.update(5L, new CommentUpdateRequestDto("새 내용"), 0L, authOf(owner)))
                .isInstanceOf(PostNotFoundException.class);
    }

    @Test
    @DisplayName("delete: 관리자는 소프트 삭제된 글 아래의 댓글도 지울 수 있다")
    void delete_whenPostDeletedAndAdmin_allowed() {
        User author = userWithEmail("owner@example.com", 1L);
        Comment comment = commentOf(author, 5L);
        ReflectionTestUtils.setField(comment.getPost(), "deletedAt", java.time.LocalDateTime.now());
        given(commentRepository.findById(5L)).willReturn(Optional.of(comment));
        given(commentRepository.countRepliesByParentIdIn(List.of(5L))).willReturn(Map.of());

        commentService.delete(5L, authOf(9L, "admin@example.com", Role.ADMIN));

        verify(commentRepository).delete(comment);
    }

    @Test
    @DisplayName("findNextPageForView: 삭제된 글의 댓글 페이지는 일반 사용자에게 글이 없는 것으로 답하고 관리자는 읽는다")
    void findNextPageForView_whenPostDeleted_onlyAdminReads() {
        given(postRepository.existsVisibleById(1L)).willReturn(false);

        assertThatThrownBy(() -> commentService.findNextPageForView(1L, 20L, authOf(1L, "u@example.com", Role.USER)))
                .isInstanceOf(PostNotFoundException.class);

        given(commentRepository.findPageByPostIdAsc(1L, 20L, PageRequest.of(0, 21))).willReturn(List.of());
        assertThat(commentService.findNextPageForView(1L, 20L, authOf(9L, "admin@example.com", Role.ADMIN)).comments())
                .isEmpty();
    }

    @Test
    @DisplayName("findRepliesPage: 삭제된 글 아래 부모의 답글 페이지는 일반 사용자에게 글이 없는 것으로 답한다")
    void findRepliesPage_whenPostDeleted_throwsPostNotFound() {
        User owner = userWithEmail("owner@example.com", 1L);
        Comment parent = commentOf(owner, 5L);
        ReflectionTestUtils.setField(parent.getPost(), "deletedAt", java.time.LocalDateTime.now());
        given(commentRepository.findById(5L)).willReturn(Optional.of(parent));

        assertThatThrownBy(() -> commentService.findRepliesPage(5L, null, authOf(1L, "u@example.com", Role.USER)))
                .isInstanceOf(PostNotFoundException.class);
    }

    private static Comment blindedCommentOf(User owner, Long id) {
        Comment comment = commentOf(owner, id);
        ReflectionTestUtils.setField(comment, "blindedAt", java.time.LocalDateTime.now());
        return comment;
    }

    @Test
    @DisplayName("숨긴 댓글: 일반 사용자에게는 내용이 비고 관리 권한이 없으며, 관리자에게는 원문과 관리 권한이 보인다")
    void blindedComment_isMaskedForNonAdminAndVisibleToAdmin() {
        User author = userWithEmail("author@example.com", 1L);
        Comment comment = blindedCommentOf(author, 5L);
        given(commentRepository.findPageByPostIdAsc(1L, null, PageRequest.of(0, 21))).willReturn(List.of(comment));
        given(commentRepository.countRepliesByParentIdIn(List.of(5L))).willReturn(Map.of());
        given(commentRepository.findInitialRepliesGroupedByParentIdIn(List.of(5L), 20)).willReturn(Map.of());

        // 작성자 본인도 일반 사용자와 같다 — 숨긴 내용을 다시 볼 수 없다.
        CommentViewDto asAuthor = commentService.findInitialPageForView(1L, authOf(author)).comments().get(0);
        assertThat(asAuthor.blinded()).isTrue();
        assertThat(asAuthor.content()).isEmpty();
        assertThat(asAuthor.canManage()).isFalse();
        assertThat(asAuthor.canModerate()).isFalse();

        CommentViewDto asAdmin = commentService.findInitialPageForView(1L, authOf(9L, "admin@example.com", Role.ADMIN))
                .comments().get(0);
        assertThat(asAdmin.blinded()).isTrue();
        assertThat(asAdmin.content()).isEqualTo("원래 댓글");
        assertThat(asAdmin.canManage()).isTrue();
        assertThat(asAdmin.canModerate()).isTrue();
    }

    @Test
    @DisplayName("update·delete: 숨긴 댓글은 작성자도 고치거나 지울 수 없다(403)")
    void updateAndDelete_whenBlindedAndAuthor_areForbidden() {
        User author = userWithEmail("author@example.com", 1L);
        Comment comment = blindedCommentOf(author, 5L);
        given(commentRepository.findById(5L)).willReturn(Optional.of(comment));
        given(userRepository.findById(1L)).willReturn(Optional.of(author));

        assertThatThrownBy(() -> commentService.update(5L, new CommentUpdateRequestDto("새 내용"), 0L, authOf(author)))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(() -> commentService.delete(5L, authOf(author)))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);

        verify(commentRepository, never()).delete(any(Comment.class));
    }

    @Test
    @DisplayName("blind: 댓글을 숨기고, 이미 소프트 삭제된 댓글은 건드리지 않는다")
    void blind_blindsComment_andSkipsSoftDeleted() {
        User author = userWithEmail("author@example.com", 1L);
        Comment live = commentOf(author, 5L);
        Comment softDeleted = commentOf(author, 6L);
        softDeleted.softDelete();
        given(commentRepository.findById(5L)).willReturn(Optional.of(live));
        given(commentRepository.findById(6L)).willReturn(Optional.of(softDeleted));

        commentService.blind(5L);
        commentService.blind(6L);

        verify(commentRepository).blind(org.mockito.ArgumentMatchers.eq(5L), any());
        verify(commentRepository, never()).blind(org.mockito.ArgumentMatchers.eq(6L), any());
    }

    @Test
    @DisplayName("unblind: 숨겨진 댓글이 아니면 댓글이 없는 것으로 답한다")
    void unblind_whenNotBlinded_throwsNotFound() {
        given(commentRepository.unblind(5L)).willReturn(0);

        assertThatThrownBy(() -> commentService.unblind(5L))
                .isInstanceOf(com.kraft.shared.exception.NotFoundException.class);
    }

    @Test
    @DisplayName("update: 숨긴 글 아래의 댓글은 작성자도 수정할 수 없다(글이 없는 것으로 답한다)")
    void update_whenPostBlinded_throwsPostNotFound() {
        User owner = userWithEmail("owner@example.com", 1L);
        Comment comment = commentOf(owner, 5L);
        ReflectionTestUtils.setField(comment.getPost(), "blindedAt", java.time.LocalDateTime.now());
        given(commentRepository.findById(5L)).willReturn(Optional.of(comment));

        assertThatThrownBy(() -> commentService.update(5L, new CommentUpdateRequestDto("새 내용"), 0L, authOf(owner)))
                .isInstanceOf(PostNotFoundException.class);
    }
}
