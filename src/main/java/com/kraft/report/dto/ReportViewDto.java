package com.kraft.report.dto;

import java.time.LocalDateTime;

/**
 * 관리자 화면의 신고 한 줄.
 * <p>
 * {@code targetPreview}는 신고 대상의 현재 내용이다. 이미 지워졌으면 null이며, 화면은 그것을
 * 정상으로 다뤄야 한다 — 관리자가 다른 경로로 먼저 지웠거나, 같은 대상의 다른 신고를 처리한
 * 뒤일 수 있다.
 */
public record ReportViewDto(
        Long id,
        String targetType,
        String targetTypeTitle,
        Long targetId,
        String targetPreview,
        String targetAuthor,
        String reasonTitle,
        String detail,
        String reporter,
        LocalDateTime createdAt
) {
}
