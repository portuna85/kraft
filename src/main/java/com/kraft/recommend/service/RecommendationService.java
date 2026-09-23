package com.kraft.recommend.service;

import com.kraft.recommend.domain.Combinatorics;
import com.kraft.recommend.domain.ExplanationCode;
import com.kraft.recommend.domain.LottoBitmask;
import com.kraft.recommend.domain.LottoNumbers;
import com.kraft.recommend.domain.RecommendationHistorySnapshot;
import com.kraft.recommend.domain.RecommendationValidationException;
import com.kraft.recommend.dto.RecommendRequestDto;
import com.kraft.recommend.dto.RecommendResponseDto;
import com.kraft.recommend.dto.RecommendationItemDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 요청 검증 → 이력 스냅샷 획득 → 실현 가능성 검증 → 후보 생성 → 생성 후 이력 재검증 → 응답
 * 조립만 담당한다(02문서 3절). 회원·게시글·추천 저장 엔티티와 연결하지 않는다 — 비저장
 * 응답이다.
 */
@Service
@RequiredArgsConstructor
public class RecommendationService {

    private static final String EXCLUSION_POLICY_VERSION = "historical-first-prize-v1";

    private final RecommendationRequestValidator requestValidator;
    private final RecommendationHistoryProvider historyProvider;
    private final RecommendationCandidateGenerator candidateGenerator;

    public RecommendResponseDto recommend(RecommendRequestDto request) {
        NormalizedRecommendationRequest normalized = requestValidator.validate(request);
        RecommendationHistorySnapshot snapshot = historyProvider.currentReadySnapshot();

        long allowedPossible = verifyCombinationFeasibility(normalized, snapshot);

        List<RecommendationItemDto> items = switch (normalized.strategy()) {
            case RANDOM -> assembleWithoutScore(candidateGenerator.generateRandom(normalized, snapshot, allowedPossible));
            case REDUCE_SHARED_WINNER_RISK -> assembleWithoutScore(
                    candidateGenerator.generateReduceSharedWinnerRisk(normalized, snapshot, allowedPossible));
            case BALANCED -> assembleBalanced(candidateGenerator.generateBalanced(normalized, snapshot, allowedPossible));
        };

        // HIST-04/05: 생성 도중 이력이 바뀌었으면(정정·삭제 포함) 결과를 버린다.
        historyProvider.verifyUnchanged(snapshot);

        return new RecommendResponseDto(
                normalized.strategy().code(),
                normalized.strategy().algorithmVersion(),
                snapshot.verifiedThroughRound(),
                true,
                EXCLUSION_POLICY_VERSION,
                items
        );
    }

    /**
     * 요청 count가 수학적으로 가능한지 사전 검증한다(01문서 3절). 허용 가능 수 =
     * {@code C(45-E-L, 6-L)} - 고정 번호를 모두 포함하고 제외 번호를 포함하지 않는 과거 고유
     * 조합 수. 부족하면 {@code INSUFFICIENT_UNIQUE_COMBINATIONS}로 즉시 거절해 반복 상한
     * 소진(생성 한도 오류)과 원인을 구분한다.
     * <p>
     * 계산한 허용 가능 수를 그대로 돌려준다(개선 보고서 PERF-01) — 후보 생성기가 이 값으로
     * 목표 표본 크기를 조합 공간 크기에 맞춰 줄이거나, 조합 공간 자체가 작으면 무작위 표본
     * 추출 대신 전수 나열로 전환한다. 예전에는 이 계산이 여기서 버려져, 생성기가 가능한
     * 조합보다 훨씬 큰 목표치를 향해 계속 무작위로 뽑으며 충돌만 반복하다 상한을 소진했다.
     */
    private long verifyCombinationFeasibility(NormalizedRecommendationRequest request,
                                               RecommendationHistorySnapshot snapshot) {
        int locked = request.lockedNumbers().size();
        int excluded = request.excludedNumbers().size();
        int freeCount = LottoNumbers.MAX - excluded - locked;
        int needed = LottoNumbers.SIZE - locked;

        long totalPossible = Combinatorics.nCr(freeCount, needed);

        long lockedMask = LottoBitmask.maskOf(request.lockedNumbers());
        long excludedMask = LottoBitmask.maskOf(request.excludedNumbers());
        long matchingHistorical = snapshot.winningMasks().stream()
                .filter(mask -> (mask & lockedMask) == lockedMask && (mask & excludedMask) == 0L)
                .count();

        long allowedPossible = totalPossible - matchingHistorical;
        if (request.count() > allowedPossible) {
            throw new RecommendationValidationException(
                    "INSUFFICIENT_UNIQUE_COMBINATIONS",
                    "요청한 개수만큼 생성할 수 있는 고유 조합이 부족합니다.");
        }
        return allowedPossible;
    }

    private List<RecommendationItemDto> assembleWithoutScore(List<LottoNumbers> combos) {
        List<RecommendationItemDto> items = new ArrayList<>();
        for (int i = 0; i < combos.size(); i++) {
            items.add(new RecommendationItemDto(i + 1, combos.get(i).numbers(), null, List.of()));
        }
        return items;
    }

    private List<RecommendationItemDto> assembleBalanced(List<RecommendationCandidateGenerator.BalancedCandidate> combos) {
        List<RecommendationItemDto> items = new ArrayList<>();
        for (int i = 0; i < combos.size(); i++) {
            RecommendationCandidateGenerator.BalancedCandidate candidate = combos.get(i);
            List<String> codes = candidate.explanationCodes().stream().map(ExplanationCode::name).toList();
            items.add(new RecommendationItemDto(i + 1, candidate.numbers().numbers(), candidate.score(), codes));
        }
        return items;
    }
}
