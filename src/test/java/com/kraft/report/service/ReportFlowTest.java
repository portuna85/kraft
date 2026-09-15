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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

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
        return new UsernamePasswordAuthenticationToken(user.getEmail(), "n/a",
                List.of(new SimpleGrantedAuthority(user.getRoleKey())));
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
    @DisplayName("처리하면 대상 글이 사라지고 대기 목록에서도 빠진다")
    void resolvingDeletesTargetAndClearsPendingList() {
        reportThePost(reporter, ReportReason.ABUSE);
        Long reportId = reportRepository.findAll().get(0).getId();

        reportService.resolve(reportId, authOf(admin));

        assertThat(postRepository.findById(post.getId())).isEmpty();
        assertThat(reportService.findPending(PageRequest.of(0, 20)).getContent()).isEmpty();
        assertThat(reportRepository.findById(reportId).orElseThrow().getStatus())
                .isEqualTo(ReportStatus.RESOLVED);
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

    @Test
    @DisplayName("대상이 이미 지워진 신고도 목록에 남고, 미리보기는 비어 있다")
    void reportForDeletedTargetStillListsWithoutPreview() {
        reportThePost(reporter, ReportReason.SEXUAL);
        postRepository.delete(post);

        List<ReportViewDto> pending = reportService.findPending(PageRequest.of(0, 20)).getContent();

        assertThat(pending).singleElement().satisfies(view -> {
            assertThat(view.targetPreview()).isNull();
            assertThat(view.targetAuthor()).isNull();
        });
    }
}
