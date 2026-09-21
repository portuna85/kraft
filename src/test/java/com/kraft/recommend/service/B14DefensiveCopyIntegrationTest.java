package com.kraft.recommend.service;

import com.kraft.recommend.domain.LottoNumbers;
import com.kraft.recommend.domain.RecommendationHistorySnapshot;
import com.kraft.recommend.domain.RecommendationHistoryState;
import com.kraft.recommend.domain.RecommendationHistoryStateRepository;
import com.kraft.recommend.domain.WinningDraw;
import com.kraft.recommend.domain.WinningDrawRepository;
import com.kraft.recommend.dto.RecommendRequestDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * B14/P3(방어적 복사)가 각 타입의 생성자 안에서 끝나는 게 아니라, 실제 저장·조회·검증·생성
 * 경로를 통째로 거치는 동안에도 지켜지는지 확인한다. 각 타입은 이미 단위 테스트가 있지만
 * ({@link LottoNumbers}, {@link RecommendationHistorySnapshot}, {@link ImportedDraw},
 * {@link NormalizedRecommendationRequest}), 여기서는 Mock 없이 실제 JPA 저장소와 실제 협력
 * 객체(Importer → Provider → Validator → CandidateGenerator)를 그대로 이어 붙여 두 가지를
 * 검증한다: (1) 호출부가 원본을 나중에 바꿔도 이미 저장·캐시된 값은 영향받지 않는지,
 * (2) 돌려받은 값을 직접 바꾸려 하면 실제로 막히는지.
 */
@DataJpaTest
class B14DefensiveCopyIntegrationTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private WinningDrawRepository winningDrawRepository;

    @Autowired
    private RecommendationHistoryStateRepository stateRepository;

    private RecommendationHistoryImporter importer;
    private RecommendationHistoryProvider provider;

    private final RecommendationRequestValidator validator = new RecommendationRequestValidator();
    private final RecommendationCandidateGenerator generator =
            new RecommendationCandidateGenerator(() -> new Random(42L));

    @BeforeEach
    void setUp() {
        importer = new RecommendationHistoryImporter(winningDrawRepository, stateRepository);
        provider = new RecommendationHistoryProvider(winningDrawRepository, stateRepository);
    }

    @Test
    @DisplayName("ImportedDraw 생성 뒤 호출부가 원본 목록을 바꿔도 실제로 저장된 회차 번호는 영향받지 않는다")
    void importedDraw_survivesSourceMutationAfterConstruction() {
        em.persistAndFlush(RecommendationHistoryState.builder().id(1).version(0L).verifiedThroughRound(0).build());
        List<Integer> mutableSource = new ArrayList<>(List.of(1, 2, 3, 4, 5, 6));
        ImportedDraw draw = new ImportedDraw(1, mutableSource);

        // 생성자를 통과한 뒤 호출부가 원본을 바꾼다 — 캐시된 List를 재사용하다 실수로 건드리는
        // 상황을 흉내낸다. 방어적 복사가 없다면 이 변조가 그대로 DB에 반영됐을 것이다.
        mutableSource.set(0, 99);
        mutableSource.add(7);

        importer.importHistory(List.of(draw), 1, "src");

        assertThat(winningDrawRepository.findById(1).orElseThrow().numbers())
                .isEqualTo(List.of(1, 2, 3, 4, 5, 6));
    }

    @Test
    @DisplayName("실제 DB에서 읽은 스냅샷의 winningMasks는 바깥에서 바꿀 수 없고, 캐시된 다음 조회에도 영향이 없다")
    void historySnapshot_winningMasksIsImmutableAcrossRealCache() {
        seedDraw(1, List.of(1, 2, 3, 4, 5, 6));
        em.persistAndFlush(RecommendationHistoryState.builder()
                .id(1).version(1L).verifiedThroughRound(1).build());

        RecommendationHistorySnapshot snapshot = provider.currentReadySnapshot();

        assertThatThrownBy(() -> snapshot.winningMasks().add(999L))
                .isInstanceOf(UnsupportedOperationException.class);

        // version이 그대로면 provider가 같은 캐시 인스턴스를 돌려준다(refresh:66-69) — 방금
        // 던진 변조 시도가 내부 Set을 실제로 건드렸다면 여기서 크기가 늘어나 있을 것이다.
        RecommendationHistorySnapshot cachedAgain = provider.currentReadySnapshot();
        assertThat(cachedAgain.winningMasks()).hasSize(1);
    }

    @Test
    @DisplayName("검증된 요청의 고정·제외 번호 집합은 바꿀 수 없고, 후보 생성기가 실제로 반환한 조합도 마찬가지다")
    void normalizedRequestAndGeneratedCandidates_areBothImmutable() {
        RecommendRequestDto dto = new RecommendRequestDto(
                3, "random", new ArrayList<>(List.of(1, 2)), new ArrayList<>(List.of(3, 4, 5)));

        NormalizedRecommendationRequest request = validator.validate(dto);

        assertThatThrownBy(() -> request.lockedNumbers().add(10))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> request.excludedNumbers().add(10))
                .isInstanceOf(UnsupportedOperationException.class);

        RecommendationHistorySnapshot emptyHistory =
                new RecommendationHistorySnapshot(Set.of(), 1, 1, 1, 1L, Instant.now());
        List<LottoNumbers> generated = generator.generateRandom(request, emptyHistory);

        assertThat(generated).isNotEmpty();
        for (LottoNumbers combo : generated) {
            assertThatThrownBy(() -> combo.numbers().add(1))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    private void seedDraw(int round, List<Integer> numbers) {
        em.persistAndFlush(WinningDraw.builder()
                .roundNo(round).numbers(numbers).updatedAt(LocalDateTime.now()).build());
    }
}
