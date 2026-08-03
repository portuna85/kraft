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
        Page<WinningNumber> result = winningNumberRepository.findAllByOrderByRoundDesc(PageRequest.of(page, size));
        return new WinningNumberListResponse(
                result.getContent().stream().map(WinningNumberResponse::from).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }
}
