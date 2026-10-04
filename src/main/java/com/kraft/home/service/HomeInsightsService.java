package com.kraft.home.service;

import com.kraft.recommend.domain.WinningDraw;
import com.kraft.recommend.domain.WinningDrawRepository;
import com.kraft.recommend.service.RecommendationHistoryChanged;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * 홈의 "과거 기록 살펴보기" 요약. 추첨은 주 1회 바뀌지만 계산은 전체 회차를 읽으므로 한 시간 캐시하고
 * (CacheConfig), 이력이 새로 반영되면 커밋 직후 바로 비운다(BE-17).
 */
@Service
public class HomeInsightsService {

    static final int WINDOW = 30;

    private final WinningDrawRepository repository;

    public HomeInsightsService(WinningDrawRepository repository) {
        this.repository = repository;
    }

    /** 수입·정정이 커밋되면 오래된 요약이 한 시간 남지 않도록 비운다. 수입을 트랜잭션 밖에서 해도 동작한다. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @CacheEvict(value = "drawInsights", allEntries = true)
    public void onHistoryChanged(RecommendationHistoryChanged event) {
    }

    @Cacheable("drawInsights")
    @Transactional(readOnly = true)
    public DrawInsights current() {
        List<List<Integer>> newestFirst = repository.findAll(Sort.by(Sort.Direction.DESC, "roundNo"))
                .stream().map(WinningDraw::numbers).toList();
        return DrawInsights.of(newestFirst, WINDOW);
    }
}
