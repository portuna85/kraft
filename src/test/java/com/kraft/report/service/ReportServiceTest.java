package com.kraft.report.service;

import com.kraft.comment.domain.Comment;
import com.kraft.comment.domain.CommentRepository;
import com.kraft.post.domain.Post;
import com.kraft.post.domain.PostRepository;
import com.kraft.post.service.PostService;
import com.kraft.report.domain.Report;
import com.kraft.report.domain.ReportReason;
import com.kraft.report.domain.ReportRepository;
import com.kraft.report.domain.ReportStatus;
import com.kraft.report.domain.ReportTargetType;
import com.kraft.report.dto.ReportSaveRequestDto;
import com.kraft.report.dto.ReportViewDto;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
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
 * 이미 신고한 대상)와, 처리가 <b>기존 삭제 경로를 그대로 부르는지</b>를 고정한다.
 * <p>
 * 후자가 중요한 이유: 여기서 저장소를 직접 지우면 이미지 정리 예약·커밋 후 파일 삭제 같은
 * 뒷정리가 통째로 빠진다. 그래서 "무엇을 호출했는가"가 이 클래스에서는 의미 있는 검증이다.
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
    private PostService postService;

    @Mock
    private CommentService commentService;

    private ReportService reportService;

    private static final String REPORTER_EMAIL = "reporter@example.com";

    @BeforeEach
    void setUp() {
        reportService = new ReportService(reportRepository, postRepository, commentRepository,
                userRepository, postService, commentService);
    }

    private static User userWithId(Long id, String email) {
        User user = User.builder().name("user" + id).email(email).password("encoded").role(Role.USER).build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private static Authentication authOf(String email) {
        return new UsernamePasswordAuthenticationToken(email, "n/a", List.of());
    }

    private void givenReporter(User reporter) {
        given(userRepository.findByEmailHash(any())).willReturn(Optional.of(reporter));
    }

    private static Post postBy(User author) {
        return Post.builder().title("제목").content("내용").user(author).build();
    }

    @Test
    @DisplayName("report: 대상이 이미 없으면 접수하지 않는다")
    void report_whenTargetIsGone_isRejected() {
        givenReporter(userWithId(1L, REPORTER_EMAIL));
        given(postRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> reportService.report(
                new ReportSaveRequestDto(ReportTargetType.POST, 99L, ReportReason.SPAM, null),
                authOf(REPORTER_EMAIL)))
                .isInstanceOf(IllegalArgumentException.class)
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
                authOf(REPORTER_EMAIL)))
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
                authOf(REPORTER_EMAIL)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이미 신고한 대상");

        verify(reportRepository, never()).save(any());
    }

    @Test
    @DisplayName("resolve: 게시글 신고를 받아들이면 기존 삭제 경로로 지운다(뒷정리를 다시 만들지 않는다)")
    void resolve_deletesPostThroughPostService() {
        User admin = userWithId(9L, "admin@example.com");
        Report report = Report.builder()
                .reporter(userWithId(1L, REPORTER_EMAIL))
                .targetType(ReportTargetType.POST)
                .targetId(10L)
                .reason(ReportReason.SPAM)
                .build();
        ReflectionTestUtils.setField(report, "id", 5L);
        given(reportRepository.findById(5L)).willReturn(Optional.of(report));
        given(userRepository.findByEmailHash(any())).willReturn(Optional.of(admin));
        given(postRepository.findById(10L)).willReturn(Optional.of(postBy(userWithId(2L, "other@example.com"))));
        given(reportRepository.findByTargetTypeAndTargetIdAndStatus(
                ReportTargetType.POST, 10L, ReportStatus.PENDING)).willReturn(List.of(report));

        Authentication adminAuth = authOf("admin@example.com");
        reportService.resolve(5L, adminAuth);

        verify(postService).delete(10L, adminAuth);
        org.assertj.core.api.Assertions.assertThat(report.getStatus()).isEqualTo(ReportStatus.RESOLVED);
        org.assertj.core.api.Assertions.assertThat(report.getHandledBy()).isSameAs(admin);
    }

    @Test
    @DisplayName("resolve: 대상이 이미 지워졌으면 삭제는 건너뛰고 기록만 남긴다")
    void resolve_whenTargetAlreadyGone_skipsDeletion() {
        User admin = userWithId(9L, "admin@example.com");
        Report report = Report.builder()
                .reporter(userWithId(1L, REPORTER_EMAIL))
                .targetType(ReportTargetType.COMMENT)
                .targetId(77L)
                .reason(ReportReason.ABUSE)
                .build();
        ReflectionTestUtils.setField(report, "id", 6L);
        given(reportRepository.findById(6L)).willReturn(Optional.of(report));
        given(userRepository.findByEmailHash(any())).willReturn(Optional.of(admin));
        given(commentRepository.findById(77L)).willReturn(Optional.empty());
        given(reportRepository.findByTargetTypeAndTargetIdAndStatus(
                ReportTargetType.COMMENT, 77L, ReportStatus.PENDING)).willReturn(List.of(report));

        reportService.resolve(6L, authOf("admin@example.com"));

        verify(commentService, never()).delete(anyLong(), any());
        org.assertj.core.api.Assertions.assertThat(report.getStatus()).isEqualTo(ReportStatus.RESOLVED);
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
        given(userRepository.findByEmailHash(any())).willReturn(Optional.of(admin));
        given(postRepository.findById(10L)).willReturn(Optional.of(postBy(userWithId(2L, "other@example.com"))));
        given(reportRepository.findByTargetTypeAndTargetIdAndStatus(
                ReportTargetType.POST, 10L, ReportStatus.PENDING)).willReturn(List.of(handled, other));

        reportService.resolve(5L, authOf("admin@example.com"));

        // 이미 지운 글이 목록에 남아 있으면 관리자가 같은 판단을 반복하게 된다.
        org.assertj.core.api.Assertions.assertThat(other.getStatus()).isEqualTo(ReportStatus.RESOLVED);
        // 삭제는 한 번만 부른다.
        verify(postService).delete(anyLong(), any());
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
        given(userRepository.findByEmailHash(any())).willReturn(Optional.of(admin));

        reportService.reject(5L, authOf("admin@example.com"));

        verify(postService, never()).delete(anyLong(), any());
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

        assertThatThrownBy(() -> reportService.resolve(5L, authOf("admin@example.com")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이미 처리된 신고");

        verify(postService, never()).delete(anyLong(), any());
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
                authOf(REPORTER_EMAIL));

        org.assertj.core.api.Assertions.assertThat(id).isEqualTo(3L);
        verify(reportRepository).save(any(Report.class));
    }
}
