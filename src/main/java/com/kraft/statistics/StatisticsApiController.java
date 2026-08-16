package com.kraft.statistics;

import com.kraft.common.error.ApiErrorCode;
import com.kraft.common.error.ApiException;
import jakarta.validation.Valid;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stats")
public class StatisticsApiController {

    private static final Set<Integer> ALLOWED_LIMITS = Set.of(100, 200, 500);

    private final WinningStatisticsCacheService statisticsService;

    public StatisticsApiController(WinningStatisticsCacheService statisticsService) {
        this.statisticsService = statisticsService;
    }

    // 2026-08-16 정정: 이전에 REF-03/I-10 정리로 이 호출을 지웠다가 배포 직후 스모크
    // 테스트(check_header_contains, curl -I 즉 HEAD 요청)가 깨졌다 — 원인은
    // PublicApiCacheControlFilter.shouldNotFilter가 GET이 아니면(HEAD 포함) 무조건
    // 건너뛰도록 되어 있어서다. GET에서는 이 호출이 필터에 덮여써지는 죽은 코드가
    // 맞지만(§I-09/I-10 감사에서 실측 확인), HEAD에서는 필터가 아예 안 도므로 이
    // 컨트롤러 레벨 값이 유일한 소스다 — 없애면 HEAD 응답에 Cache-Control이 아예
    // 안 붙거나(또는 Spring Security 기본 writer의 no-store 계열로 남는다). 되돌린다.
    @GetMapping("/frequency")
    public ResponseEntity<FrequencyStatsResponse> frequency(@RequestParam(required = false) Integer limit) {
        FrequencyStatsResponse body;
        if (limit == null) {
            body = statisticsService.getFrequencyStats();
        } else {
            if (!ALLOWED_LIMITS.contains(limit)) {
                throw new ApiException(ApiErrorCode.INVALID_LIMIT,
                        "limit 허용값: 100, 200, 500");
            }
            body = statisticsService.getFrequencyStatsByLimit(limit);
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(60, TimeUnit.SECONDS).cachePublic())
                .body(body);
    }

    @GetMapping("/patterns")
    public ResponseEntity<PatternStatsResponse> patterns() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(60, TimeUnit.SECONDS).cachePublic())
                .body(statisticsService.getPatternStats());
    }

    @GetMapping("/companion")
    public ResponseEntity<CompanionStatsResponse> companion(@RequestParam(required = false) Integer ball) {
        CompanionStatsResponse body;
        if (ball == null) {
            body = statisticsService.getCompanionStats();
        } else {
            if (ball < 1 || ball > 45) {
                throw new ApiException(ApiErrorCode.INVALID_BALL,
                        "ball 허용 범위: 1~45");
            }
            body = statisticsService.getCompanionStatsByBall(ball);
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(60, TimeUnit.SECONDS).cachePublic())
                .body(body);
    }

    @PostMapping("/analysis")
    public AnalysisResponse analysis(@Valid @RequestBody AnalysisRequest request) {
        return statisticsService.analyze(request.numbers());
    }
}
