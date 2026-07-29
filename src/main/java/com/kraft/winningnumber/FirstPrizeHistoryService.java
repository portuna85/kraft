package com.kraft.winningnumber;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FirstPrizeHistoryService {

    private final WinningNumberRepository winningNumberRepository;

    public FirstPrizeHistoryService(WinningNumberRepository winningNumberRepository) {
        this.winningNumberRepository = winningNumberRepository;
    }

    /**
     * 같은 6개 번호가 여러 회차에서 당첨됐을 가능성까지 보존해 최신 회차부터 반환한다.
     * 호출부의 API 입력은 {@code @LottoNumbers}로 검증되며, 내부 통계 조합도 항상 6개다.
     */
    @Transactional(readOnly = true)
    public List<FirstPrizeHistoryDto> findByNumbers(List<Integer> numbers) {
        long combinationMask = 0L;
        for (int number : numbers) {
            combinationMask |= 1L << (number - 1);
        }
        return winningNumberRepository.findAllByCombinationMaskOrderByRoundDesc(combinationMask).stream()
                .map(FirstPrizeHistoryDto::from)
                .toList();
    }
}
