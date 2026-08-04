package com.kraft.community.report;

import com.kraft.community.auth.CommunityPrincipal;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/community/reports")
public class CommunityReportController {

    private final CommunityReportService communityReportService;

    public CommunityReportController(CommunityReportService communityReportService) {
        this.communityReportService = communityReportService;
    }

    @PostMapping
    @ApiResponse(responseCode = "201")
    public ResponseEntity<Void> report(
            @AuthenticationPrincipal CommunityPrincipal principal,
            @Valid @RequestBody CreateReportRequest request) {
        communityReportService.report(principal.getUserId(), request);
        return ResponseEntity.status(201).build();
    }
}
