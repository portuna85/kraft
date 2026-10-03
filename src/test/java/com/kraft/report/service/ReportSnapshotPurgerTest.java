package com.kraft.report.service;

import com.kraft.report.domain.Report;
import com.kraft.report.domain.ReportReason;
import com.kraft.report.domain.ReportRepository;
import com.kraft.report.domain.ReportTargetType;
import com.kraft.user.domain.Role;
import com.kraft.user.domain.User;
import com.kraft.user.domain.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 처리 끝난 신고의 스냅샷(다른 사람의 글·댓글 내용)을 보관기간 뒤 비우는 스케줄러가 정확히
 * 비우는지 확인한다(BE-10). 데이터를 지우는 작업인데 전용 테스트가 없었다 — 대기 중 신고의
 * 유일한 근거를 잘못 지우면 되돌릴 수 없으므로, 지우면 안 되는 경우를 특히 고정한다.
 * <p>
 * 한 번에 비우는 건수(batch-size)를 2로 줄여, 여러 묶음에 걸쳐 끝까지 처리하는지도 본다.
 */
@SpringBootTest(properties = {
        "app.report.snapshot-retention-days=90",
        "app.report.snapshot-purge-batch-size=2"
})
class ReportSnapshotPurgerTest {

    @Autowired
    private ReportSnapshotPurger purger;

    @Autowired
    private ReportRepository reportRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User reporter;
    private User author;
    private long nextTargetId;

    @BeforeEach
    void setUp() {
        reportRepository.deleteAll();
        userRepository.deleteAll();
        reporter = userRepository.save(User.builder()
                .name("reporter").email("reporter@example.com").password("encoded").role(Role.USER).build());
        author = userRepository.save(User.builder()
                .name("author").email("author@example.com").password("encoded").role(Role.USER).build());
        nextTargetId = 1L;
    }

    private Report saveReport() {
        return reportRepository.save(Report.builder()
                .reporter(reporter)
                .targetType(ReportTargetType.POST)
                .targetId(nextTargetId++)
                .reason(ReportReason.SPAM)
                .targetAuthor(author)
                .targetTitleSnapshot("제목 스냅샷")
                .targetContentSnapshot("본문 스냅샷")
                .build());
    }

    /** 처리 완료 상태와 처리 시각을 직접 심는다 — handledAt은 처리하는 순간의 현재 시각으로만 정해진다. */
    private Report handledDaysAgo(int days, String status) {
        Report report = saveReport();
        jdbcTemplate.update("UPDATE reports SET status = ?, handled_at = ? WHERE id = ?",
                status, LocalDateTime.now().minusDays(days), report.getId());
        return report;
    }

    private Report reload(Report report) {
        return reportRepository.findById(report.getId()).orElseThrow();
    }

    @Test
    @DisplayName("보관기간(90일)이 지난 처리 완료 신고의 제목·본문 스냅샷을 비운다")
    void clearsSnapshotOfHandledReportsOlderThanRetention() {
        Report rejected = handledDaysAgo(100, "REJECTED");
        Report resolved = handledDaysAgo(120, "RESOLVED");
        Report targetDeleted = handledDaysAgo(95, "TARGET_DELETED");

        purger.purgeOldSnapshots();

        for (Report r : List.of(rejected, resolved, targetDeleted)) {
            Report after = reload(r);
            assertThat(after.getTargetTitleSnapshot()).isNull();
            assertThat(after.getTargetContentSnapshot()).isNull();
            // 감사 기록(누구를 정지했는지)은 남긴다.
            assertThat(after.getTargetAuthor()).isNotNull();
        }
    }

    @Test
    @DisplayName("보관기간 안인 처리 완료 신고의 스냅샷은 그대로 둔다")
    void keepsSnapshotWithinRetention() {
        Report recent = handledDaysAgo(10, "RESOLVED");

        purger.purgeOldSnapshots();

        assertThat(reload(recent).getTargetContentSnapshot()).isEqualTo("본문 스냅샷");
        assertThat(reload(recent).getTargetTitleSnapshot()).isEqualTo("제목 스냅샷");
    }

    @Test
    @DisplayName("대기 중(PENDING) 신고의 스냅샷은 아무리 오래돼도 지우지 않는다")
    void neverClearsPendingReports() {
        Report pending = saveReport();
        jdbcTemplate.update("UPDATE reports SET created_at = ? WHERE id = ?",
                LocalDateTime.now().minusDays(400), pending.getId());

        purger.purgeOldSnapshots();

        assertThat(reload(pending).getTargetContentSnapshot()).isEqualTo("본문 스냅샷");
    }

    @Test
    @DisplayName("대상이 한 번에 처리하는 묶음 크기보다 많아도 끝까지 비운다")
    void clearsEverythingAcrossMultipleBatches() {
        List<Report> old = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            old.add(handledDaysAgo(100 + i, "REJECTED"));
        }
        Report recent = handledDaysAgo(1, "REJECTED");

        purger.purgeOldSnapshots();

        assertThat(old).allSatisfy(r -> assertThat(reload(r).getTargetContentSnapshot()).isNull());
        assertThat(reload(recent).getTargetContentSnapshot()).isEqualTo("본문 스냅샷");
    }

    @Test
    @DisplayName("이미 비워진 신고만 있으면 아무 일도 하지 않고 다시 실행해도 안전하다")
    void isIdempotent() {
        Report old = handledDaysAgo(100, "REJECTED");

        purger.purgeOldSnapshots();
        purger.purgeOldSnapshots();

        assertThat(reload(old).getTargetContentSnapshot()).isNull();
    }
}
