package com.kraft.report.service;

import com.kraft.shared.exception.BusinessValidationException;
import com.kraft.comment.domain.Comment;
import com.kraft.comment.domain.CommentRepository;
import com.kraft.comment.service.CommentService;
import com.kraft.post.domain.Post;
import com.kraft.post.domain.PostRepository;
import com.kraft.post.service.PostService;
import com.kraft.report.domain.Report;
import com.kraft.report.domain.ReportRepository;
import com.kraft.report.domain.ReportStatus;
import com.kraft.report.domain.ReportTargetType;
import com.kraft.report.dto.ReportSaveRequestDto;
import com.kraft.report.dto.ReportViewDto;
import com.kraft.report.event.TargetDeletedEvent;
import com.kraft.shared.exception.NotFoundException;
import com.kraft.shared.security.CurrentUser;
import com.kraft.user.domain.User;
import com.kraft.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 신고 접수와 처리.
 *
 * <h3>왜 대상을 지우는 쪽을 다시 만들지 않는가</h3>
 * 처리(삭제)는 {@link PostService}·{@link CommentService}의 기존 삭제를 그대로 부른다. 그쪽은
 * 이미 소유권 검사(관리자 통과), 이미지 정리 예약, 커밋 후 파일 정리까지 맡고 있다. 여기서
 * 저장소를 직접 지우면 그 뒷정리가 통째로 빠진다.
 *
 * <h3>한 대상에 쌓인 신고</h3>
 * 인기 있는 스팸 글은 여러 사람이 신고한다. 하나를 처리하면 같은 대상의 나머지 대기 신고도
 * 함께 정리한다 — 이미 지운 글이 목록에 계속 남아 있으면 관리자가 같은 판단을 반복하게 된다.
 */
@Slf4j
@RequiredArgsConstructor
@Service
@Transactional(readOnly = true)
public class ReportService {

    /** 관리자 목록에 보여줄 대상 내용의 길이. 판단에 필요한 만큼만 보여준다. */
    private static final int PREVIEW_LENGTH = 80;

    /**
     * 신고 접수 시점에 저장해 두는 스냅샷의 길이(A-SEC-07). 목록 미리보기(PREVIEW_LENGTH)보다
     * 넉넉하다 — 이건 나중에 관리자가 판단할 유일한 근거로 남을 수 있어, 짧게 자르면 그 근거
     * 자체가 부실해진다. {@code reports.target_content_snapshot} 컬럼 길이와 맞춘다.
     */
    private static final int SNAPSHOT_LENGTH = 500;

    /** O05: 이보다 큰 정지 기간은 날짜·DB 범위 오류로 이어질 수 있어 입력 단계에서 막는다. */
    private static final int MAX_SUSPEND_DAYS = 3650;

    private final ReportRepository reportRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final PostService postService;
    private final CommentService commentService;

    /**
     * 신고를 접수한다. 대상이 없거나, 자기 글이거나, 이미 신고한 대상이면 거절한다.
     * <p>
     * 자기 글을 막는 이유는 지우고 싶으면 직접 지우면 되기 때문이다 — 관리자의 목록만 길어진다.
     */
    @Transactional
    public Long report(ReportSaveRequestDto requestDto, Authentication authentication) {
        User reporter = findUser(authentication);
        TargetSnapshot snapshot = snapshotOf(requestDto.targetType(), requestDto.targetId())
                .orElseThrow(() -> new NotFoundException("이미 삭제되었거나 존재하지 않는 대상입니다."));

        if (snapshot.author().getId().equals(reporter.getId())) {
            throw new BusinessValidationException("자신이 쓴 글은 신고할 수 없습니다. 직접 삭제할 수 있습니다.");
        }
        if (reportRepository.existsByReporterIdAndTargetTypeAndTargetId(
                reporter.getId(), requestDto.targetType(), requestDto.targetId())) {
            throw new BusinessValidationException("이미 신고한 대상입니다. 관리자가 확인하고 있습니다.");
        }

        // 접수 시점의 작성자·제목·본문을 함께 저장한다(A-SEC-07) — 작성자가 처리 전에 스스로
        // 지우면 target_id로는 더 이상 아무것도 조회할 수 없다. 이 스냅샷이 그때 남는 유일한
        // 근거다. resolve()는 실시간 조회가 가능하면 그쪽을 우선하고, 이 값은 fallback으로만
        // 쓴다.
        Report saved = reportRepository.save(Report.builder()
                .reporter(reporter)
                .targetType(requestDto.targetType())
                .targetId(requestDto.targetId())
                .reason(requestDto.reason())
                .detail(requestDto.detail())
                .targetAuthor(snapshot.author())
                .targetTitleSnapshot(snapshot.title())
                .targetContentSnapshot(snapshot.content())
                .build());

        log.info("신고가 접수되었습니다. reportId={}, targetType={}, targetId={}, reason={}",
                saved.getId(), requestDto.targetType(), requestDto.targetId(), requestDto.reason());
        return saved.getId();
    }

