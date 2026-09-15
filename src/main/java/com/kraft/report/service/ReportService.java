package com.kraft.report.service;

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
import com.kraft.user.domain.EmailHasher;
import com.kraft.user.domain.EmailMasker;
import com.kraft.user.domain.User;
import com.kraft.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

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
        User reporter = findUser(authentication.getName());
        User targetAuthor = targetAuthorOf(requestDto.targetType(), requestDto.targetId())
                .orElseThrow(() -> new IllegalArgumentException("이미 삭제되었거나 존재하지 않는 대상입니다."));

        if (targetAuthor.getId().equals(reporter.getId())) {
            throw new IllegalArgumentException("자신이 쓴 글은 신고할 수 없습니다. 직접 삭제할 수 있습니다.");
        }
        if (reportRepository.existsByReporterIdAndTargetTypeAndTargetId(
                reporter.getId(), requestDto.targetType(), requestDto.targetId())) {
            throw new IllegalArgumentException("이미 신고한 대상입니다. 관리자가 확인하고 있습니다.");
        }

        Report saved = reportRepository.save(Report.builder()
                .reporter(reporter)
                .targetType(requestDto.targetType())
                .targetId(requestDto.targetId())
                .reason(requestDto.reason())
                .detail(requestDto.detail())
                .build());

        log.info("신고가 접수되었습니다. reportId={}, targetType={}, targetId={}, reason={}",
                saved.getId(), requestDto.targetType(), requestDto.targetId(), requestDto.reason());
        return saved.getId();
    }

    /** 관리자 화면의 대기 목록. 오래된 신고부터 본다. */
    public Page<ReportViewDto> findPending(Pageable pageable) {
        return reportRepository.findByStatusOrderByIdAsc(ReportStatus.PENDING, pageable)
                .map(this::toView);
    }

    public long countPending() {
        return reportRepository.countByStatus(ReportStatus.PENDING);
    }

    /**
     * 신고를 받아들여 대상을 지운다. 대상이 이미 없으면 지우는 단계만 건너뛰고 기록은 남긴다 —
     * 관리자가 다른 경로로 먼저 지웠을 수 있다.
     */
    @Transactional
    public void resolve(Long id, Authentication authentication) {
        Report report = findPendingReport(id);
        User admin = findUser(authentication.getName());

        deleteTarget(report, authentication);
        report.resolve(admin);
        resolveOthersOnSameTarget(report, admin);

        log.info("신고를 처리했습니다(대상 삭제). reportId={}, targetType={}, targetId={}",
                report.getId(), report.getTargetType(), report.getTargetId());
    }

    /** 문제가 없다고 판단한다. 대상은 그대로 두고 이 신고만 닫는다. */
    @Transactional
    public void reject(Long id, Authentication authentication) {
        Report report = findPendingReport(id);
        report.reject(findUser(authentication.getName()));

        log.info("신고를 반려했습니다. reportId={}", report.getId());
    }

    private void deleteTarget(Report report, Authentication authentication) {
        if (targetAuthorOf(report.getTargetType(), report.getTargetId()).isEmpty()) {
            return;
        }
        // 관리자 권한으로 기존 삭제 경로를 그대로 탄다(이미지 정리·소유권 검사 포함).
        if (report.getTargetType() == ReportTargetType.POST) {
            postService.delete(report.getTargetId(), authentication);
        } else {
            commentService.delete(report.getTargetId(), authentication);
        }
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
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 신고입니다. id=" + id));
        if (!report.isPending()) {
            throw new IllegalArgumentException("이미 처리된 신고입니다. id=" + id);
        }
        return report;
    }

    private Optional<User> targetAuthorOf(ReportTargetType targetType, Long targetId) {
        if (targetType == ReportTargetType.POST) {
            return postRepository.findById(targetId).map(Post::getUser);
        }
        return commentRepository.findById(targetId).map(Comment::getUser);
    }

    private ReportViewDto toView(Report report) {
        String preview = null;
        String author = null;

        if (report.getTargetType() == ReportTargetType.POST) {
            Optional<Post> post = postRepository.findById(report.getTargetId());
            preview = post.map(p -> shorten(p.getTitle())).orElse(null);
            author = post.map(p -> p.getUser().getName()).orElse(null);
        } else {
            Optional<Comment> comment = commentRepository.findById(report.getTargetId());
            preview = comment.map(c -> shorten(c.getContent())).orElse(null);
            author = comment.map(c -> c.getUser().getName()).orElse(null);
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
                report.getCreatedAt());
    }

    private static String shorten(String text) {
        if (text == null) {
            return null;
        }
        return text.length() <= PREVIEW_LENGTH ? text : text.substring(0, PREVIEW_LENGTH) + "…";
    }

    private User findUser(String email) {
        return userRepository.findByEmailHash(EmailHasher.sha512Hex(email))
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다. email=" + EmailMasker.mask(email)));
    }
}
