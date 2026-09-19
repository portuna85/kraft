package com.kraft.recommend.service;

import com.kraft.recommend.domain.LottoNumbers;
import com.kraft.recommend.domain.RecommendationHistoryStateRepository;
import com.kraft.recommend.domain.RecommendationImportException;
import com.kraft.recommend.domain.WinningDraw;
import com.kraft.recommend.domain.WinningDrawRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 검증된 당첨 이력을 운영 절차로 반영하는 내부 경계(02문서 3절). 공개 REST 엔드포인트나
 * 관리자 화면은 없다 — {@code com.kraft.operations.recommendimport.RecommendationImportRunner}
 * 같은 일회성 도구에서만 호출한다.
 * <p>
 * 원자료(어디서 구했는지는 호출자 책임)를 받아 전부 검증한 뒤에만 DB에 쓴다. 검증을 쓰기보다
 * 먼저 전부 끝내므로, 검증 실패 시나리오는 트랜잭션 롤백에 기대지 않고도 "아무것도 쓰지
 * 않음"을 보장한다(부분 반영 금지).
 */
@Service
@RequiredArgsConstructor
public class RecommendationHistoryImporter {

    private final WinningDrawRepository winningDrawRepository;
    private final RecommendationHistoryStateRepository stateRepository;

    @Transactional
    public Result importHistory(List<ImportedDraw> draws, int verifiedThroughRound, String sourceReference) {
        List<ValidatedDraw> validated = validate(draws, verifiedThroughRound);

        if (!stateRepository.existsById(1)) {
            throw new RecommendationImportException("HISTORY_STATE_MISSING",
                    "recommendation_history_state(id=1) 행이 없습니다. V20 마이그레이션이 적용되었는지 확인하세요.");
        }

        LocalDateTime now = LocalDateTime.now();
        int inserted = 0;
        int updated = 0;
        for (ValidatedDraw draw : validated) {
            WinningDraw existing = winningDrawRepository.findById(draw.roundNo()).orElse(null);
            if (existing != null) {
                // 관리되는 인스턴스를 직접 바꾼다 — 새 인스턴스로 save()(merge)하면 변경이
                // 조용히 유실될 수 있다(WinningDraw.replaceNumbers 주석 참고).
                existing.replaceNumbers(draw.numbers(), now);
                updated++;
            } else {
                winningDrawRepository.save(WinningDraw.builder()
                        .roundNo(draw.roundNo())
                        .numbers(draw.numbers())
                        .updatedAt(now)
                        .build());
                inserted++;
            }
        }

        stateRepository.updateVerificationMetadata(verifiedThroughRound, sourceReference, now);

        return new Result(inserted, updated, verifiedThroughRound);
    }

    private List<ValidatedDraw> validate(List<ImportedDraw> draws, int verifiedThroughRound) {
        if (verifiedThroughRound <= 0) {
            throw new RecommendationImportException("INVALID_VERIFIED_THROUGH_ROUND",
                    "verifiedThroughRound는 1 이상이어야 합니다: " + verifiedThroughRound);
        }

        List<ValidatedDraw> validated = new ArrayList<>();
        Set<Integer> seenRounds = new HashSet<>();
        for (ImportedDraw draw : draws) {
            if (draw.roundNo() <= 0) {
                throw new RecommendationImportException("INVALID_ROUND",
                        "회차는 1 이상이어야 합니다: " + draw.roundNo());
            }
            if (!seenRounds.add(draw.roundNo())) {
                throw new RecommendationImportException("DUPLICATE_ROUND_IN_INPUT",
                        "입력에 같은 회차가 두 번 있습니다: " + draw.roundNo());
            }
            LottoNumbers numbers;
            try {
                numbers = LottoNumbers.of(draw.numbers());
            } catch (IllegalArgumentException e) {
                throw new RecommendationImportException("INVALID_NUMBERS",
                        "회차 " + draw.roundNo() + "의 번호가 올바르지 않습니다: " + e.getMessage());
            }
            validated.add(new ValidatedDraw(draw.roundNo(), numbers.numbers()));
        }

        for (int round = 1; round <= verifiedThroughRound; round++) {
            boolean inInput = seenRounds.contains(round);
            boolean inDb = winningDrawRepository.existsById(round);
            if (!inInput && !inDb) {
                throw new RecommendationImportException("MISSING_ROUND",
                        "1.." + verifiedThroughRound + " 구간에 누락된 회차가 있습니다: " + round);
            }
        }

        return validated;
    }

    private record ValidatedDraw(int roundNo, List<Integer> numbers) {
    }

    public record Result(int inserted, int updated, int verifiedThroughRound) {
    }
}