    /**
     * 관리자 화면의 대기 목록. 오래된 신고부터 본다.
     * <p>
     * 예전에는 행마다 대상(게시글/댓글)을 따로 조회했다(개선 보고서 "신고 목록의 대상별
     * 조회") — 페이지 하나(최대 20건)에 최대 20번의 추가 조회가 났다. 페이지를 가져온 뒤
     * 대상 종류별로 id를 모아 한 번씩만 배치 조회하고, 그 결과를 메모리에서 매핑한다.
     */
    public Page<ReportViewDto> findPending(Pageable pageable) {
        Page<Report> page = reportRepository.findByStatusOrderByIdAsc(ReportStatus.PENDING, pageable);

        List<Long> postIds = page.getContent().stream()
                .filter(r -> r.getTargetType() == ReportTargetType.POST)
                .map(Report::getTargetId)
                .toList();
        List<Long> commentIds = page.getContent().stream()
                .filter(r -> r.getTargetType() == ReportTargetType.COMMENT)
                .map(Report::getTargetId)
                .toList();

        Map<Long, Post> posts = postIds.isEmpty() ? Map.of()
                : postRepository.findAllByIdInWithUser(postIds).stream()
                        .collect(Collectors.toMap(Post::getId, Function.identity()));
        Map<Long, Comment> comments = commentIds.isEmpty() ? Map.of()
                : commentRepository.findAllByIdInWithUser(commentIds).stream()
                        .collect(Collectors.toMap(Comment::getId, Function.identity()));

        return page.map(report -> toView(report, posts, comments));
    }

    public long countPending() {
        return reportRepository.countByStatus(ReportStatus.PENDING);
    }

    /** 대상만 지우고 작성자는 건드리지 않는다. */
    @Transactional
    public void resolve(Long id, Authentication authentication) {
        resolve(id, authentication, 0);
    }

    /**
     * 신고를 받아들여 대상을 지우고, 필요하면 작성자를 그만큼 정지한다.
     * <p>
     * 지우기만 해서는 반복하는 사람을 막지 못한다 — 같은 사람이 곧바로 다시 쓸 수 있기 때문이다.
     * 정지는 작성만 막고 읽기·로그인은 그대로 둔다. 기간이 지나면 저절로 풀린다.
     * <p>
     * 작성자를 <b>지우기 전에</b> 찾아 둔다. 대상을 먼저 지우면 누구를 정지해야 할지 알 수 없다.
     * 대상이 이미 없으면 지우는 단계만 건너뛰고 기록은 남긴다 — 관리자가 다른 경로로 먼저
     * 지웠을 수 있다.
     *
     * @param suspendDays 0이면 정지하지 않는다. 음수이거나 {@link #MAX_SUSPEND_DAYS}를
     *                    넘으면 거절한다(O05) — 음수를 조용히 건너뛰면 관리자가 잘못된 입력을
     *                    성공으로 오해하고, 지나치게 큰 값은 날짜 계산에서 예상 밖의 결과를 낸다.
     */
    @Transactional
    public void resolve(Long id, Authentication authentication, int suspendDays) {
        if (suspendDays < 0 || suspendDays > MAX_SUSPEND_DAYS) {
            throw new BusinessValidationException(
                    "정지 기간은 0에서 %d일 사이여야 합니다: %d".formatted(MAX_SUSPEND_DAYS, suspendDays));
        }
        Report report = findPendingReport(id);
        User admin = findUser(authentication);
        // 한 번만 조회해 아래 삭제 여부 판정에도 재사용한다(BE-16) — 예전에는 여기와
        // deleteTarget() 안에서 같은 대상을 각각 한 번씩, 총 두 번 조회했다.
        Optional<User> targetAuthor = targetAuthorOf(report.getTargetType(), report.getTargetId());
        // 대상을 지우기 전에 그 아래 딸린 댓글 id를 먼저 기록해 둔다(BE-16) — 지운 뒤에는
        // 조회할 수 없다. 게시글이면 그 댓글 전체, 최상위 댓글이면 그 답글이 함께 지워진다.
        List<Long> cascadedCommentIds = cascadedCommentIdsFor(report);

        if (targetAuthor.isPresent()) {
            // 관리자 권한으로 기존 삭제 경로를 그대로 탄다(이미지 정리·소유권 검사 포함).
            if (report.getTargetType() == ReportTargetType.POST) {
                postService.delete(report.getTargetId(), authentication);
            } else {
                commentService.delete(report.getTargetId(), authentication);
            }
        }
        if (suspendDays > 0) {
            // 실시간 조회가 안 되면(작성자가 스스로 지웠음) 접수 시점 스냅샷의 작성자로
            // 물러선다(A-SEC-07) — 그러지 않으면 "쓰고 → 신고되면 지우고 → 다시 쓰기"를
            // 반복해도 제재할 수 없었다.
            User authorForSuspension = targetAuthor.orElse(report.getTargetAuthor());
            if (authorForSuspension != null) {
                suspend(authorForSuspension, report, suspendDays);
            }
        }
        report.resolve(admin);
        resolveOthersOnSameTarget(report, admin);
        resolveCascadedReports(cascadedCommentIds, admin);

        log.info("신고를 처리했습니다(대상 삭제{}). reportId={}, targetType={}, targetId={}",
                suspendDays > 0 ? ", 작성자 " + suspendDays + "일 정지" : "",
                report.getId(), report.getTargetType(), report.getTargetId());
    }

