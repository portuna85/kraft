package com.kraft.report.service;

import com.kraft.comment.domain.Comment;
import com.kraft.comment.domain.CommentRepository;
import com.kraft.post.domain.Post;
import com.kraft.post.domain.PostRepository;
import com.kraft.post.service.PostModerationService;
import com.kraft.report.domain.Report;
import com.kraft.report.domain.ReportReason;
import com.kraft.report.domain.ReportRepository;
import com.kraft.report.domain.ReportStatus;
import com.kraft.report.domain.ReportTargetType;
import com.kraft.report.dto.ReportSaveRequestDto;
import com.kraft.report.dto.ReportViewDto;
import com.kraft.support.TestAuthentication;
import com.kraft.user.domain.Role;
import com.kraft.user.domain.User;
import com.kraft.user.domain.UserRepository;
import com.kraft.comment.service.CommentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * {@link ReportService} 단위 테스트. 접수 단계에서 거절해야 하는 세 가지(없는 대상, 자기 글,
 * 이미 신고한 대상)와, 처리가 대상을 <b>지우지 않고 숨기는지</b>를 고정한다.
 * <p>
 * 후자가 중요한 이유: 숨김은 되돌릴 수 있고 근거가 남지만 삭제는 그렇지 않다. 그래서 "무엇을
 * 호출했는가"(숨김이지 삭제가 아니다)가 이 클래스에서는 의미 있는 검증이다.
 */
