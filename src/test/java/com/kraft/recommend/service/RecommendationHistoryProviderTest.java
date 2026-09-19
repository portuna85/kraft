package com.kraft.recommend.service;

import com.kraft.recommend.domain.RecommendationHistoryNotReadyException;
import com.kraft.recommend.domain.RecommendationHistorySnapshot;
import com.kraft.recommend.domain.RecommendationHistoryState;
import com.kraft.recommend.domain.RecommendationHistoryStateRepository;
import com.kraft.recommend.domain.WinningDraw;
import com.kraft.recommend.domain.WinningDrawRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link RecommendationHistoryProvider} 통합 테스트. 실제 트리거는 MariaDB에서만 동작하므로
 * (V20 마이그레이션), 여기서는 {@link RecommendationHistoryState#bumpVersionForTesting()}으로
 * 트리거의 결과(version 증가)만 흉내낸다.
 */
@DataJpaTest
class RecommendationHistoryProviderTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private WinningDrawRepository winningDrawRepository;

    @Autowired
    private RecommendationHistoryStateRepository stateRepository;

    private RecommendationHistoryProvider provider;

    @BeforeEach
    void setUp() {
        provider = new RecommendationHistoryProvider(winningDrawRepository, stateRepository);
    }

    @Test
    @DisplayName("이력 상태 행이 없으면 미준비로 503 예외를 던진다")
    void noStateRow_isNotReady() {
        assertThatThrownBy(() -> provider.currentReadySnapshot())
                .isInstanceOf(RecommendationHistoryNotReadyException.class);
    }

    @Test
    @DisplayName("1회부터 검증 기준 회차까지 누락 없이 있으면 준비 완료로 스냅샷을 반환한다")
    void continuousHistory_isReady() {
        seedDraws(1, 2, 3);
        em.persistAndFlush(RecommendationHistoryState.builder()
                .id(1).version(3L).verifiedThroughRound(3).build());

        RecommendationHistorySnapshot snapshot = provider.currentReadySnapshot();

        assertThat(snapshot.isReady()).isTrue();
        assertThat(snapshot.roundCount()).isEqualTo(3);
        assertThat(snapshot.verifiedThroughRound()).isEqualTo(3);
    }

    @Test
    @DisplayName("중간 회차가 누락되면 준비되지 않은 것으로 본다")
    void missingRound_isNotReady() {
        seedDraws(1, 3); // 2회 누락
        em.persistAndFlush(RecommendationHistoryState.builder()
                .id(1).version(2L).verifiedThroughRound(3).build());

        assertThatThrownBy(() -> provider.currentReadySnapshot())
                .isInstanceOf(RecommendationHistoryNotReadyException.class);
    }

    @Test
    @DisplayName("생성 도중 버전이 바뀌면(정정·삭제 포함) verifyUnchanged가 예외를 던진다")
    void versionChangedDuringGeneration_throws() {
        seedDraws(1, 2, 3);
        RecommendationHistoryState state = em.persistAndFlush(RecommendationHistoryState.builder()
                .id(1).version(3L).verifiedThroughRound(3).build());

        RecommendationHistorySnapshot snapshot = provider.currentReadySnapshot();

        state.bumpVersionForTesting();
        em.persistAndFlush(state);

        assertThatThrownBy(() -> provider.verifyUnchanged(snapshot))
                .isInstanceOf(RecommendationHistoryNotReadyException.class);
    }

    private void seedDraws(int... rounds) {
        for (int round : rounds) {
            em.persist(WinningDraw.builder()
                    .roundNo(round)
                    .numbers(List.of(1, 2, 3, 4, 5, round + 10))
                    .updatedAt(LocalDateTime.now())
                    .build());
        }
        em.flush();
    }
}
