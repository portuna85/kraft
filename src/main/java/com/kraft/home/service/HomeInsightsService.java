package com.kraft.home.service;

import com.kraft.recommend.domain.WinningDraw;
import com.kraft.recommend.domain.WinningDrawRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 홈의 "과거 기록 살펴보기" 요약. 추첨은 주 1회라 짧은 캐시(전역 45초)로 충분하다. */
@Service
public class HomeInsightsService {

    static final int WINDOW = 30;

    private final WinningDrawRepository repository;

    public HomeInsightsService(WinningDrawRepository repository) {
        this.repository = repository;
    }

    @Cacheable("drawInsights")
    @Transactional(readOnly = true)
    public DrawInsights current() {
        List<List<Integer>> newestFirst = repository.findAll(Sort.by(Sort.Direction.DESC, "roundNo"))
                .stream().map(WinningDraw::numbers).toList();
        return DrawInsights.of(newestFirst, WINDOW);
    }
}
