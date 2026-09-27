package com.kraft.report.dto;

import java.time.LocalDateTime;

/**
 * 관리자 화면의 신고 한 줄.
 * <p>
 * {@code targetPreview}는 대상이 지금 살아 있으면 그 실시간 내용이고, 이미 사라졌으면(작성자가
 * 스스로 지웠거나 다른 경로로 먼저 지워졌으면) 접수 시점 스냅샷으로 물러선다(A-SEC-07). 스냅샷도
 * 없으면(V27 이전 신고) null이며, 화면은 "대상이 이미 삭제되었습니다"로 다뤄야 한다.
 * {@code targetDeleted}가 true면 지금 보여주는 값이 실시간이 아니라 스냅샷이라는 뜻이다 —
 * 화면은 이 경우 대상으로의 링크를 만들지 않는다.
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
        LocalDateTime createdAt,
        boolean targetDeleted
) {
}
