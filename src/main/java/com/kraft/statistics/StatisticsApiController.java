package com.kraft.statistics;

import com.kraft.common.error.ApiErrorCode;
import com.kraft.common.error.ApiException;
import jakarta.validation.Valid;
import java.util.Set;
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

    // REF-03/I-10: PublicApiCacheControlFilter가 이 경로(isCacheablePath)에 대해 2xx 응답마다
    // Cache-Control을 무조건 setHeader(교체)하므로, 여기서 별도로 걸었던 값은 항상 그 값으로
    // 덮여써져 wire에는 절대 나가지 않는 죽은 코드였다 — 운영 실측(§I-09/I-10 감사)도 필터의
    // "public, max-age=60, must-revalidate"만 관측됐다. 정책은 필터 한 곳에서만 관리한다.
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
        return ResponseEntity.ok(body);
    }

    @GetMapping("/patterns")
    public ResponseEntity<PatternStatsResponse> patterns() {
        return ResponseEntity.ok(statisticsService.getPatternStats());
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
        return ResponseEntity.ok(body);
    }

    @PostMapping("/analysis")
    public AnalysisResponse analysis(@Valid @RequestBody AnalysisRequest request) {
        return statisticsService.analyze(request.numbers());
    }
}