@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private PostRepository postRepository;

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PostModerationService postModerationService;

    @Mock
    private CommentService commentService;

    private ReportService reportService;

    private static final String REPORTER_EMAIL = "reporter@example.com";

    @BeforeEach
    void setUp() {
        reportService = new ReportService(reportRepository, postRepository, commentRepository,
                userRepository, postModerationService, commentService);
    }

    private static User userWithId(Long id, String email) {
        User user = User.builder().name("user" + id).email(email).password("encoded").role(Role.USER).build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private static Authentication authOf(User user) {
        return TestAuthentication.of(user);
    }

    private static Authentication authOf(Long id, String email, Role role) {
        return TestAuthentication.of(id, email, role);
    }

    private void givenReporter(User reporter) {
        given(userRepository.findById(any())).willReturn(Optional.of(reporter));
    }

    private static Post postBy(User author) {
        return Post.builder().title("제목").content("내용").user(author).build();
    }

    @Test
    @DisplayName("report: 대상이 이미 없으면 접수하지 않는다")
    void report_whenTargetIsGone_isRejected() {
        User reporter = userWithId(1L, REPORTER_EMAIL);
        givenReporter(reporter);
        given(postRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> reportService.report(
                new ReportSaveRequestDto(ReportTargetType.POST, 99L, ReportReason.SPAM, null),
                authOf(reporter)))
                .isInstanceOf(com.kraft.shared.exception.NotFoundException.class)
                .hasMessageContaining("존재하지 않는 대상");

        verify(reportRepository, never()).save(any());
    }

    @Test
    @DisplayName("report: 자기 글은 신고할 수 없다 — 지우고 싶으면 직접 지우면 된다")
    void report_onOwnPost_isRejected() {
        User reporter = userWithId(1L, REPORTER_EMAIL);
        givenReporter(reporter);
        given(postRepository.findById(10L)).willReturn(Optional.of(postBy(reporter)));

        assertThatThrownBy(() -> reportService.report(
                new ReportSaveRequestDto(ReportTargetType.POST, 10L, ReportReason.SPAM, null),
                authOf(reporter)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("자신이 쓴 글");

        verify(reportRepository, never()).save(any());
    }

    @Test
    @DisplayName("report: 같은 대상을 두 번 신고할 수 없다")
    void report_whenAlreadyReported_isRejected() {
        User reporter = userWithId(1L, REPORTER_EMAIL);
        givenReporter(reporter);
        given(postRepository.findById(10L)).willReturn(Optional.of(postBy(userWithId(2L, "other@example.com"))));
        given(reportRepository.existsByReporterIdAndTargetTypeAndTargetId(1L, ReportTargetType.POST, 10L))
                .willReturn(true);

        assertThatThrownBy(() -> reportService.report(
                new ReportSaveRequestDto(ReportTargetType.POST, 10L, ReportReason.ABUSE, null),
                authOf(reporter)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이미 신고한 대상");

        verify(reportRepository, never()).save(any());
    }

    @Test
    @DisplayName("resolve: 게시글 신고를 받아들이면 지우지 않고 숨긴다")
    void resolve_blindsPost() {
        User admin = userWithId(9L, "admin@example.com");
        Report report = Report.builder()
                .reporter(userWithId(1L, REPORTER_EMAIL))
                .targetType(ReportTargetType.POST)
                .targetId(10L)
                .reason(ReportReason.SPAM)
                .build();
        ReflectionTestUtils.setField(report, "id", 5L);
        given(reportRepository.findById(5L)).willReturn(Optional.of(report));
        given(userRepository.findById(any())).willReturn(Optional.of(admin));
        given(postRepository.findById(10L)).willReturn(Optional.of(postBy(userWithId(2L, "other@example.com"))));
        given(reportRepository.findByTargetTypeAndTargetIdAndStatus(
                ReportTargetType.POST, 10L, ReportStatus.PENDING)).willReturn(List.of(report));

        Authentication adminAuth = authOf(admin);
        reportService.resolve(5L, adminAuth);

        verify(postModerationService).blindPost(10L);
        org.assertj.core.api.Assertions.assertThat(report.getStatus()).isEqualTo(ReportStatus.RESOLVED);
        org.assertj.core.api.Assertions.assertThat(report.getHandledBy()).isSameAs(admin);
    }

    @Test
    @DisplayName("resolve: 댓글 신고를 받아들이면 댓글만 숨기고, 계속 보이는 답글에 걸린 신고는 닫지 않는다")
    void resolve_blindsCommentAndKeepsReplyReportsOpen() {
        User admin = userWithId(9L, "admin@example.com");
        Report report = Report.builder()
                .reporter(userWithId(1L, REPORTER_EMAIL))
                .targetType(ReportTargetType.COMMENT).targetId(77L).reason(ReportReason.ABUSE).build();
        ReflectionTestUtils.setField(report, "id", 6L);
        com.kraft.comment.domain.Comment comment = com.kraft.comment.domain.Comment.builder()
                .content("문제의 댓글").user(userWithId(2L, "author@example.com")).build();
        given(reportRepository.findById(6L)).willReturn(Optional.of(report));
        given(userRepository.findById(any())).willReturn(Optional.of(admin));
        given(commentRepository.findById(77L)).willReturn(Optional.of(comment));
        given(reportRepository.findByTargetTypeAndTargetIdAndStatus(
                ReportTargetType.COMMENT, 77L, ReportStatus.PENDING)).willReturn(List.of(report));

        reportService.resolve(6L, authOf(admin));

        verify(commentService).blind(77L);
        // 댓글을 숨겨도 답글은 그대로 보이므로 답글의 대기 신고를 같이 닫지 않는다.
        verify(commentRepository, never()).findIdsByParentId(any());
        verify(reportRepository, never()).findByTargetTypeAndTargetIdInAndStatus(any(), any(), any());
        org.assertj.core.api.Assertions.assertThat(report.getStatus()).isEqualTo(ReportStatus.RESOLVED);
    }

    @Test
    @DisplayName("resolve: 글을 숨기면 그 아래 댓글에 걸린 대기 신고도 함께 닫는다")
    void resolve_blindingPostClosesReportsOnItsComments() {
        User admin = userWithId(9L, "admin@example.com");
        Report report = Report.builder()
                .reporter(userWithId(1L, REPORTER_EMAIL))
                .targetType(ReportTargetType.POST).targetId(10L).reason(ReportReason.SPAM).build();
        Report commentReport = Report.builder()
                .reporter(userWithId(3L, "another@example.com"))
                .targetType(ReportTargetType.COMMENT).targetId(70L).reason(ReportReason.ABUSE).build();
        ReflectionTestUtils.setField(report, "id", 5L);
        given(reportRepository.findById(5L)).willReturn(Optional.of(report));
        given(userRepository.findById(any())).willReturn(Optional.of(admin));
        given(postRepository.findById(10L)).willReturn(Optional.of(postBy(userWithId(2L, "other@example.com"))));
        given(commentRepository.findIdsByPostId(10L)).willReturn(List.of(70L));
        given(reportRepository.findByTargetTypeAndTargetIdAndStatus(
                ReportTargetType.POST, 10L, ReportStatus.PENDING)).willReturn(List.of(report));
        given(reportRepository.findByTargetTypeAndTargetIdInAndStatus(
                ReportTargetType.COMMENT, List.of(70L), ReportStatus.PENDING)).willReturn(List.of(commentReport));

        reportService.resolve(5L, authOf(admin));

        org.assertj.core.api.Assertions.assertThat(commentReport.getStatus()).isEqualTo(ReportStatus.RESOLVED);
    }

    @Test
    @DisplayName("report: 이미 숨겨진 글은 신고를 접수하지 않는다")
    void report_whenTargetIsBlinded_isRejected() {
        User reporter = userWithId(1L, REPORTER_EMAIL);
        givenReporter(reporter);
        Post blinded = postBy(userWithId(2L, "other@example.com"));
        ReflectionTestUtils.setField(blinded, "blindedAt", java.time.LocalDateTime.now());
        given(postRepository.findById(10L)).willReturn(Optional.of(blinded));

        assertThatThrownBy(() -> reportService.report(
                new ReportSaveRequestDto(ReportTargetType.POST, 10L, ReportReason.SPAM, null), authOf(reporter)))
                .isInstanceOf(com.kraft.shared.exception.NotFoundException.class);

        verify(reportRepository, never()).save(any());
    }

    @Test
    @DisplayName("resolve: 대상이 이미 지워졌으면 숨기기는 건너뛰고 기록만 남긴다")
    void resolve_whenTargetAlreadyGone_skipsBlinding() {
        User admin = userWithId(9L, "admin@example.com");
        Report report = Report.builder()
                .reporter(userWithId(1L, REPORTER_EMAIL))
                .targetType(ReportTargetType.COMMENT)
                .targetId(77L)
                .reason(ReportReason.ABUSE)
                .build();
        ReflectionTestUtils.setField(report, "id", 6L);
        given(reportRepository.findById(6L)).willReturn(Optional.of(report));
        given(userRepository.findById(any())).willReturn(Optional.of(admin));
        given(commentRepository.findById(77L)).willReturn(Optional.empty());
        given(reportRepository.findByTargetTypeAndTargetIdAndStatus(
                ReportTargetType.COMMENT, 77L, ReportStatus.PENDING)).willReturn(List.of(report));

        reportService.resolve(6L, authOf(admin));

        verify(commentService, never()).blind(anyLong());
        org.assertj.core.api.Assertions.assertThat(report.getStatus()).isEqualTo(ReportStatus.RESOLVED);
    }

    /**
     * A-SEC-07: 대상이 이미 사라져 실시간 조회가 안 되면, 접수 시점 스냅샷의 작성자로
     * 정지를 건다 — 그러지 않으면 "쓰고 → 신고되면 지우고 → 다시 쓰기"를 반복해도 제재할
     * 수 없다.
     */
    @Test
    @DisplayName("resolve: 대상이 사라졌어도 정지는 접수 시점 스냅샷의 작성자에게 건다")
    void resolve_whenTargetGone_suspendsSnapshotAuthor() {
        User admin = userWithId(9L, "admin@example.com");
        User snapshotAuthor = userWithId(2L, "author@example.com");
        Report report = Report.builder()
                .reporter(userWithId(1L, REPORTER_EMAIL))
                .targetType(ReportTargetType.COMMENT)
                .targetId(77L)
                .reason(ReportReason.ABUSE)
                .targetAuthor(snapshotAuthor)
                .targetContentSnapshot("문제의 댓글")
                .build();
        ReflectionTestUtils.setField(report, "id", 6L);
        given(reportRepository.findById(6L)).willReturn(Optional.of(report));
        given(userRepository.findById(any())).willReturn(Optional.of(admin));
        given(commentRepository.findById(77L)).willReturn(Optional.empty());
        given(reportRepository.findByTargetTypeAndTargetIdAndStatus(
                ReportTargetType.COMMENT, 77L, ReportStatus.PENDING)).willReturn(List.of(report));

        reportService.resolve(6L, authOf(admin), 7);

        org.assertj.core.api.Assertions.assertThat(snapshotAuthor.isSuspended()).isTrue();
    }

    @Test
    @DisplayName("resolve: 같은 대상에 쌓인 다른 대기 신고도 함께 처리한다")
    void resolve_alsoClosesOtherPendingReportsOnSameTarget() {
        User admin = userWithId(9L, "admin@example.com");
        Report handled = Report.builder()
                .reporter(userWithId(1L, REPORTER_EMAIL))
                .targetType(ReportTargetType.POST).targetId(10L).reason(ReportReason.SPAM).build();
        Report other = Report.builder()
                .reporter(userWithId(3L, "another@example.com"))
                .targetType(ReportTargetType.POST).targetId(10L).reason(ReportReason.ABUSE).build();
        ReflectionTestUtils.setField(handled, "id", 5L);
        ReflectionTestUtils.setField(other, "id", 6L);
        given(reportRepository.findById(5L)).willReturn(Optional.of(handled));
        given(userRepository.findById(any())).willReturn(Optional.of(admin));
        given(postRepository.findById(10L)).willReturn(Optional.of(postBy(userWithId(2L, "other@example.com"))));
        given(reportRepository.findByTargetTypeAndTargetIdAndStatus(
                ReportTargetType.POST, 10L, ReportStatus.PENDING)).willReturn(List.of(handled, other));

        reportService.resolve(5L, authOf(admin));

        // 이미 숨긴 글이 목록에 남아 있으면 관리자가 같은 판단을 반복하게 된다.
        org.assertj.core.api.Assertions.assertThat(other.getStatus()).isEqualTo(ReportStatus.RESOLVED);
        // 숨기기는 한 번만 부른다.
        verify(postModerationService).blindPost(anyLong());
    }

    @Test
    @DisplayName("resolve: 음수 정지 기간은 조용히 건너뛰지 않고 거절한다(O05)")
    void resolve_withNegativeSuspendDays_isRejected() {
        assertThatThrownBy(() -> reportService.resolve(5L, authOf(9L, "admin@example.com", Role.ADMIN), -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("정지 기간");

        verify(reportRepository, never()).findById(any());
    }

    @Test
    @DisplayName("resolve: 지나치게 큰 정지 기간은 거절한다(O05)")
    void resolve_withExcessiveSuspendDays_isRejected() {
        assertThatThrownBy(() -> reportService.resolve(5L, authOf(9L, "admin@example.com", Role.ADMIN), 3651))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("정지 기간");

        verify(reportRepository, never()).findById(any());
    }

    @Test
    @DisplayName("reject: 대상을 건드리지 않고 신고만 닫는다")
    void reject_keepsTargetAndClosesReport() {
        User admin = userWithId(9L, "admin@example.com");
        Report report = Report.builder()
                .reporter(userWithId(1L, REPORTER_EMAIL))
                .targetType(ReportTargetType.POST).targetId(10L).reason(ReportReason.OTHER).build();
        ReflectionTestUtils.setField(report, "id", 5L);
        given(reportRepository.findById(5L)).willReturn(Optional.of(report));
        given(userRepository.findById(any())).willReturn(Optional.of(admin));

        reportService.reject(5L, authOf(admin));

        verify(postModerationService, never()).blindPost(anyLong());
        org.assertj.core.api.Assertions.assertThat(report.getStatus()).isEqualTo(ReportStatus.REJECTED);
    }

    @Test
    @DisplayName("resolve: 이미 처리된 신고는 다시 처리할 수 없다")
    void resolve_whenAlreadyHandled_isRejected() {
        User admin = userWithId(9L, "admin@example.com");
        Report report = Report.builder()
                .reporter(userWithId(1L, REPORTER_EMAIL))
                .targetType(ReportTargetType.POST).targetId(10L).reason(ReportReason.SPAM).build();
        ReflectionTestUtils.setField(report, "id", 5L);
        report.reject(admin);
        given(reportRepository.findById(5L)).willReturn(Optional.of(report));

        assertThatThrownBy(() -> reportService.resolve(5L, authOf(admin)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이미 처리된 신고");

        verify(postModerationService, never()).blindPost(anyLong());
    }

    @Test
    @DisplayName("findPending: 대상 종류별로 배치 조회해 미리보기·작성자를 채우고, 삭제된 대상은 null로 남긴다")
    void findPending_batchFetchesTargetsByTypeAndFillsPreviewAndAuthor() {
        User postAuthor = userWithId(2L, "post-author@example.com");
        User commentAuthor = userWithId(3L, "comment-author@example.com");

        Report postReport = Report.builder()
                .reporter(userWithId(1L, REPORTER_EMAIL))
                .targetType(ReportTargetType.POST).targetId(10L).reason(ReportReason.SPAM).build();
        ReflectionTestUtils.setField(postReport, "id", 100L);
        Report commentReport = Report.builder()
                .reporter(userWithId(1L, REPORTER_EMAIL))
                .targetType(ReportTargetType.COMMENT).targetId(20L).reason(ReportReason.ABUSE).build();
        ReflectionTestUtils.setField(commentReport, "id", 101L);
        Report missingTargetReport = Report.builder()
                .reporter(userWithId(1L, REPORTER_EMAIL))
                .targetType(ReportTargetType.POST).targetId(999L).reason(ReportReason.OTHER).build();
        ReflectionTestUtils.setField(missingTargetReport, "id", 102L);

        Post post = postBy(postAuthor);
        ReflectionTestUtils.setField(post, "id", 10L);
        Comment comment = Comment.builder().content("문제가 되는 댓글").post(post).user(commentAuthor).build();
        ReflectionTestUtils.setField(comment, "id", 20L);

        PageRequest pageable = PageRequest.of(0, 10);
        given(reportRepository.findByStatusOrderByIdAsc(ReportStatus.PENDING, pageable))
                .willReturn(new PageImpl<>(List.of(postReport, commentReport, missingTargetReport), pageable, 3));
        // missingTargetReport(999L)는 이미 지워진 대상이라 배치 조회 결과에 포함되지 않는다.
        given(postRepository.findAllByIdInWithUser(List.of(10L, 999L))).willReturn(List.of(post));
        given(commentRepository.findAllByIdInWithUser(List.of(20L))).willReturn(List.of(comment));

        Page<ReportViewDto> result = reportService.findPending(pageable);

        ReportViewDto postView = result.getContent().stream()
                .filter(v -> v.id().equals(100L)).findFirst().orElseThrow();
        assertThat(postView.targetPreview()).isEqualTo("제목");
        assertThat(postView.targetAuthor()).isEqualTo(postAuthor.getName());

        ReportViewDto commentView = result.getContent().stream()
                .filter(v -> v.id().equals(101L)).findFirst().orElseThrow();
        assertThat(commentView.targetPreview()).isEqualTo("문제가 되는 댓글");
        assertThat(commentView.targetAuthor()).isEqualTo(commentAuthor.getName());

        ReportViewDto missingView = result.getContent().stream()
                .filter(v -> v.id().equals(102L)).findFirst().orElseThrow();
        assertThat(missingView.targetPreview()).isNull();
        assertThat(missingView.targetAuthor()).isNull();
    }

    @Test
    @DisplayName("findPending: 대기 목록이 비어 있으면 대상 배치 조회 자체를 하지 않는다")
    void findPending_whenPageIsEmpty_skipsBatchFetch() {
        PageRequest pageable = PageRequest.of(0, 10);
        given(reportRepository.findByStatusOrderByIdAsc(ReportStatus.PENDING, pageable))
                .willReturn(new PageImpl<>(List.of(), pageable, 0));

        reportService.findPending(pageable);

        verify(postRepository, never()).findAllByIdInWithUser(any());
        verify(commentRepository, never()).findAllByIdInWithUser(any());
    }

    @Test
    @DisplayName("report: 댓글 신고도 같은 규칙으로 접수된다")
    void report_onComment_isAccepted() {
        User reporter = userWithId(1L, REPORTER_EMAIL);
        givenReporter(reporter);
        Comment comment = Comment.builder()
                .content("문제가 되는 댓글")
                .post(postBy(userWithId(2L, "other@example.com")))
                .user(userWithId(2L, "other@example.com"))
                .build();
        given(commentRepository.findById(77L)).willReturn(Optional.of(comment));
        given(reportRepository.existsByReporterIdAndTargetTypeAndTargetId(1L, ReportTargetType.COMMENT, 77L))
                .willReturn(false);
        Report saved = Report.builder().reporter(reporter).targetType(ReportTargetType.COMMENT)
                .targetId(77L).reason(ReportReason.ABUSE).build();
        ReflectionTestUtils.setField(saved, "id", 3L);
        given(reportRepository.save(any())).willReturn(saved);

        Long id = reportService.report(
                new ReportSaveRequestDto(ReportTargetType.COMMENT, 77L, ReportReason.ABUSE, "욕설입니다"),
                authOf(reporter));

        org.assertj.core.api.Assertions.assertThat(id).isEqualTo(3L);
        verify(reportRepository).save(any(Report.class));
    }

    /** A-SEC-07: 접수 시점의 작성자·제목·본문을 스냅샷으로 함께 저장한다. */
    @Test
    @DisplayName("report: 접수 시점의 작성자·제목·본문을 스냅샷으로 저장한다")
    void report_savesTargetSnapshot() {
        User reporter = userWithId(1L, REPORTER_EMAIL);
        givenReporter(reporter);
        User author = userWithId(2L, "author@example.com");
        Post post = Post.builder().title("제목").content("내용".repeat(300)).user(author).build();
        given(postRepository.findById(10L)).willReturn(Optional.of(post));
        given(reportRepository.existsByReporterIdAndTargetTypeAndTargetId(1L, ReportTargetType.POST, 10L))
                .willReturn(false);
        given(reportRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        reportService.report(
                new ReportSaveRequestDto(ReportTargetType.POST, 10L, ReportReason.SPAM, null),
                authOf(reporter));

        org.mockito.ArgumentCaptor<Report> captor = org.mockito.ArgumentCaptor.forClass(Report.class);
        verify(reportRepository).save(captor.capture());
        Report saved = captor.getValue();
        assertThat(saved.getTargetAuthor()).isSameAs(author);
        assertThat(saved.getTargetTitleSnapshot()).isEqualTo("제목");
        // 500자를 넘는 본문은 잘려서 저장된다(SNAPSHOT_LENGTH).
        assertThat(saved.getTargetContentSnapshot()).hasSize(501).endsWith("…");
    }

    /** A-BE-01: PENDING인 것만 닫고, 이미 처리된 것은 건드리지 않는다(리포지토리 필터가 보장). */
    @Test
    @DisplayName("closeForDeletedTargets: 대기 중인 신고를 TARGET_DELETED로 닫는다")
    void closeForDeletedTargets_closesPendingReports() {
        Report pending = Report.builder()
                .reporter(userWithId(1L, REPORTER_EMAIL))
                .targetType(ReportTargetType.POST).targetId(10L).reason(ReportReason.SPAM).build();
        given(reportRepository.findByTargetTypeAndTargetIdInAndStatus(
                ReportTargetType.POST, List.of(10L), ReportStatus.PENDING))
                .willReturn(List.of(pending));

        reportService.closeForDeletedTargets(ReportTargetType.POST, List.of(10L));

        assertThat(pending.getStatus()).isEqualTo(ReportStatus.TARGET_DELETED);
    }

    @Test
    @DisplayName("closeForDeletedTargets: 대상 id가 비어 있으면 조회조차 하지 않는다")
    void closeForDeletedTargets_withEmptyIds_skipsQuery() {
        reportService.closeForDeletedTargets(ReportTargetType.POST, List.of());

        verify(reportRepository, never()).findByTargetTypeAndTargetIdInAndStatus(any(), any(), any());
    }
}
