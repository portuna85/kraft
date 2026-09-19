package com.kraft.recommend.service;

import com.kraft.recommend.domain.BalancedScorer;
import com.kraft.recommend.domain.CombinationScorer;
import com.kraft.recommend.domain.ExplanationCode;
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
 * 세 전략의 후보 생성(03문서 4절 POL-RANDOM/POL-BALANCED/POL-CHOICE, 6절 반복 상한). 점수
 * 계산·번호 집합 연산은 {@link BalancedScorer}/{@link CombinationScorer}(순수 로직)에 위임하고,
 * 이 클래스는 표본 추출·상한 관리만 담당한다.
 */
@Component
@RequiredArgsConstructor
public class RecommendationCandidateGenerator {

    private static final int HISTORY_RETRY_CAP = 100;
    private static final int CHOICE_CANDIDATE_COMPARISON_CAP = 50;

    public record BalancedCandidate(LottoNumbers numbers, int score, List<ExplanationCode> explanationCodes) {
    }

    private final RecommendationRandomSource randomSource;

    public List<LottoNumbers> generateRandom(NormalizedRecommendationRequest request,
                                              RecommendationHistorySnapshot snapshot) {
        List<Integer> pool = freePool(request);
        Random random = randomSource.current();
        Set<Long> usedMasks = new HashSet<>();
        List<LottoNumbers> results = new ArrayList<>();

        int attempts = 0;
        int collectionCap = request.count() * 100;
        while (results.size() < request.count()) {
            if (attempts >= collectionCap) {
                throw new RecommendationGenerationLimitException("요청 개수를 채우지 못했습니다(random).");
            }
            attempts++;
            LottoNumbers candidate = drawNonHistorical(pool, request.lockedNumbers(), snapshot, random);
            if (candidate != null && usedMasks.add(candidate.mask())) {
                results.add(candidate);
            }
        }
        return results;
    }

    public List<BalancedCandidate> generateBalanced(NormalizedRecommendationRequest request,
                                                      RecommendationHistorySnapshot snapshot) {
        List<Integer> pool = freePool(request);
        Random random = randomSource.current();
        int target = Math.max(50, request.count() * 10);
        int collectionCap = target * 100;

        Set<Long> usedMasks = new HashSet<>();
        List<BalancedCandidate> pooled = new ArrayList<>();

        int attempts = 0;
        while (pooled.size() < target && attempts < collectionCap) {
            attempts++;
            LottoNumbers candidate = drawNonHistorical(pool, request.lockedNumbers(), snapshot, random);
            if (candidate == null || !usedMasks.add(candidate.mask())) {
                continue;
            }
            BalancedScorer.Evaluation evaluation = BalancedScorer.evaluate(candidate.numbers());
            pooled.add(new BalancedCandidate(candidate, evaluation.score(), evaluation.codes()));
        }

        if (pooled.size() < request.count()) {
            throw new RecommendationGenerationLimitException("요청 개수를 채우지 못했습니다(balanced).");
        }

        pooled.sort((a, b) -> Integer.compare(b.score(), a.score()));
        return pooled.subList(0, request.count());
    }

    public List<LottoNumbers> generateReduceSharedWinnerRisk(NormalizedRecommendationRequest request,
                                                               RecommendationHistorySnapshot snapshot) {
        List<Integer> pool = freePool(request);
        Random random = randomSource.current();
        Set<Long> usedMasks = new HashSet<>();
        List<LottoNumbers> results = new ArrayList<>();

        int attempts = 0;
        int collectionCap = request.count() * 100;
        while (results.size() < request.count()) {
            if (attempts >= collectionCap) {
                throw new RecommendationGenerationLimitException("요청 개수를 채우지 못했습니다(reduce_shared_winner_risk).");
            }
            attempts++;
            LottoNumbers best = pickBestOfFifty(pool, request.lockedNumbers(), snapshot, random);
            if (best != null && usedMasks.add(best.mask())) {
                results.add(best);
            }
        }
        return results;
    }

    private LottoNumbers pickBestOfFifty(List<Integer> pool, Set<Integer> locked,
                                          RecommendationHistorySnapshot snapshot, Random random) {
        LottoNumbers best = null;
        int bestScore = Integer.MIN_VALUE;
        for (int i = 0; i < CHOICE_CANDIDATE_COMPARISON_CAP; i++) {
            LottoNumbers candidate = drawNonHistorical(pool, locked, snapshot, random);
            if (candidate == null) {
                continue;
            }
            int score = CombinationScorer.score(candidate.numbers());
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    /** 과거 당첨 조합이면 {@value #HISTORY_RETRY_CAP}회까지 다시 뽑는다(HIST-01, HIST-08 우회 금지). */
    private LottoNumbers drawNonHistorical(List<Integer> pool, Set<Integer> locked,
                                            RecommendationHistorySnapshot snapshot, Random random) {
        for (int i = 0; i < HISTORY_RETRY_CAP; i++) {
            LottoNumbers candidate = drawOne(pool, locked, random);
            if (!snapshot.winningMasks().contains(candidate.mask())) {
                return candidate;
            }
        }
        return null;
    }

    private LottoNumbers drawOne(List<Integer> pool, Set<Integer> locked, Random random) {
        List<Integer> shuffled = new ArrayList<>(pool);
        Collections.shuffle(shuffled, random);
        int need = LottoNumbers.SIZE - locked.size();
        List<Integer> picked = new ArrayList<>(locked);
        picked.addAll(shuffled.subList(0, need));
        return LottoNumbers.of(picked);
    }

    private List<Integer> freePool(NormalizedRecommendationRequest request) {
        List<Integer> pool = new ArrayList<>();
        for (int n = LottoNumbers.MIN; n <= LottoNumbers.MAX; n++) {
            if (!request.lockedNumbers().contains(n) && !request.excludedNumbers().contains(n)) {
                pool.add(n);
            }
        }
        return pool;
    }
}