    private List<Long> cascadedCommentIdsFor(Report report) {
        if (report.getTargetType() == ReportTargetType.POST) {
            return commentRepository.findIdsByPostId(report.getTargetId());
        }
        return commentRepository.findIdsByParentId(report.getTargetId());
    }

    /** 대상 삭제로 함께 사라진 댓글·답글에 걸린 대기 신고를 마저 닫는다(BE-16). */
    private void resolveCascadedReports(List<Long> cascadedCommentIds, User admin) {
        if (cascadedCommentIds.isEmpty()) {
            return;
        }
        reportRepository.findByTargetTypeAndTargetIdInAndStatus(
                        ReportTargetType.COMMENT, cascadedCommentIds, ReportStatus.PENDING)
                .forEach(report -> report.resolve(admin));
    }

    private void suspend(User author, Report report, int days) {
        author.suspendUntil(LocalDateTime.now().plusDays(days),
                "%s 신고 처리(%d일)".formatted(report.getReason().getTitle(), days));
        log.info("작성자를 정지했습니다. userId={}, days={}, reportId={}", author.getId(), days, report.getId());
    }

    /**
     * 관리자가 판단하기 전에 작성자 본인이 대상을 지워 사라진 신고를 닫는다(A-BE-01).
     * {@code PostService.delete}·{@code CommentService.delete}가 실제로 행을 지웠을 때만
     * {@link #onTargetDeleted}를 통해 호출된다 — 관리자가 신고를 처리하며 지운 경우는 이미
     * {@link #resolve(Long, Authentication, int)}가 그 자리에서 관련 신고를 {@code RESOLVED}로
     * 직접 처리하므로, 이 메서드가 실행되는 시점(커밋 직전)에는 더 이상 {@code PENDING}이
     * 아니라 걸리지 않는다 — 그래서 admin 삭제와 본인 삭제가 서로 다른 상태로 남는다.
     */
    @Transactional
    public void closeForDeletedTargets(ReportTargetType targetType, List<Long> targetIds) {
        if (targetIds.isEmpty()) {
            return;
        }
        List<Report> pending = reportRepository.findByTargetTypeAndTargetIdInAndStatus(
                targetType, targetIds, ReportStatus.PENDING);
        if (pending.isEmpty()) {
            return;
        }
        pending.forEach(Report::closeAsTargetDeleted);
        log.info("본인 삭제로 사라진 대상의 신고 {}건을 닫았습니다. targetType={}", pending.size(), targetType);
    }

    /**
     * {@link TargetDeletedEvent}를 받는다. {@code BEFORE_COMMIT}이라 대상을 지운 트랜잭션과
     * 같은 트랜잭션 안에서 함께 커밋되거나 함께 롤백된다 — 순환 의존을 피하려고 직접 호출
     * 대신 이벤트로 받는 이유는 {@link TargetDeletedEvent}의 클래스 문서 참고.
     */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onTargetDeleted(TargetDeletedEvent event) {
        closeForDeletedTargets(event.targetType(), event.targetIds());
    }

