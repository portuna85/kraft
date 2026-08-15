package com.kraft.ops;

import com.kraft.common.web.PageResponse;
import com.kraft.operationlog.WinningNumberOperationLogResponse;
import com.kraft.winningnumber.WinningNumberResponse;
import com.kraft.winningnumber.WinningNumberUpsertRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ops")
public class OpsController {

    private final OpsService opsService;

    public OpsController(OpsService opsService) {
        this.opsService = opsService;
    }

    @GetMapping("/summary")
    public OpsSummaryResponse summary() {
        return opsService.getSummary();
    }

    @GetMapping("/logs")
    public PageResponse<WinningNumberOperationLogResponse> logs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String operationType,
            @RequestParam(required = false) String executionStatus,
            @RequestParam(required = false) Integer round,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return opsService.getRecentOperationLogs(page, size, operationType, executionStatus, round, from, to);
    }

    @PostMapping("/rounds")
    public WinningNumberResponse upsertRound(@Valid @RequestBody WinningNumberUpsertRequest request) {
        return opsService.upsertWinningNumber(request);
    }

    @PostMapping("/collect/latest")
    public WinningNumberResponse collectLatest() {
        return opsService.collectLatestWinningNumber();
    }

    @PostMapping("/collect/{round}")
    public WinningNumberResponse collectRound(@PathVariable int round) {
        return opsService.collectWinningNumber(round);
    }
}
