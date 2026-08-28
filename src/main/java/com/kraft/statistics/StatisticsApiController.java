package com.kraft.statistics;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stats")
public class StatisticsApiController {

    private final WinningStatisticsCacheService statisticsService;

    public StatisticsApiController(WinningStatisticsCacheService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @PostMapping("/analysis")
    public AnalysisResponse analysis(@Valid @RequestBody AnalysisRequest request) {
        return statisticsService.analyze(request.numbers());
    }
}
