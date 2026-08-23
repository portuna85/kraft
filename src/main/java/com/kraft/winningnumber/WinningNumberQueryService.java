package com.kraft.winningnumber;

import com.kraft.common.config.CacheConfig;
import com.kraft.common.error.ApiErrorCode;
import com.kraft.common.error.ApiException;
import com.kraft.common.web.PageResponse;
import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import org.springframework.cache.annotation.Cacheable;
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

    // BE-CACHE-01(docs/improvement.md): 회차는 주 1회만 바뀌는데 홈·프론트 여러 곳이 이 조회를
    // 매 요청 반복한다. RoundsLatestCacheEvictionListener가 수집 이벤트로 비우고, TTL 5분은
    // 이벤트를 놓쳤을 때의 안전망이다(CacheConfig.ROUNDS_LATEST).
    @Cacheable(CacheConfig.ROUNDS_LATEST)
    public Optional<WinningNumberResponse> findLatest() {
        return winningNumberRepository.findTopByOrderByRoundDesc().map(WinningNumberResponse::from);
    }

    public WinningNumberResponse getByRound(int round) {
        return winningNumberRepository.findByRound(round)
                .map(WinningNumberResponse::from)
                .orElseThrow(() -> new ApiException(ApiErrorCode.ROUND_NOT_FOUND, round + "회차 정보를 찾을 수 없습니다."));
    }

    public RoundFreshnessResponse getFreshness() {
        // BE-PERF-01: this.findLatest()는 같은 클래스 내부 호출이라 @Cacheable 프록시를 우회한다
        // (자기호출 함정) — 이 메서드 자체가 캐시를 타지 않는 건 기존과 동일한 동작이다.
        // HomeSummaryService처럼 외부에서 findLatest()를 먼저 부르고 freshnessOf()로 조합하는
        // 호출자만 캐시 이득을 본다.
        return findLatest()
                .map(this::freshnessOf)
                .orElseThrow(() -> new ApiException(ApiErrorCode.ROUND_NOT_FOUND, "당첨 번호 데이터가 없습니다."));
    }

    /**
     * BE-PERF-01: {@link #getFreshness()}의 계산 로직을 순수 함수로 분리한 것 — 쿼리를 새로
     * 하지 않고 이미 조회된 최신 회차로부터 신선도를 계산한다. {@code findLatest()}(캐시 적용)를
     * 먼저 부른 결과를 넘기면 홈 요약처럼 회차 조회를 한 번만 쓰고 싶은 호출자가 재사용할 수 있다.
     */
    public RoundFreshnessResponse freshnessOf(WinningNumberResponse latest) {
        ZonedDateTime now = ZonedDateTime.now(clock).withZoneSameInstant(KST);
        return new RoundFreshnessResponse(
                latest.round(),
                latest.drawDate(),
                drawScheduleCalculator.isFresh(latest.drawDate(), now),
                now
        );
    }

    public PageResponse<WinningNumberResponse> list(int page, int size) {
        int clampedPage = Math.max(0, page);
        int clampedSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        Page<WinningNumber> result =
                winningNumberRepository.findAllByOrderByRoundDesc(PageRequest.of(clampedPage, clampedSize));
        return PageResponse.from(result.map(WinningNumberResponse::from));
    }
}
