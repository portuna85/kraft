package com.kraft.recommend;

import com.kraft.common.account.AccountDataDeletionHandler;
import org.springframework.stereotype.Component;

@Component
public class RecommendationAccountDataDeletionHandler implements AccountDataDeletionHandler {

    private final RecommendationSetRepository recommendationSetRepository;
    private final RecommendationItemRepository recommendationItemRepository;

    public RecommendationAccountDataDeletionHandler(RecommendationSetRepository recommendationSetRepository,
                                                     RecommendationItemRepository recommendationItemRepository) {
        this.recommendationSetRepository = recommendationSetRepository;
        this.recommendationItemRepository = recommendationItemRepository;
    }

    @Override
    public void deleteForAccount(Long userId) {
        recommendationSetRepository.findByOwnerUserIdOrderByCreatedAtDesc(userId).forEach(set -> {
            recommendationItemRepository.deleteBySetId(set.getId());
            recommendationSetRepository.delete(set);
        });
    }
}
