package com.kraft.recommend;

import com.kraft.common.lotto.LottoBitmask;
import com.kraft.winningnumber.WinningBallsOnly;
import com.kraft.winningnumber.WinningNumberRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * TD-008 1단계: {@link LottoRecommendationService}에서 이력 스냅샷 수명주기(로드·버전
 * 관리·완전성 판정)를 분리했다. REF-01: 이전에는 Spring 빈이 아니라
 * {@code LottoRecommendationService} 생성자 안에서 직접 만드는 plain 협력자였다 — 다중
 * 인스턴스 staleness 테스트({@code LottoRecommendationServiceTest}의 두 번째 인스턴스
 * 생성부)가 이 클래스도 함께 손으로 만들어 주면 되므로, 실제로는 생성자 주입 전환이
 * 그 테스트를 깨뜨리지 않는다 — {@code new}는 여전히 가능하고 프로덕션 조립만
 * Spring에 맡긴다.
 *
 * 스로틀된 실패 로그는 이 클래스가 아니라 {@code LottoRecommendationService}가 담당한다
 * ({@link LottoRecommendationService}의 로거에 바인딩된 기존 테스트를 그대로 두기 위함) —
 * 이 클래스는 {@link #currentDatabaseVersion()}이 던지는 예외를 그대로 전파하기만 한다.
 */
@Component
final class RecommendationHistorySnapshotManager {

    private final WinningNumberRepository winningNumberRepository;
    private final RecommendationHistoryStateRepository historyStateRepository;
    private final Clock clock;
    private final MeterRegistry meterRegistry;
    private final Timer historyRefreshTimer;

    // 조합 비트마스크(ball n → bit n-1)와 이력 완전성(회차 수·최신 반영 회차·최초 누락 회차)을
    // 한 번에 갱신되는 원자적 스냅샷으로 묶는다. 별개 필드로 뒀다면 refresh 도중 masks만
    // 갱신되고 metadata는 이전 값인 순간이 생겨(비원자적 갱신), ready 판정이 masks 상태와
    // 어긋날 수 있다.
    // NOTE: 인스턴스별 상태다 — DB가 단일 진실 공급원이라 인스턴스 수가 늘어나도 정합성
    // 문제는 없지만, 각 인스턴스가 독립적으로 리빌드하므로 롤링 배포로 막 뜬 인스턴스는
    // 리빌드가 끝나기 전까지 ready()==false라 추천 요청을 fail-closed로 거부한다(요청
    // 실패지 오답이 아니다). 헬스체크가 이 gauge(kraft_lotto_history_ready)를 트래픽
    // 유입 조건으로 삼지 않으면 롤링 배포 중 일시적 503이 노출될 수 있다.
    private volatile HistorySnapshot historySnapshot = HistorySnapshot.empty();

    record HistorySnapshot(Set<Long> masks, int roundCount, int historyThroughRound,
                            Integer firstMissingRound, long version, Instant loadedAt) {
        static HistorySnapshot empty() {
            return new HistorySnapshot(Set.of(), 0, 0, null, 0L, Instant.EPOCH);
        }

        /**
         * 1회부터 최신 회차까지 빈틈없이 로드돼야 배제 이력을 신뢰할 수 있다(P1-05) — 중간
         * 누락 회차가 있으면 그 회차의 1등 조합이 배제 목록에서 빠져 있을 수 있으므로, DB가
         * 비어 있는 경우(roundCount=0)뿐 아니라 연속성이 깨진 경우(firstMissingRound!=null)도
         * fail-closed로 막는다. 테스트 픽스처(BaseApiIntegrationTest 등)는 이 정책에 맞춰
         * 회차를 1부터 연속으로 시딩해야 한다.
         */
        boolean ready() {
            return roundCount > 0 && firstMissingRound == null;
        }
    }

    RecommendationHistorySnapshotManager(WinningNumberRepository winningNumberRepository,
                                         RecommendationHistoryStateRepository historyStateRepository,
                                         Clock clock,
                                         MeterRegistry meterRegistry) {
        this.winningNumberRepository = winningNumberRepository;
        this.historyStateRepository = historyStateRepository;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
        this.historyRefreshTimer = Timer.builder("kraft_lotto_history_refresh_duration_seconds")
                .description("Time spent loading and validating the recommendation history snapshot")
                .register(meterRegistry);
    }

    HistorySnapshot currentSnapshot() {
        return historySnapshot;
    }

    /** DB 조회가 실패하면 RuntimeException을 그대로 전파한다 — 호출자가 fail-closed로 처리한다. */
    long currentDatabaseVersion() {
        return historyStateRepository.findById(1)
                .map(RecommendationHistoryState::getVersion)
                .orElse(0L);
    }

    void refresh() {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            long versionBeforeLoad = currentDatabaseVersion();
            List<WinningBallsOnly> all = winningNumberRepository.findAllBalls();
            Set<Long> combos = new HashSet<>();
            List<Integer> rounds = new ArrayList<>(all.size());
            for (WinningBallsOnly wn : all) {
                combos.add(LottoBitmask.of(wn.getN1(), wn.getN2(), wn.getN3(), wn.getN4(), wn.getN5(), wn.getN6()));
                rounds.add(wn.getRound());
            }
            rounds.sort(Integer::compareTo);

            Integer firstMissingRound = null;
            int expected = 1;
            for (int round : rounds) {
                if (round != expected) {
                    firstMissingRound = expected;
                    break;
                }
                expected++;
            }
            int historyThroughRound = rounds.isEmpty() ? 0 : rounds.get(rounds.size() - 1);

            historySnapshot = new HistorySnapshot(
                    Set.copyOf(combos), rounds.size(), historyThroughRound, firstMissingRound,
                    versionBeforeLoad, Instant.now(clock));
        } finally {
            sample.stop(historyRefreshTimer);
        }
    }
}
