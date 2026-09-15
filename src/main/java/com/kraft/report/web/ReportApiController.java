package com.kraft.report.web;

import com.kraft.report.dto.ReportSaveRequestDto;
import com.kraft.report.service.ReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
public class ReportApiController {

    private final ReportService reportService;

    /** 신고 접수. 로그인만 하면 할 수 있다 — 이메일 인증 전이라도 문제를 알릴 수는 있어야 한다. */
    @PostMapping("/api/v1/reports")
    public Long report(@Valid @RequestBody ReportSaveRequestDto requestDto, Authentication authentication) {
        return reportService.report(requestDto, authentication);
    }

    /** 신고를 받아들여 대상을 지운다. 관리자 전용(SecurityConfig의 /api/v1/admin/**). */
    @PostMapping("/api/v1/admin/reports/{id}/resolve")
    public ResponseEntity<Void> resolve(@PathVariable Long id, Authentication authentication) {
        reportService.resolve(id, authentication);
        return ResponseEntity.noContent().build();
    }

    /** 문제가 없다고 판단해 신고만 닫는다. 관리자 전용. */
    @PostMapping("/api/v1/admin/reports/{id}/reject")
    public ResponseEntity<Void> reject(@PathVariable Long id, Authentication authentication) {
        reportService.reject(id, authentication);
        return ResponseEntity.noContent().build();
    }
}
