package com.kraft.recommend.service;

import com.kraft.recommend.domain.RecommendationHistoryNotReadyException;
import com.kraft.recommend.domain.RecommendationHistorySnapshot;
import com.kraft.recommend.domain.RecommendationHistoryState;
import com.kraft.recommend.domain.RecommendationHistoryStateRepository;
import com.kraft.recommend.domain.WinningDraw;
import com.kraft.recommend.domain.WinningDrawRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 검증된 당첨 이력의 불변 스냅샷을 {@code volatile} 참조로 교체한다(01문서 6절). 애플리케이션
 * 시작 시({@code @PostConstruct}) 강제로 읽지 않는다 — 이력이 비어 있거나 DB 문제가 있어도
 * 게시판 전체 기동에 영향을 주지 않기 위해서다(01문서 6절 경고, 02문서 5.3절). 대신 매 요청마다
 * DB의 {@code version}을 먼저 확인해 캐시가 최신이면 재사용하고, 아니면 다시 읽는다.
 */
@Component
@RequiredArgsConstructor
public class RecommendationHistoryProvider {

    private static final Integer STATE_ID = 1;

    private final WinningDrawRepository winningDrawRepository;
    private final RecommendationHistoryStateRepository stateRepository;

    private volatile RecommendationHistorySnapshot cached;

    /**
     * 준비되지 않은 이력이면 {@link RecommendationHistoryNotReadyException}을 던진다
     * (HIST-03). 준비된 스냅샷 하나를 반환하며, 호출자는 생성이 끝난 뒤 반드시
     * {@link #verifyUnchanged(RecommendationHistorySnapshot)}로 재확인해야 한다(HIST-04).
     */
    @Transactional(readOnly = true)
    public RecommendationHistorySnapshot currentReadySnapshot() {
        RecommendationHistorySnapshot snapshot = snapshotForCurrentVersion();
        if (!snapshot.isReady()) {
            throw new RecommendationHistoryNotReadyException("검증된 당첨 이력이 준비되지 않았습니다.");
        }
        return snapshot;
    }

    /**
     * 샘플링 후 DB 버전을 다시 확인한다(HIST-04/HIST-05). 도중에 이력이 바뀌었으면(정정·삭제
     * 포함) 결과를 버리게 한다 — 호출자는 이미 만든 결과를 응답하지 않아야 한다.
     */
    @Transactional(readOnly = true)
    public void verifyUnchanged(RecommendationHistorySnapshot snapshotUsedForGeneration) {
        RecommendationHistoryState state = stateRepository.findById(STATE_ID).orElse(null);
        if (state == null || !state.getVersion().equals(snapshotUsedForGeneration.version())) {
            throw new RecommendationHistoryNotReadyException("추천 생성 중 이력이 변경되었습니다. 다시 시도해 주세요.");
        }
    }

    /**
     * 락 없이 버전만 먼저 비교한다(BE-10) — 캐시가 최신이면(대부분의 요청) 여기서 바로
     * 끝나므로, 동시 요청들이 {@link #refresh}의 {@code synchronized}에서 서로를 기다리며
     * 커넥션을 쥔 채 대기하지 않는다. 잠금은 실제로 다시 읽어야 할 때만 진입한다.
     */
    private RecommendationHistorySnapshot snapshotForCurrentVersion() {
        RecommendationHistoryState state = stateRepository.findById(STATE_ID).orElse(null);
        if (state == null) {
            return emptySnapshot();
        }
        RecommendationHistorySnapshot current = cached;
        if (current != null && current.version() == state.getVersion()) {
            return current;
        }
        return refresh(state);
    }

    private synchronized RecommendationHistorySnapshot refresh(RecommendationHistoryState state) {
        // 잠금을 기다리는 동안 다른 스레드가 이미 갱신했을 수 있어 다시 한번 확인한다.
        RecommendationHistorySnapshot current = cached;
        if (current != null && current.version() == state.getVersion()) {
            return current;
        }

        List<WinningDraw> draws = winningDrawRepository.findAll();
        Set<Long> masks = draws.stream().map(WinningDraw::mask).collect(Collectors.toSet());
        int maxRound = draws.stream().mapToInt(WinningDraw::getRoundNo).max().orElse(0);
        int verifiedThroughRound = state.getVerifiedThroughRound() == null ? 0 : state.getVerifiedThroughRound();

        RecommendationHistorySnapshot fresh = new RecommendationHistorySnapshot(
                masks, draws.size(), maxRound, verifiedThroughRound, state.getVersion(), Instant.now());
        cached = fresh;
        return fresh;
    }

    private RecommendationHistorySnapshot emptySnapshot() {
        return new RecommendationHistorySnapshot(Set.of(), 0, 0, 0, -1L, Instant.now());
    }
}
