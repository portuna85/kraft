package com.kraft.recommend;

import com.kraft.winningnumber.WinningBallsOnly;
import com.kraft.winningnumber.WinningNumberRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("추천 이력 스냅샷 매니저 단위 테스트(TD-008 1단계)")
class RecommendationHistorySnapshotManagerTest {

    @Mock
    private WinningNumberRepository winningNumberRepository;

    @Mock
    private RecommendationHistoryStateRepository historyStateRepository;

    @Mock
    private RecommendationHistoryState historyState;

    private RecommendationHistorySnapshotManager manager;

    private static WinningBallsOnly ballsOnly(int round, int n1, int n2, int n3, int n4, int n5, int n6) {
        return new WinningBallsOnly() {
            public int getRound() { return round; }
            public int getN1() { return n1; }
            public int getN2() { return n2; }
            public int getN3() { return n3; }
            public int getN4() { return n4; }
            public int getN5() { return n5; }
            public int getN6() { return n6; }
        };
    }

    @BeforeEach
    void setUp() {
        manager = new RecommendationHistorySnapshotManager(
                winningNumberRepository, historyStateRepository, Clock.systemUTC(), new SimpleMeterRegistry());
    }

    @Test
    @DisplayName("초기 스냅샷은 비어 있고 ready가 false다")
    void currentSnapshot_beforeRefresh_isEmptyAndNotReady() {
        RecommendationHistorySnapshotManager.HistorySnapshot snapshot = manager.currentSnapshot();

        assertThat(snapshot.ready()).isFalse();
        assertThat(snapshot.roundCount()).isZero();
        assertThat(snapshot.masks()).isEmpty();
    }

    @Test
    @DisplayName("1회부터 연속으로 로드되면 ready가 true다")
    void refresh_continuousFromRoundOne_isReady() {
        given(winningNumberRepository.findAllBalls()).willReturn(List.of(
                ballsOnly(1, 1, 2, 3, 4, 5, 6),
                ballsOnly(2, 7, 8, 9, 10, 11, 12)
        ));
        given(historyStateRepository.findById(1)).willReturn(Optional.of(historyState));
        given(historyState.getVersion()).willReturn(1L);

        manager.refresh();
        RecommendationHistorySnapshotManager.HistorySnapshot snapshot = manager.currentSnapshot();

        assertThat(snapshot.ready()).isTrue();
        assertThat(snapshot.roundCount()).isEqualTo(2);
        assertThat(snapshot.historyThroughRound()).isEqualTo(2);
        assertThat(snapshot.firstMissingRound()).isNull();
        assertThat(snapshot.version()).isEqualTo(1L);
    }

    @Test
    @DisplayName("중간 회차가 빠지면 firstMissingRound가 그 회차를 가리키고 ready는 false다")
    void refresh_missingMiddleRound_notReady() {
        given(winningNumberRepository.findAllBalls()).willReturn(List.of(
                ballsOnly(1, 1, 2, 3, 4, 5, 6),
                ballsOnly(3, 7, 8, 9, 10, 11, 12)
        ));
        given(historyStateRepository.findById(1)).willReturn(Optional.of(historyState));
        given(historyState.getVersion()).willReturn(1L);

        manager.refresh();
        RecommendationHistorySnapshotManager.HistorySnapshot snapshot = manager.currentSnapshot();

        assertThat(snapshot.ready()).isFalse();
        assertThat(snapshot.firstMissingRound()).isEqualTo(2);
    }

    @Test
    @DisplayName("당첨 조합이 비트마스크로 정확히 인코딩된다")
    void refresh_encodesWinningComboAsMask() {
        given(winningNumberRepository.findAllBalls())
                .willReturn(List.of(ballsOnly(1, 1, 2, 3, 4, 5, 6)));
        given(historyStateRepository.findById(1)).willReturn(Optional.of(historyState));
        given(historyState.getVersion()).willReturn(1L);

        manager.refresh();

        long expectedMask = 0b0000_0000_0000_0000_0000_0000_0000_0000_0000_0011_1111L;
        assertThat(manager.currentSnapshot().masks()).containsExactly(expectedMask);
    }

    @Test
    @DisplayName("currentDatabaseVersion은 저장된 버전을 그대로 반환한다")
    void currentDatabaseVersion_returnsStoredVersion() {
        given(historyStateRepository.findById(1)).willReturn(Optional.of(historyState));
        given(historyState.getVersion()).willReturn(7L);

        assertThat(manager.currentDatabaseVersion()).isEqualTo(7L);
    }

    @Test
    @DisplayName("버전 행이 없으면 0을 반환한다")
    void currentDatabaseVersion_missingRow_returnsZero() {
        given(historyStateRepository.findById(1)).willReturn(Optional.empty());

        assertThat(manager.currentDatabaseVersion()).isZero();
    }

    @Test
    @DisplayName("DB 조회가 실패하면 예외를 그대로 전파한다")
    void currentDatabaseVersion_repositoryThrows_propagates() {
        given(historyStateRepository.findById(1)).willThrow(new RuntimeException("DB down"));

        assertThatThrownBy(() -> manager.currentDatabaseVersion())
                .isInstanceOf(RuntimeException.class)
                .hasMessage("DB down");
    }

    @Test
    @DisplayName("refresh는 로드 시작 시점의 DB 버전을 스냅샷 버전으로 기록한다")
    void refresh_recordsVersionBeforeLoad() {
        given(winningNumberRepository.findAllBalls()).willReturn(List.of());
        given(historyStateRepository.findById(1)).willReturn(Optional.of(historyState));
        given(historyState.getVersion()).willReturn(3L);

        manager.refresh();

        assertThat(manager.currentSnapshot().version()).isEqualTo(3L);
    }

    @Test
    @DisplayName("loadedAt은 주입된 Clock을 기준으로 기록된다")
    void refresh_recordsLoadedAtFromInjectedClock() {
        Instant fixedInstant = Instant.parse("2026-01-01T00:00:00Z");
        Clock fixedClock = Clock.fixed(fixedInstant, ZoneId.of("UTC"));
        RecommendationHistorySnapshotManager fixedClockManager = new RecommendationHistorySnapshotManager(
                winningNumberRepository, historyStateRepository, fixedClock, new SimpleMeterRegistry());
        given(winningNumberRepository.findAllBalls()).willReturn(List.of());
        given(historyStateRepository.findById(1)).willReturn(Optional.of(historyState));
        given(historyState.getVersion()).willReturn(0L);

        fixedClockManager.refresh();

        assertThat(fixedClockManager.currentSnapshot().loadedAt()).isEqualTo(fixedInstant);
    }
}
