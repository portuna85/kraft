package com.kraft.recommend.service;

import com.kraft.recommend.domain.LottoNumbers;
import com.kraft.recommend.domain.RecommendationGenerationLimitException;
import com.kraft.recommend.domain.RecommendationHistorySnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * 역대 1등 조합을 제외한 균일 무작위 후보 생성. 이 클래스는 표본 추출과 반복 상한만 담당한다.
 */
@Component
@RequiredArgsConstructor
public class RecommendationCandidateGenerator {

    private static final int HISTORY_RETRY_CAP = 100;
    private static final int COLLECTION_ATTEMPTS_PER_ITEM = 100;

    private final RecommendationRandomSource randomSource;

    /** 서로 다른 {@code count}개 조합을 뽑는다. 역대 1등 조합과 같은 것은 반환하지 않는다. */
    public List<LottoNumbers> generate(int count, RecommendationHistorySnapshot snapshot) {
        Random random = randomSource.current();
        Set<Long> usedMasks = new HashSet<>();
        List<LottoNumbers> results = new ArrayList<>();

        int attempts = 0;
        int collectionCap = count * COLLECTION_ATTEMPTS_PER_ITEM;
        while (results.size() < count) {
            if (attempts >= collectionCap) {
                throw new RecommendationGenerationLimitException("요청 개수를 채우지 못했습니다.");
            }
            attempts++;
            LottoNumbers candidate = drawNonHistorical(snapshot, random);
            if (candidate != null && usedMasks.add(candidate.mask())) {
                results.add(candidate);
            }
        }
        return results;
    }

    /** 과거 당첨 조합이면 {@value #HISTORY_RETRY_CAP}회까지 다시 뽑는다. */
    private LottoNumbers drawNonHistorical(RecommendationHistorySnapshot snapshot, Random random) {
        for (int i = 0; i < HISTORY_RETRY_CAP; i++) {
            LottoNumbers candidate = drawOne(random);
            if (!snapshot.winningMasks().contains(candidate.mask())) {
                return candidate;
            }
        }
        return null;
    }

    private LottoNumbers drawOne(Random random) {
        List<Integer> pool = new ArrayList<>();
        for (int n = LottoNumbers.MIN; n <= LottoNumbers.MAX; n++) {
            pool.add(n);
        }
        Collections.shuffle(pool, random);
        return LottoNumbers.of(pool.subList(0, LottoNumbers.SIZE));
    }
}
