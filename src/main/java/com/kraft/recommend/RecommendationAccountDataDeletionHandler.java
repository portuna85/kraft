package com.kraft.recommend;

import com.kraft.common.account.AccountDataDeletionHandler;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class RecommendationAccountDataDeletionHandler implements AccountDataDeletionHandler {

    private final RecommendationSetRepository recommendationSetRepository;
    private final RecommendationItemRepository recommendationItemRepository;
    private final Timer deletionTimer;

    public RecommendationAccountDataDeletionHandler(RecommendationSetRepository recommendationSetRepository,
                                                     RecommendationItemRepository recommendationItemRepository,
                                                     MeterRegistry meterRegistry) {
        this.recommendationSetRepository = recommendationSetRepository;
        this.recommendationItemRepository = recommendationItemRepository;
        this.deletionTimer = Timer.builder("kraft_recommendation_account_deletion_duration_seconds")
                .description("Time spent deleting recommendation data during account withdrawal")
                .register(meterRegistry);
    }

    // TD-014: 세트 수(N)만큼 반복하던 개별 삭제(최대 1+2N 왕복)를, 세트 ID만 가벼운
    // 목록으로 조회한 뒤 IN절 벌크 삭제 2회(아이템 → 세트)로 대체한다. 호출자
    // (CommunityWithdrawalService.withdraw)가 이미 @Transactional이라 이 메서드는
    // 자체 트랜잭션을 열지 않는다 — AccountDataDeletionHandler 계약대로 호출자의
    // 트랜잭션 안에서 실행된다. recommendation_items.set_id FK에 ON DELETE CASCADE가
    // 없으므로 아이템을 세트보다 반드시 먼저 지워야 한다.
    //
    // DATA-REC-01(docs/improvement.md): setIds가 매우 크면 아이템 삭제의 `IN` 절
    // 파라미터 수가 DB/드라이버 상한에 가까워질 수 있다 — CHUNK_SIZE 단위로 나눠
    // 지운다. 세트 자체는 ownerUserId로 직접 지우므로(`IN` 절이 아니다) 청크가
    // 필요 없다.
    private static final int CHUNK_SIZE = 500;

    @Override
    public void deleteForAccount(Long userId) {
        deletionTimer.record(() -> deleteForAccountInternal(userId));
    }

    private void deleteForAccountInternal(Long userId) {
        List<Long> setIds = recommendationSetRepository.findIdsByOwnerUserId(userId);
        if (setIds.isEmpty()) {
            return;
        }
        for (int start = 0; start < setIds.size(); start += CHUNK_SIZE) {
            List<Long> chunk = setIds.subList(start, Math.min(start + CHUNK_SIZE, setIds.size()));
            recommendationItemRepository.deleteBySetIdIn(chunk);
        }
        recommendationSetRepository.deleteByOwnerUserId(userId);
    }
}
