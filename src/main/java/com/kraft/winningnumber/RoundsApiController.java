package com.kraft.winningnumber;

import java.util.concurrent.TimeUnit;
import org.springframework.http.CacheControl;
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

    // 2026-08-16 정정: 이전에 REF-03/I-10 정리로 이 호출을 지웠다가 배포 직후 스모크
    // 테스트(check_header_contains, curl -I 즉 HEAD 요청)가 깨졌다 — 원인은
    // PublicApiCacheControlFilter.shouldNotFilter가 GET이 아니면(HEAD 포함) 무조건
    // 건너뛰도록 되어 있어서다. GET에서는 이 호출이 필터에 덮여써지는 죽은 코드가
    // 맞지만(§I-09/I-10 감사에서 실측 확인), HEAD에서는 필터가 아예 안 도므로 이
    // 컨트롤러 레벨 값이 유일한 소스다 — 없애면 HEAD 응답에 Cache-Control이 아예
    // 안 붙거나(또는 Spring Security 기본 writer의 no-store 계열로 남는다). 되돌린다.
    @GetMapping("/latest")
    public ResponseEntity<WinningNumberResponse> latest() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(60, TimeUnit.SECONDS).cachePublic())
                .body(winningNumberQueryService.getLatest());
    }

    @GetMapping("/freshness")
    public ResponseEntity<RoundFreshnessResponse> freshness() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(60, TimeUnit.SECONDS).cachePublic())
                .body(winningNumberQueryService.getFreshness());
    }
}
