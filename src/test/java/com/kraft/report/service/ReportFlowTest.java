package com.kraft.report.service;

import com.kraft.post.domain.Post;
import com.kraft.post.domain.PostRepository;
import com.kraft.report.domain.Report;
import com.kraft.report.domain.ReportReason;
import com.kraft.report.domain.ReportRepository;
import com.kraft.report.domain.ReportStatus;
import com.kraft.report.domain.ReportTargetType;
import com.kraft.report.dto.ReportSaveRequestDto;
import com.kraft.report.dto.ReportViewDto;
import com.kraft.shared.security.WriteAccessPolicy;
import com.kraft.support.TestAuthentication;
import com.kraft.user.domain.Role;
import com.kraft.user.domain.User;
import com.kraft.user.domain.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 신고를 실제 DB로 검증한다. 단위 테스트가 볼 수 없는 두 가지가 여기 있다:
 * 중복 신고를 <b>DB 제약</b>이 최종적으로 막는지, 그리고 처리한 뒤 대기 목록과 대상 글이
 * 실제로 어떻게 되는지.
 */
@SpringBootTest
class ReportFlowTest {

    @Autowired
    private ReportService reportService;

    @Autowired
    private ReportRepository reportRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private com.kraft.post.service.PostService postService;

    @Autowired
    private com.kraft.comment.service.CommentService commentService;

    @Autowired
    private com.kraft.comment.domain.CommentRepository commentRepository;

    private User author;
    private User reporter;
    private User admin;
    private Post post;

    @BeforeEach
    void setUp() {
        reportRepository.deleteAll();

        author = saveUser("author", Role.USER);
        reporter = saveUser("reporter", Role.USER);
        admin = saveUser("admin", Role.ADMIN);
        post = postRepository.save(Post.builder().title("신고당할 글").content("내용").user(author).build());
    }

