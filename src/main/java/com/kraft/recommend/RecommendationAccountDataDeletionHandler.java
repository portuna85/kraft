package com.kraft.recommend;

import com.kraft.common.account.AccountDataDeletionHandler;
import java.util.List;
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

    // TD-014: 세트 수(N)만큼 반복하던 개별 삭제(최대 1+2N 왕복)를, 세트 ID만 가벼운
    // 목록으로 조회한 뒤 IN절 벌크 삭제 2회(아이템 → 세트)로 대체한다. 호출자
    // (CommunityWithdrawalService.withdraw)가 이미 @Transactional이라 이 메서드는
    // 자체 트랜잭션을 열지 않는다 — AccountDataDeletionHandler 계약대로 호출자의
    // 트랜잭션 안에서 실행된다. recommendation_items.set_id FK에 ON DELETE CASCADE가
    // 없으므로 아이템을 세트보다 반드시 먼저 지워야 한다.
    @Override
    public void deleteForAccount(Long userId) {
        List<Long> setIds = recommendationSetRepository.findByOwnerUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(RecommendationSet::getId)
                .toList();
        if (setIds.isEmpty()) {
            return;
        }
        recommendationItemRepository.deleteBySetIdIn(setIds);
        recommendationSetRepository.deleteByOwnerUserId(userId);
    }
}
