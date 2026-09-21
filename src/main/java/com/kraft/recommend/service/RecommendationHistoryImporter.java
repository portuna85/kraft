package com.kraft.recommend.service;

import com.kraft.recommend.domain.DrawDetails;
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
                existing.applyDetails(draw.details());
                updated++;
            } else {
                WinningDraw fresh = WinningDraw.builder()
                        .roundNo(draw.roundNo())
                        .numbers(draw.numbers())
                        .updatedAt(now)
                        .build();
                fresh.applyDetails(draw.details());
                winningDrawRepository.save(fresh);
                inserted++;
            }
        }

        // 이 UPDATE는 검증 구간을 뒤로 되돌리는 값이면 0행을 갱신한다(B14) — 더 앞서 나간
        // 다른 수입(예: 늦게 끝난 백필보다 먼저 완료된 자동 수집)의 검증 구간을 조용히
        // 되돌리지 않는다. 여기서 멈추지 않으면 draws는 이미 반영됐는데 검증 구간만 뒤로
        // 밀린 채 아무 일도 없었던 것처럼 보고될 수 있다.
        int metadataUpdated = stateRepository.updateVerificationMetadata(verifiedThroughRound, sourceReference, now);
        if (metadataUpdated == 0) {
            throw new RecommendationImportException("VERIFICATION_REGRESSION",
                    "요청한 verifiedThroughRound(" + verifiedThroughRound + ")가 이미 기록된 검증 구간보다 "
                            + "앞서지 않습니다 — 더 나중에 검증된 이력이 이미 있습니다. 회차 데이터는 "
                            + "반영됐지만(inserted=" + inserted + ", updated=" + updated + "), 검증 구간은 "
                            + "바뀌지 않았습니다. 의도적으로 구간을 줄여야 한다면 별도 운영 절차를 따르세요.");
        }

        return new Result(inserted, updated, verifiedThroughRound);
    }

    /**
     * {@link #importHistory}와 같은 형식·중복·범위·연속성 검증을 수행하지만 아무것도 쓰지
     * 않는다. 운영자가 실제 반영 전에 CSV를 미리 확인할 수 있게 한다(운영 절차 문서 참고).
     * 검증 실패 시 {@link #importHistory}와 동일한 {@link RecommendationImportException}을 던진다.
     */
    @Transactional(readOnly = true)
    public DryRunResult dryRunValidate(List<ImportedDraw> draws, int verifiedThroughRound) {
        List<ValidatedDraw> validated = validate(draws, verifiedThroughRound);

        int wouldInsert = 0;
        int wouldUpdate = 0;
        for (ValidatedDraw draw : validated) {
            if (winningDrawRepository.existsById(draw.roundNo())) {
                wouldUpdate++;
            } else {
                wouldInsert++;
            }
        }

        return new DryRunResult(wouldInsert, wouldUpdate, verifiedThroughRound);
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
            validated.add(new ValidatedDraw(draw.roundNo(), numbers.numbers(), draw.details()));
        }

        // 회차마다 existsById를 부르는 대신 구간 전체를 한 번에 조회한다(B11) — 왕복 수가
        // verifiedThroughRound에 비례해 늘어나지 않는다.
        Set<Integer> existingRounds = new HashSet<>(winningDrawRepository.findRoundNosBetween(1, verifiedThroughRound));
        for (int round = 1; round <= verifiedThroughRound; round++) {
            boolean inInput = seenRounds.contains(round);
            boolean inDb = existingRounds.contains(round);
            if (!inInput && !inDb) {
                throw new RecommendationImportException("MISSING_ROUND",
                        "1.." + verifiedThroughRound + " 구간에 누락된 회차가 있습니다: " + round);
            }
        }

        return validated;
    }

    private record ValidatedDraw(int roundNo, List<Integer> numbers, DrawDetails details) {
    }

    public record Result(int inserted, int updated, int verifiedThroughRound) {
    }

    public record DryRunResult(int wouldInsert, int wouldUpdate, int verifiedThroughRound) {
    }
}
