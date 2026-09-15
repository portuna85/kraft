package com.kraft.report.web;

import com.kraft.report.dto.ReportViewDto;
import com.kraft.report.service.ReportService;
import com.kraft.shared.web.PageWindow;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 관리자용 신고 목록 화면. 경로가 {@code /admin} 아래라 {@code SecurityConfig}가 관리자만
 * 들여보낸다 — 이 컨트롤러는 권한을 다시 판정하지 않는다.
 */
@RequiredArgsConstructor
@Controller
public class AdminReportPageController {

    private final ReportService reportService;

    @GetMapping("/admin/reports")
    public String reports(@PageableDefault(size = 20) Pageable pageable, Model model) {
        Page<ReportViewDto> reports = reportService.findPending(pageable);
        model.addAttribute("reports", reports.getContent());
        model.addAttribute("reportsPage", reports);
        model.addAttribute("pageWindow", PageWindow.of(reports.getNumber(), reports.getTotalPages()));
        model.addAttribute("pendingCount", reports.getTotalElements());
        model.addAttribute("pageTitle", "신고 처리");
        return "admin/reports";
    }
}
