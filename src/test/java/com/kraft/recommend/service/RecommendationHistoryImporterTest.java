package com.kraft.recommend.service;

import com.kraft.recommend.domain.RecommendationHistoryState;
import com.kraft.recommend.domain.RecommendationHistoryStateRepository;
import com.kraft.recommend.domain.RecommendationImportException;
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
 * {@link RecommendationHistoryImporter} 검증 로직 테스트. 실제 트리거(V20)는 MariaDB에서만
 * 동작하므로, 여기서는 검증 순서와 "아무것도 쓰지 않음"(전체 실패 시)만 H2로 확인한다.
 */
@DataJpaTest
class RecommendationHistoryImporterTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private WinningDrawRepository winningDrawRepository;

    @Autowired
    private RecommendationHistoryStateRepository stateRepository;

    private RecommendationHistoryImporter importer;

    @BeforeEach
    void setUp() {
        importer = new RecommendationHistoryImporter(winningDrawRepository, stateRepository);
        em.persistAndFlush(RecommendationHistoryState.builder().id(1).version(0L).verifiedThroughRound(0).build());
    }

    @Test
    @DisplayName("빈 DB에 1~3회차를 반영하면 모두 신규 삽입되고 검증 기준 회차가 갱신된다")
    void emptyHistory_importsAllAsInserts() {
        List<ImportedDraw> draws = List.of(
                new ImportedDraw(1, List.of(6, 5, 4, 3, 2, 1)),
                new ImportedDraw(2, List.of(7, 8, 9, 10, 11, 12)),
                new ImportedDraw(3, List.of(13, 14, 15, 16, 17, 18)));

        RecommendationHistoryImporter.Result result = importer.importHistory(draws, 3, "test-source");

        assertThat(result.inserted()).isEqualTo(3);
        assertThat(result.updated()).isZero();
        assertThat(winningDrawRepository.findById(1).orElseThrow().numbers()).isEqualTo(List.of(1, 2, 3, 4, 5, 6));

        RecommendationHistoryState state = stateRepository.findById(1).orElseThrow();
        assertThat(state.getVerifiedThroughRound()).isEqualTo(3);
        assertThat(state.getSourceReference()).isEqualTo("test-source");
    }

    @Test
    @DisplayName("입력에 같은 회차가 두 번 있으면 아무것도 쓰지 않고 거부한다")
    void duplicateRoundInInput_rejectedWithoutWriting() {
        List<ImportedDraw> draws = List.of(
                new ImportedDraw(1, List.of(1, 2, 3, 4, 5, 6)),
                new ImportedDraw(1, List.of(7, 8, 9, 10, 11, 12)));

        assertThatThrownBy(() -> importer.importHistory(draws, 1, "src"))
                .isInstanceOf(RecommendationImportException.class)
                .hasFieldOrPropertyWithValue("reason", "DUPLICATE_ROUND_IN_INPUT");
        assertThat(winningDrawRepository.count()).isZero();
    }

    @Test
    @DisplayName("검증 기준 회차 구간에 누락이 있으면 거부한다")
    void missingRoundInRange_rejected() {
        List<ImportedDraw> draws = List.of(
                new ImportedDraw(1, List.of(1, 2, 3, 4, 5, 6)),
                new ImportedDraw(3, List.of(7, 8, 9, 10, 11, 12))); // 2회 누락

        assertThatThrownBy(() -> importer.importHistory(draws, 3, "src"))
                .isInstanceOf(RecommendationImportException.class)
                .hasFieldOrPropertyWithValue("reason", "MISSING_ROUND");
        assertThat(winningDrawRepository.count()).isZero();
    }

    @Test
    @DisplayName("범위를 벗어나거나 중복된 번호가 있으면 거부한다")
    void invalidNumbers_rejected() {
        List<ImportedDraw> outOfRange = List.of(new ImportedDraw(1, List.of(0, 2, 3, 4, 5, 6)));
        assertThatThrownBy(() -> importer.importHistory(outOfRange, 1, "src"))
                .isInstanceOf(RecommendationImportException.class)
                .hasFieldOrPropertyWithValue("reason", "INVALID_NUMBERS");

        List<ImportedDraw> duplicateNumber = List.of(new ImportedDraw(1, List.of(1, 1, 3, 4, 5, 6)));
        assertThatThrownBy(() -> importer.importHistory(duplicateNumber, 1, "src"))
                .isInstanceOf(RecommendationImportException.class)
                .hasFieldOrPropertyWithValue("reason", "INVALID_NUMBERS");

        assertThat(winningDrawRepository.count()).isZero();
    }

    @Test
    @DisplayName("이미 있는 회차가 입력에도 있으면 정정으로 취급해 UPSERT한다")
    void existingRound_correctedAsUpsert() {
        em.persistAndFlush(WinningDraw.builder()
                .roundNo(1).numbers(List.of(1, 2, 3, 4, 5, 6)).updatedAt(LocalDateTime.now()).build());

        RecommendationHistoryImporter.Result result = importer.importHistory(
                List.of(new ImportedDraw(1, List.of(10, 20, 30, 40, 41, 42))), 1, "correction");

        assertThat(result.inserted()).isZero();
        assertThat(result.updated()).isEqualTo(1);
        assertThat(winningDrawRepository.findById(1).orElseThrow().numbers())
                .isEqualTo(List.of(10, 20, 30, 40, 41, 42));
    }

    @Test
    @DisplayName("회차 데이터 변경 없이 동일 이력을 재검증해도 버전이 오른다")
    void reimportingIdenticalHistory_stillBumpsVersion() {
        List<ImportedDraw> draws = List.of(new ImportedDraw(1, List.of(1, 2, 3, 4, 5, 6)));

        importer.importHistory(draws, 1, "src-v1");
        long versionAfterFirstImport = stateRepository.findById(1).orElseThrow().getVersion();

        importer.importHistory(draws, 1, "src-v2");

        RecommendationHistoryState state = stateRepository.findById(1).orElseThrow();
        assertThat(state.getVersion()).isGreaterThan(versionAfterFirstImport);
        assertThat(state.getSourceReference()).isEqualTo("src-v2");
    }

    @Test
    @DisplayName("큰 배치 중 한 회차라도 규칙을 어기면 배치 전체를 반영하지 않는다")
    void batchWithOneBadRound_rejectsWholeBatch() {
        List<ImportedDraw> draws = List.of(
                new ImportedDraw(1, List.of(1, 2, 3, 4, 5, 6)),
                new ImportedDraw(2, List.of(7, 8, 9, 10, 11, 12)),
                new ImportedDraw(3, List.of(1, 1, 3, 4, 5, 6))); // 3회차만 유효하지 않음

        assertThatThrownBy(() -> importer.importHistory(draws, 3, "src"))
                .isInstanceOf(RecommendationImportException.class);
        assertThat(winningDrawRepository.count()).isZero();
    }
}