    /**
     * 남긴 신고를 반드시 치운다. 신고는 회원을 FK로 가리키므로, 남겨 두면 뒤에 도는 다른 테스트
     * 클래스가 {@code userRepository.deleteAll()}에서 참조 무결성 위반으로 깨진다(실제로 겪었다).
     */
    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        reportRepository.deleteAll();
    }

    private User saveUser(String prefix, Role role) {
        String unique = prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
        return userRepository.save(User.builder()
                .name(unique)
                .email(unique + "@example.com")
                .password("encoded")
                .role(role)
                .build());
    }

    private static Authentication authOf(User user) {
        return TestAuthentication.of(user);
    }

    private Long reportThePost(User by, ReportReason reason) {
        return reportService.report(
                new ReportSaveRequestDto(ReportTargetType.POST, post.getId(), reason, "문제가 있습니다"),
                authOf(by));
    }

    @Test
    @DisplayName("접수한 신고는 관리자 대기 목록에 대상 미리보기와 함께 나타난다")
    void reportedPostAppearsInPendingListWithPreview() {
        reportThePost(reporter, ReportReason.SPAM);

        List<ReportViewDto> pending = reportService.findPending(PageRequest.of(0, 20)).getContent();

        assertThat(pending).singleElement().satisfies(view -> {
            assertThat(view.targetPreview()).isEqualTo("신고당할 글");
            assertThat(view.targetAuthor()).isEqualTo(author.getName());
            assertThat(view.reporter()).isEqualTo(reporter.getName());
            assertThat(view.reasonTitle()).isEqualTo("스팸·광고");
        });
    }

    @Test
    @DisplayName("같은 대상을 두 번 신고하면 DB 제약이 최종적으로 막는다")
    void duplicateReportIsBlockedByDatabaseConstraint() {
        Report first = reportRepository.save(Report.builder()
                .reporter(reporter)
                .targetType(ReportTargetType.POST)
                .targetId(post.getId())
                .reason(ReportReason.SPAM)
                .build());
        assertThat(first.getId()).isNotNull();

        // 서비스의 사전 검사를 건너뛰고 저장소로 직접 넣어도 막혀야 한다 — 검사와 INSERT
        // 사이의 경쟁은 제약만이 최종적으로 막을 수 있다.
        assertThatThrownBy(() -> reportRepository.saveAndFlush(Report.builder()
                .reporter(reporter)
                .targetType(ReportTargetType.POST)
                .targetId(post.getId())
                .reason(ReportReason.ABUSE)
                .build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("처리하면 대상 글이 소프트 삭제되고 대기 목록에서도 빠진다")
    void resolvingDeletesTargetAndClearsPendingList() {
        reportThePost(reporter, ReportReason.ABUSE);
        Long reportId = reportRepository.findAll().get(0).getId();

        reportService.resolve(reportId, authOf(admin));

        assertThat(postRepository.findById(post.getId()).orElseThrow().isDeleted()).isTrue();
        assertThat(reportService.findPending(PageRequest.of(0, 20)).getContent()).isEmpty();
        assertThat(reportRepository.findById(reportId).orElseThrow().getStatus())
                .isEqualTo(ReportStatus.RESOLVED);
    }

    /**
     * A-BE-01: 관리자가 판단하기 전에 작성자 본인이 대상을 지우면(PostApiController 경로,
     * 관리자의 ReportService.resolve()를 거치지 않음) 대기 중이던 신고가 자동으로
     * TARGET_DELETED로 닫혀야 한다 — 그러지 않으면 "대상이 이미 삭제되었습니다"인 채로
     * 영원히 PENDING에 남는다. PostService.delete가 발행하는 이벤트를
     * {@code ReportService.onTargetDeleted}가 받아 처리하므로, 순수 Mockito 단위 테스트가
     * 아니라 실제 트랜잭션·이벤트가 도는 이 통합 테스트로만 검증할 수 있다.
     */
    @Test
    @DisplayName("A-BE-01: 작성자가 스스로 게시글을 지우면 대기 신고가 TARGET_DELETED로 닫힌다")
    void selfDeletePost_closesPendingReportAsTargetDeleted() {
        Long reportId = reportThePost(reporter, ReportReason.SPAM);

        postService.delete(post.getId(), authOf(author));

        Report report = reportRepository.findById(reportId).orElseThrow();
        assertThat(report.getStatus()).isEqualTo(ReportStatus.TARGET_DELETED);
        // 관리자가 처리한 게 아니므로 handledBy는 비어 있다 — RESOLVED와 구분되는 지점이다.
        assertThat(report.getHandledBy()).isNull();
    }

    /** 같은 이유(A-BE-01), 댓글 자기 삭제 경로. 답글이 없어 하드 삭제되는 경우다. */
    @Test
    @DisplayName("A-BE-01: 작성자가 스스로 댓글을 지우면 대기 신고가 TARGET_DELETED로 닫힌다")
    void selfDeleteComment_closesPendingReportAsTargetDeleted() {
        com.kraft.comment.domain.Comment comment = commentRepository.save(
                com.kraft.comment.domain.Comment.builder().content("문제의 댓글").post(post).user(author).build());
        Long reportId = reportService.report(
                new ReportSaveRequestDto(ReportTargetType.COMMENT, comment.getId(), ReportReason.ABUSE, null),
                authOf(reporter));

        commentService.delete(comment.getId(), authOf(author));

        Report report = reportRepository.findById(reportId).orElseThrow();
        assertThat(report.getStatus()).isEqualTo(ReportStatus.TARGET_DELETED);
    }

    /**
     * 대비 확인: 관리자가 신고를 처리하며 지운 경우는 지금처럼 RESOLVED로 남아야 한다 —
     * PostService.delete가 발행하는 이벤트가 이 경로를 TARGET_DELETED로 덮어쓰면 안 된다
     * (resolvingDeletesTargetAndClearsPendingList가 이미 이 회귀를 잡지만, 여기서 한 번 더
     * 명시적으로 남긴다).
     */
    @Test
    @DisplayName("A-BE-01: 관리자가 처리한 삭제는 TARGET_DELETED가 아니라 RESOLVED로 남는다")
    void adminResolvedDelete_staysResolvedNotTargetDeleted() {
        Long reportId = reportThePost(reporter, ReportReason.SPAM);

        reportService.resolve(reportId, authOf(admin));

        Report report = reportRepository.findById(reportId).orElseThrow();
        assertThat(report.getStatus()).isEqualTo(ReportStatus.RESOLVED);
        assertThat(report.getHandledBy().getId()).isEqualTo(admin.getId());
    }

    @Test
    @DisplayName("한 글에 여러 사람이 신고했어도 한 번 처리하면 모두 정리된다")
    void resolvingClearsEveryPendingReportOnTheSameTarget() {
        reportThePost(reporter, ReportReason.SPAM);
        User another = saveUser("another", Role.USER);
        reportThePost(another, ReportReason.ABUSE);
        assertThat(reportService.countPending()).isEqualTo(2);

        reportService.resolve(reportRepository.findAll().get(0).getId(), authOf(admin));

        // 이미 지운 글이 목록에 남아 있으면 관리자가 같은 판단을 반복하게 된다.
        assertThat(reportService.countPending()).isZero();
    }

    @Test
    @DisplayName("정지와 함께 처리하면 작성자는 기간 동안 글을 쓸 수 없다")
    void resolvingWithSuspensionBlocksTheAuthorFromWriting() {
        reportThePost(reporter, ReportReason.ABUSE);
        Long reportId = reportRepository.findAll().get(0).getId();

        reportService.resolve(reportId, authOf(admin), 7);

        User suspended = userRepository.findById(author.getId()).orElseThrow();
        assertThat(suspended.isSuspended()).isTrue();
        assertThat(suspended.getSuspensionReason()).contains("욕설·비방").contains("7일");
        // 지우기만 해서는 반복하는 사람을 막지 못한다. 실제로 작성이 거절되는지까지 본다.
        assertThatThrownBy(() -> WriteAccessPolicy.requireVerified(suspended))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("이용이 제한된 계정");
    }

    @Test
    @DisplayName("정지 없이 처리하면 작성자는 계속 글을 쓸 수 있다")
    void resolvingWithoutSuspensionLeavesTheAuthorAlone() {
        reportThePost(reporter, ReportReason.SPAM);

        reportService.resolve(reportRepository.findAll().get(0).getId(), authOf(admin));

        assertThat(userRepository.findById(author.getId()).orElseThrow().isSuspended()).isFalse();
    }

    @Test
    @DisplayName("반려하면 대상 글은 그대로 남고 신고만 닫힌다")
    void rejectingKeepsTheTarget() {
        reportThePost(reporter, ReportReason.OTHER);
        Long reportId = reportRepository.findAll().get(0).getId();

        reportService.reject(reportId, authOf(admin));

        assertThat(postRepository.findById(post.getId())).isPresent();
        assertThat(reportRepository.findById(reportId).orElseThrow().getStatus())
                .isEqualTo(ReportStatus.REJECTED);
        assertThat(reportService.countPending()).isZero();
    }

    /**
     * B11: 두 관리자가 같은 신고를 동시에 처리하면 Report.version 낙관적 잠금이 나중 커밋을
     * 충돌로 표면화해야 한다. 이 실패는 트랜잭션 커밋 시점(서비스 메서드가 반환한 뒤)에 나므로
     * 같은 스레드의 중첩 트랜잭션 호출로는 재현할 수 없다(B03/B07과 같은 이유) — 실제 스레드로
     * 검증한다.
     */
    @Test
    @DisplayName("B11: 같은 신고를 두 관리자가 동시에 resolve/reject하면 한쪽만 성공한다")
    void resolveAndReject_concurrentlyOnSameReport_onlyOneSucceeds() throws InterruptedException {
        reportThePost(reporter, ReportReason.ABUSE);
        Long reportId = reportRepository.findAll().get(0).getId();
        User otherAdmin = saveUser("admin2", Role.ADMIN);

        List<Boolean> results = new java.util.concurrent.CopyOnWriteArrayList<>();
        java.util.concurrent.CountDownLatch ready = new java.util.concurrent.CountDownLatch(2);
        Runnable resolveAttempt = () -> {
            ready.countDown();
            awaitQuietly(ready);
            try {
                reportService.resolve(reportId, authOf(admin));
                results.add(true);
            } catch (org.springframework.dao.OptimisticLockingFailureException | IllegalArgumentException e) {
                results.add(false);
            }
        };
        Runnable rejectAttempt = () -> {
            ready.countDown();
            awaitQuietly(ready);
            try {
                reportService.reject(reportId, authOf(otherAdmin));
                results.add(true);
            } catch (org.springframework.dao.OptimisticLockingFailureException | IllegalArgumentException e) {
                results.add(false);
            }
        };
        Thread t1 = new Thread(resolveAttempt);
        Thread t2 = new Thread(rejectAttempt);
        t1.start();
        t2.start();
        t1.join(10_000);
        t2.join(10_000);
        // 타임아웃 안에 안 끝났으면(교착 등) 여기서 바로 드러낸다(OPS-B1) — join()이 스레드가
        // 살아 있어도 조용히 반환하는 것과 달리, 이 단언은 그 상태를 테스트 실패로 만든다.
        assertThat(t1.isAlive()).as("t1이 타임아웃 안에 끝나야 한다").isFalse();
        assertThat(t2.isAlive()).as("t2가 타임아웃 안에 끝나야 한다").isFalse();

        assertThat(results).containsExactlyInAnyOrder(true, false);
        // 대상은 이긴 쪽의 처리 결과와 일관되어야 한다 — resolve가 이겼으면 지워지고, reject가
        // 이겼으면 남아 있다. 둘 다 절반씩 실행됐다는 증거가 없어야 한다(예: 정지가 반쯤 적용).
        Report finalState = reportRepository.findById(reportId).orElseThrow();
        boolean postExists = postRepository.findById(post.getId()).filter(p -> !p.isDeleted()).isPresent();
        if (finalState.getStatus() == ReportStatus.RESOLVED) {
            assertThat(postExists).isFalse();
        } else {
            assertThat(finalState.getStatus()).isEqualTo(ReportStatus.REJECTED);
            assertThat(postExists).isTrue();
        }
    }

    private static void awaitQuietly(java.util.concurrent.CountDownLatch latch) {
        try {
            latch.await(10, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * A-SEC-07: 접수 시점 스냅샷이 있으면 실시간 조회가 안 돼도(대상이 사라졌어도) 그
     * 스냅샷으로 물러선다 — 관리자가 무엇이 문제였는지 판단할 유일한 근거다.
     */
    @Test
    @DisplayName("대상이 이미 지워진 신고는 접수 시점 스냅샷을 보여주고 targetDeleted=true다")
    void reportForDeletedTargetFallsBackToSnapshot() {
        reportThePost(reporter, ReportReason.SEXUAL);
        postRepository.delete(post);

        List<ReportViewDto> pending = reportService.findPending(PageRequest.of(0, 20)).getContent();

        assertThat(pending).singleElement().satisfies(view -> {
            assertThat(view.targetPreview()).isEqualTo("신고당할 글");
            assertThat(view.targetAuthor()).isEqualTo(author.getName());
            assertThat(view.targetDeleted()).isTrue();
        });
    }

    /** V27 이전에 접수돼 스냅샷이 없는 신고는(마이그레이션 이전 데이터) 지금처럼 빈 채로 남는다. */
    @Test
    @DisplayName("스냅샷이 없는 신고(마이그레이션 이전 데이터)는 대상이 지워지면 미리보기가 비어 있다")
    void reportWithoutSnapshot_whenTargetDeleted_hasNoPreview() {
        Long reportId = reportThePost(reporter, ReportReason.SEXUAL);
        // V27 이전에 접수된 신고를 흉내 낸다 — 스냅샷 컬럼이 비어 있다.
        jdbcTemplate.update(
                "UPDATE reports SET target_author_id = NULL, target_title_snapshot = NULL, "
                        + "target_content_snapshot = NULL WHERE id = ?",
                reportId);
        postRepository.delete(post);

        List<ReportViewDto> pending = reportService.findPending(PageRequest.of(0, 20)).getContent();

        assertThat(pending).singleElement().satisfies(view -> {
            assertThat(view.targetPreview()).isNull();
            assertThat(view.targetAuthor()).isNull();
            assertThat(view.targetDeleted()).isFalse();
        });
    }
}
