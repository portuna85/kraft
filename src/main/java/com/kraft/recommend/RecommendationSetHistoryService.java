package com.kraft.recommend;

import com.kraft.common.error.ApiException;
import com.kraft.common.lotto.LottoNumberCodec;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 추천 세트의 영속화·조회·삭제를 담당한다. 생성 로직({@link LottoRecommendationService})과
 * 분리한 이유는 두 가지다: 책임이 다르고(생성 알고리즘 vs 저장소 접근), 별도 빈으로 둬야
 * {@code @Transactional}이 프록시를 거쳐 실제로 적용된다(같은 빈 안에서 this.method()로
 * 호출하면 Spring AOP 프록시를 우회해 트랜잭션이 걸리지 않는다).
 */
@Service
@Transactional
public class RecommendationSetHistoryService {

    private final RecommendationSetRepository recommendationSetRepository;
    private final RecommendationItemRepository recommendationItemRepository;
    private final LottoNumberCodec lottoNumberCodec;

    public RecommendationSetHistoryService(RecommendationSetRepository recommendationSetRepository,
                                            RecommendationItemRepository recommendationItemRepository,
                                            LottoNumberCodec lottoNumberCodec) {
        this.recommendationSetRepository = recommendationSetRepository;
        this.recommendationItemRepository = recommendationItemRepository;
        this.lottoNumberCodec = lottoNumberCodec;
    }

    public Long persist(String clientTokenHash, String strategy, String algorithmVersion, int historyThroughRound,
                         List<Integer> locked, List<Integer> excluded, List<RecommendationItemView> items,
                         OffsetDateTime createdAt) {
        String lockedStorage = locked.isEmpty() ? null : lottoNumberCodec.toStorageValueSubset(locked);
        String excludedStorage = excluded.isEmpty() ? null : lottoNumberCodec.toStorageValueSubset(excluded);

        RecommendationSet set = recommendationSetRepository.save(new RecommendationSet(
                clientTokenHash, strategy, algorithmVersion, historyThroughRound,
                lockedStorage, excludedStorage, createdAt));

        for (RecommendationItemView item : items) {
            String explanationCodes = item.explanationCodes().isEmpty()
                    ? null
                    : item.explanationCodes().stream().map(Enum::name).reduce((a, b) -> a + "," + b).orElse(null);
            recommendationItemRepository.save(new RecommendationItem(
                    set.getId(), item.position(), lottoNumberCodec.toStorageValueSubset(item.numbers()),
                    item.score(), explanationCodes));
        }
        return set.getId();
    }

    @Transactional(readOnly = true)
    public List<RecommendationSetSummary> list(String clientTokenHash) {
        return recommendationSetRepository.findByClientTokenHashOrderByCreatedAtDesc(clientTokenHash).stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public RecommendationSetSummary get(String clientTokenHash, long id) {
        RecommendationSet set = findOwned(clientTokenHash, id);
        return toSummary(set);
    }

    public void delete(String clientTokenHash, long id) {
        RecommendationSet set = findOwned(clientTokenHash, id);
        recommendationItemRepository.deleteBySetId(set.getId());
        recommendationSetRepository.delete(set);
    }

    private RecommendationSet findOwned(String clientTokenHash, long id) {
        RecommendationSet set = recommendationSetRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RECOMMENDATION_SET_NOT_FOUND",
                        "추천 세트를 찾을 수 없습니다."));
        if (!clientTokenHash.equals(set.getClientTokenHash())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "RECOMMENDATION_SET_NOT_OWNED",
                    "이 추천 세트에 대한 권한이 없습니다.");
        }
        return set;
    }

    private RecommendationSetSummary toSummary(RecommendationSet set) {
        List<RecommendationItemView> items = recommendationItemRepository.findBySetIdOrderByPosition(set.getId())
                .stream()
                .map(item -> new RecommendationItemView(
                        item.getPosition(),
                        lottoNumberCodec.fromStorageValue(item.getNumbers()),
                        item.getScore(),
                        parseExplanationCodes(item.getExplanationCodes())))
                .toList();
        return new RecommendationSetSummary(
                set.getId(),
                set.getStrategy(),
                set.getAlgorithmVersion(),
                set.getHistoryThroughRound(),
                lottoNumberCodec.fromStorageValue(set.getLockedNumbers()),
                lottoNumberCodec.fromStorageValue(set.getExcludedNumbers()),
                set.getCreatedAt(),
                items);
    }

    private static List<ExplanationCode> parseExplanationCodes(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(value.split(",")).map(ExplanationCode::valueOf).toList();
    }
}
