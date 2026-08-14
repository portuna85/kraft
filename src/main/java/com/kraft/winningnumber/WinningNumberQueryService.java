package com.kraft.winningnumber;

import com.kraft.common.error.ApiErrorCode;
import com.kraft.common.error.ApiException;
import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class WinningNumberQueryService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    // TD-003: 현재 유일한 호출자(AdminController.rounds)가 미리 클램프하지만, 이 서비스
    // 자체에도 방어를 둬 향후 호출자가 클램프를 놓쳐도 PageRequest.of가 IllegalArgumentException을
    // 던지지 않게 한다(AdminController와 동일한 클램프 범위).
    private static final int MAX_PAGE_SIZE = 100;

    private final WinningNumberRepository winningNumberRepository;
    private final LottoDrawScheduleCalculator drawScheduleCalculator;
    private final Clock clock;

    public WinningNumberQueryService(WinningNumberRepository winningNumberRepository,
                                     LottoDrawScheduleCalculator drawScheduleCalculator,
                                     Clock clock) {
        this.winningNumberRepository = winningNumberRepository;
        this.drawScheduleCalculator = drawScheduleCalculator;
        this.clock = clock;
    }

    public WinningNumberResponse getLatest() {
        return winningNumberRepository.findTopByOrderByRoundDesc()
                .map(WinningNumberResponse::from)
                .orElseThrow(() -> new ApiException(ApiErrorCode.ROUND_NOT_FOUND, "당첨 번호 데이터가 없습니다."));
    }

    public Optional<WinningNumberResponse> findLatest() {
        return winningNumberRepository.findTopByOrderByRoundDesc().map(WinningNumberResponse::from);
    }

    public WinningNumberResponse getByRound(int round) {
        return winningNumberRepository.findByRound(round)
                .map(WinningNumberResponse::from)
                .orElseThrow(() -> new ApiException(ApiErrorCode.ROUND_NOT_FOUND, round + "회차 정보를 찾을 수 없습니다."));
    }

    public RoundFreshnessResponse getFreshness() {
        ZonedDateTime now = ZonedDateTime.now(clock).withZoneSameInstant(KST);
        return winningNumberRepository.findTopByOrderByRoundDesc()
                .map(latest -> new RoundFreshnessResponse(
                        latest.getRound(),
                        latest.getDrawDate(),
                        drawScheduleCalculator.isFresh(latest.getDrawDate(), now),
                        now
                ))
                .orElseThrow(() -> new ApiException(ApiErrorCode.ROUND_NOT_FOUND, "당첨 번호 데이터가 없습니다."));
    }

    public WinningNumberListResponse list(int page, int size) {
        int clampedPage = Math.max(0, page);
        int clampedSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        Page<WinningNumber> result =
                winningNumberRepository.findAllByOrderByRoundDesc(PageRequest.of(clampedPage, clampedSize));
        return new WinningNumberListResponse(
                result.getContent().stream().map(WinningNumberResponse::from).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }
}
