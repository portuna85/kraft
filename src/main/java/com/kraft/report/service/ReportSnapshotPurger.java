package com.kraft.report.service;

import com.kraft.report.domain.ReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 처리 완료된 신고의 대상 스냅샷(제목·본문, A-SEC-07)을 일정 기간이 지나면 비운다.
 * <p>
 * 스냅샷은 대상이 사라져도 관리자가 판단할 근거로 남기려고 저장하지만, 그 자체로 다른 사람의
 * 글·댓글 내용이라는 개인정보다. 처리(관리자 판단 또는 본인 삭제)가 끝난 뒤에도 무기한
 * 남겨 둘 이유는 없다 — {@code targetAuthor}(FK)만 감사 기록으로 남기고 본문·제목은 지운다.
 * <p>
 * {@code PENDING} 신고의 스냅샷은 절대 건드리지 않는다({@code ReportRepository.findHandledWithSnapshotOlderThan}이
 * 이미 처리 완료만 조회한다) — 아직 판단이 끝나지 않은 신고의 유일한 근거를 지워서는 안 된다.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class ReportSnapshotPurger {

    private final ReportRepository reportRepository;

    @Value("${app.report.snapshot-retention-days:90}")
    private int retentionDays;

    @Value("${app.report.snapshot-purge-enabled:true}")
    private boolean enabled;

    /** 한 번에 비우는 신고 수. 묶음마다 따로 커밋해 락·메모리를 짧게 쓴다(BE-10). */
    @Value("${app.report.snapshot-purge-batch-size:500}")
    private int batchSize;

    @Scheduled(initialDelayString = "${app.report.snapshot-purge-initial-delay-ms:1800000}",
            fixedDelayString = "${app.report.snapshot-purge-interval-ms:86400000}")
    public void purgeOldSnapshots() {
        if (!enabled) {
            return;
        }

        LocalDateTime threshold = LocalDateTime.now().minus(Duration.ofDays(retentionDays));
        // 전체를 엔티티로 올려 한 트랜잭션에서 고치는 대신, id를 묶음 단위로 읽어 한 문장으로
        // 비운다. 비운 행은 다음 조회 조건(IS NOT NULL)에서 빠지므로 항상 앞으로 나아간다.
        int cleared = 0;
        while (true) {
            List<Long> ids = reportRepository.findHandledIdsWithSnapshotOlderThan(
                    threshold, PageRequest.of(0, batchSize));
            if (ids.isEmpty()) {
                break;
            }
            int updated = reportRepository.clearSnapshotsByIdIn(ids);
            if (updated == 0) {
                break;
            }
            cleared += updated;
        }
        if (cleared > 0) {
            log.info("보관 기한이 지난 신고 스냅샷 {}건을 비웠습니다.", cleared);
        }
    }
}
