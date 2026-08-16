package com.kraft.winningnumber;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/rounds")
public class RoundsApiController {

    private final WinningNumberQueryService winningNumberQueryService;

    public RoundsApiController(WinningNumberQueryService winningNumberQueryService) {
        this.winningNumberQueryService = winningNumberQueryService;
    }

    // REF-03/I-10: PublicApiCacheControlFilter가 이 경로(isCacheablePath)에 대해 2xx 응답마다
    // Cache-Control을 무조건 setHeader(교체)하므로, 여기서 별도로 걸었던 값은 항상 그 값으로
    // 덮여써져 wire에는 절대 나가지 않는 죽은 코드였다 — 운영 실측(§I-09/I-10 감사)도 필터의
    // "public, max-age=60, must-revalidate"만 관측됐다. 정책은 필터 한 곳에서만 관리한다.
    @GetMapping("/latest")
    public ResponseEntity<WinningNumberResponse> latest() {
        return ResponseEntity.ok(winningNumberQueryService.getLatest());
    }

    @GetMapping("/freshness")
    public ResponseEntity<RoundFreshnessResponse> freshness() {
        return ResponseEntity.ok(winningNumberQueryService.getFreshness());
    }
}
