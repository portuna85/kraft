package com.kraft.report.dto;

import com.kraft.report.domain.ReportReason;
import com.kraft.report.domain.ReportTargetType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 신고 접수 요청. 사유는 고르게 하고 사정은 선택으로 받는다 — 자유 입력만 받으면 관리자가
 * 무엇부터 볼지 판단할 수 없다.
 */
public record ReportSaveRequestDto(

        @NotNull(message = "신고 대상이 올바르지 않습니다.")
        ReportTargetType targetType,

        @NotNull(message = "신고 대상이 올바르지 않습니다.")
        Long targetId,

        @NotNull(message = "신고 사유는 필수입니다.")
        ReportReason reason,

        @Size(max = 500, message = "자세한 내용은 500자 이하로 입력하세요.")
        String detail
) {
}