    /** 문제가 없다고 판단한다. 대상은 그대로 두고 이 신고만 닫는다. */
    @Transactional
    public void reject(Long id, Authentication authentication) {
        Report report = findPendingReport(id);
        report.reject(findUser(authentication));

        log.info("신고를 반려했습니다. reportId={}", report.getId());
    }

    private void resolveOthersOnSameTarget(Report handled, User admin) {
        reportRepository.findByTargetTypeAndTargetIdAndStatus(
                        handled.getTargetType(), handled.getTargetId(), ReportStatus.PENDING)
                .stream()
                .filter(other -> !other.getId().equals(handled.getId()))
                .forEach(other -> other.resolve(admin));
    }

    private Report findPendingReport(Long id) {
        Report report = reportRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("존재하지 않는 신고입니다. id=" + id));
        if (!report.isPending()) {
            throw new BusinessValidationException("이미 처리된 신고입니다. id=" + id);
        }
        return report;
    }

    private Optional<User> targetAuthorOf(ReportTargetType targetType, Long targetId) {
        if (targetType == ReportTargetType.POST) {
            return postRepository.findById(targetId).filter(post -> !post.isDeleted()).map(Post::getUser);
        }
        return commentRepository.findById(targetId).map(Comment::getUser);
    }

    /** 신고 접수 시점에 저장할 스냅샷(A-SEC-07). 댓글은 제목이 없어 {@code title}이 항상 null이다. */
    private record TargetSnapshot(User author, String title, String content) {
    }

    private Optional<TargetSnapshot> snapshotOf(ReportTargetType targetType, Long targetId) {
        if (targetType == ReportTargetType.POST) {
            // 소프트 삭제된 글은 없는 대상으로 본다 — 이미 목록에서 사라졌고 신고할 수 없다.
            return postRepository.findById(targetId)
                    .filter(post -> !post.isDeleted())
                    .map(post -> new TargetSnapshot(post.getUser(), post.getTitle(), shorten(post.getContent(), SNAPSHOT_LENGTH)));
        }
        return commentRepository.findById(targetId)
                .map(comment -> new TargetSnapshot(comment.getUser(), null, shorten(comment.getContent(), SNAPSHOT_LENGTH)));
    }

    private ReportViewDto toView(Report report, Map<Long, Post> posts, Map<Long, Comment> comments) {
        String preview = null;
        String author = null;
        boolean targetExists;

        if (report.getTargetType() == ReportTargetType.POST) {
            Post post = posts.get(report.getTargetId());
            targetExists = post != null;
            if (post != null) {
                preview = shorten(post.getTitle());
                author = post.getUser().getName();
            }
        } else {
            Comment comment = comments.get(report.getTargetId());
            targetExists = comment != null;
            if (comment != null) {
                preview = shorten(comment.getContent());
                author = comment.getUser().getName();
            }
        }

        // 대상이 사라졌으면 접수 시점 스냅샷으로 물러선다(A-SEC-07) — 관리자가 무엇이
        // 문제였는지 판단할 유일한 근거다. V27 이전에 접수된 신고는 스냅샷이 없어 그대로
        // "대상이 이미 삭제되었습니다"로 남는다.
        if (!targetExists && report.getTargetContentSnapshot() != null) {
            String snapshotText = report.getTargetType() == ReportTargetType.POST
                    ? report.getTargetTitleSnapshot()
                    : report.getTargetContentSnapshot();
            preview = shorten(snapshotText);
            author = report.getTargetAuthor() != null ? report.getTargetAuthor().getName() : null;
        }

        return new ReportViewDto(
                report.getId(),
                report.getTargetType().name(),
                report.getTargetType().getTitle(),
                report.getTargetId(),
                preview,
                author,
                report.getReason().getTitle(),
                report.getDetail(),
                report.getReporter().getName(),
                report.getCreatedAt(),
                !targetExists && preview != null);
    }

    private static String shorten(String text) {
        return shorten(text, PREVIEW_LENGTH);
    }

    private static String shorten(String text, int maxLength) {
        if (text == null) {
            return null;
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "…";
    }

    private User findUser(Authentication authentication) {
        return CurrentUser.require(authentication, userRepository);
    }
}
