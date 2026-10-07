package com.kraft.recommend.service;

import com.kraft.recommend.domain.LottoNumbers;
import com.kraft.recommend.domain.RecommendationHistorySnapshot;
import com.kraft.recommend.dto.RecommendRequestDto;
import com.kraft.recommend.dto.RecommendResponseDto;
import com.kraft.recommend.dto.RecommendationItemDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 요청 검증 → 이력 스냅샷 획득 → 후보 생성 → 생성 후 이력 재검증 → 응답 조립만 담당한다.
 * 회원·게시글·추천 저장 엔티티와 연결하지 않는다 — 비저장 응답이다. 고정 동작은 역대 1등 조합
 * 제외 하나뿐이다.
 */
@Service
@RequiredArgsConstructor
public class RecommendationService {

    private static final String ALGORITHM_VERSION = "uniform-random-v1";
    private static final String EXCLUSION_POLICY_VERSION = "historical-first-prize-v1";

    private final RecommendationRequestValidator requestValidator;
    private final RecommendationHistoryProvider historyProvider;
    private final RecommendationCandidateGenerator candidateGenerator;

    public RecommendResponseDto recommend(RecommendRequestDto request) {
        int count = requestValidator.validate(request);
        RecommendationHistorySnapshot snapshot = historyProvider.currentReadySnapshot();

        List<LottoNumbers> combos = candidateGenerator.generate(count, snapshot);

        // HIST-04/05: 생성 도중 이력이 바뀌었으면(정정·삭제 포함) 결과를 버린다.
        historyProvider.verifyUnchanged(snapshot);

        List<RecommendationItemDto> items = new ArrayList<>();
        for (int i = 0; i < combos.size(); i++) {
            items.add(new RecommendationItemDto(i + 1, combos.get(i).numbers()));
        }
        return new RecommendResponseDto(
                ALGORITHM_VERSION,
                snapshot.verifiedThroughRound(),
                true,
                EXCLUSION_POLICY_VERSION,
                items
        );
    }
}
