package com.kraft.report.web;

import com.kraft.report.dto.ReportSaveRequestDto;
import com.kraft.report.service.ReportService;
import com.kraft.shared.web.RateLimitResponses;
import com.kraft.shared.web.WriteRateLimiters;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
public class ReportApiController {

    private final ReportService reportService;
    private final WriteRateLimiters rateLimiters;

    /**
     * 신고 접수. 로그인만 하면 할 수 있다 — 이메일 인증 전이라도 문제를 알릴 수는 있어야 한다.
     * 속도 제한(전체 리뷰 2026-09-26 A-SEC-06)은 관리자를 제외한다 — 신고 처리는 관리자
     * 전용 다른 API({@code resolve}·{@code reject})라 이 제한과는 무관하지만, 방어적으로
     * {@code WriteRateLimiters}의 공통 규칙(관리자 제외)을 그대로 따른다.
     */
    @PostMapping("/api/v1/reports")
    public ResponseEntity<?> report(@Valid @RequestBody ReportSaveRequestDto requestDto, Authentication authentication) {
        if (!rateLimiters.tryAcquireReport(authentication)) {
            return RateLimitResponses.tooManyRequests("REPORT_RATE_LIMITED", 60);
        }
        return ResponseEntity.ok(reportService.report(requestDto, authentication));
    }

    /**
     * 신고를 받아들여 대상을 지운다. 관리자 전용(SecurityConfig의 /api/v1/admin/**).
     *
     * @param suspendDays 0보다 크면 작성자를 그만큼 정지한다. 지우기만 해서는 반복하는 사람을
     *                    막지 못한다.
     */
    @PostMapping("/api/v1/admin/reports/{id}/resolve")
    public ResponseEntity<Void> resolve(@PathVariable Long id,
                                         @RequestParam(defaultValue = "0") int suspendDays,
                                         Authentication authentication) {
        reportService.resolve(id, authentication, suspendDays);
        return ResponseEntity.noContent().build();
    }

    /** 문제가 없다고 판단해 신고만 닫는다. 관리자 전용. */
    @PostMapping("/api/v1/admin/reports/{id}/reject")
    public ResponseEntity<Void> reject(@PathVariable Long id, Authentication authentication) {
        reportService.reject(id, authentication);
        return ResponseEntity.noContent().build();
    }
}
