package com.kraft.statistics;

import com.kraft.winningnumber.WinningNumber;
import com.kraft.winningnumber.WinningNumberRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("통계 summary 리빌더 테스트")
class StatisticsSummaryRebuilderTest {

    private static final String REBUILD_LOCK_NAME = "statistics-summary-rebuild";
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Autowired
    private StatisticsSummaryRebuilder summaryRebuilder;

    @Autowired
    private WinningNumberRepository winningNumberRepository;

    @Autowired
    private FrequencySummaryRepository frequencySummaryRepository;

    @Autowired
    private PatternStatsSummaryRepository patternStatsSummaryRepository;

    @Autowired
    private CompanionPairSummaryRepository companionPairSummaryRepository;

    @Autowired
    private LockProvider lockProvider;

    @Autowired
    private StatisticsProjectionStateRepository statisticsProjectionStateRepository;

    private SimpleLock heldLock;

    @BeforeEach
    void setUp() {
        frequencySummaryRepository.deleteAll();
        patternStatsSummaryRepository.deleteAll();
        companionPairSummaryRepository.deleteAll();
        winningNumberRepository.deleteAll();
        statisticsProjectionStateRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        if (heldLock != null) {
            heldLock.unlock();
            heldLock = null;
        }
    }

    @Test
    @DisplayName("H-01: 더 이상 어떤 회차에도 없는 패턴·동반 조합은 삭제되지 않고 count=0 행으로 남는다")
    void rebuildAllSummaries_keepsUnobservedKeysAsZeroInsteadOfDeleting() {
        // 회차 1: 1,3,5,7,9,11 (홀수 6개 패턴, 1-3 동반쌍 포함)
        winningNumberRepository.save(round(1, 1, 3, 5, 7, 9, 11, 2));
        summaryRebuilder.rebuildAllSummaries();

        assertThat(patternStatsSummaryRepository
                .findByStatTypeAndBucketKey(WinningStatisticsCacheService.TYPE_ODD_COUNT, "6"))
                .isPresent();
        assertThat(companionPairSummaryRepository.findByBallAAndBallB(1, 3)).isPresent();

        // 회차 1을 지우고 홀수 3개/동반쌍이 다른 회차 2만 남긴다 — "홀수 6개" 버킷과 1-3 동반쌍은
        // 더 이상 어떤 회차에도 존재하지 않지만, 완전 도메인 계약상 삭제되지 않고 count=0으로
        // 남아야 한다(예전에는 여기서 행 자체가 삭제됐고, 그것이 읽기 측 완전성 검사와
        // 모순돼 재계산 루프를 유발했다).
        winningNumberRepository.deleteAll();
        winningNumberRepository.save(round(2, 1, 10, 20, 30, 40, 44, 8));
        summaryRebuilder.rebuildAllSummaries();

        assertThat(patternStatsSummaryRepository
                .findByStatTypeAndBucketKey(WinningStatisticsCacheService.TYPE_ODD_COUNT, "6"))
                .hasValueSatisfying(row -> assertThat(row.getCountVal()).isZero());
        assertThat(companionPairSummaryRepository.findByBallAAndBallB(1, 3))
                .hasValueSatisfying(row -> assertThat(row.getCoCount()).isZero());
        assertThat(companionPairSummaryRepository.findByBallAAndBallB(1, 10))
                .hasValueSatisfying(row -> assertThat(row.getCoCount()).isEqualTo(1));
    }

    @Test
    @DisplayName("H-01: 원본 회차 데이터가 없어도 완전 도메인을 0으로 채운 프로젝션을 남긴다")
    void rebuildAllSummaries_producesCompleteDomainForEmptySource() {
        summaryRebuilder.rebuildAllSummaries();

        List<FrequencySummary> frequencies = frequencySummaryRepository.findAll();
        assertThat(frequencies).hasSize(45);
        assertThat(frequencies).allSatisfy(row -> assertThat(row.getFrequency()).isZero());

        List<PatternStatsSummary> patterns = patternStatsSummaryRepository.findAll();
        assertThat(patterns).hasSize(19); // 7(odd) + 7(high) + 5(sum bucket)
        assertThat(patterns).allSatisfy(row -> assertThat(row.getCountVal()).isZero());

        List<CompanionPairSummary> pairs = companionPairSummaryRepository.findAll();
        assertThat(pairs).hasSize(990);
        assertThat(pairs).allSatisfy(row -> assertThat(row.getCoCount()).isZero());

        StatisticsProjectionState state = statisticsProjectionStateRepository
                .findById(StatisticsProjectionState.SINGLETON_ID).orElseThrow();
        assertThat(state.getLastProcessedRound()).isZero();
        assertThat(state.getSourceRowCount()).isZero();
    }

    @Test
    @DisplayName("H-01: 원본이 비워지면 이전에 채워져 있던 summary도 완전 도메인·전부 0으로 덮어써진다")
    void rebuildAllSummaries_emptySource_overwritesExistingSummariesWithCompleteZeroDomain() {
        winningNumberRepository.save(round(1, 1, 2, 3, 4, 5, 6, 7));
        summaryRebuilder.rebuildAllSummaries();
        assertThat(frequencySummaryRepository.findAll()).hasSize(45);
        assertThat(patternStatsSummaryRepository.findAll()).hasSize(19);
        assertThat(companionPairSummaryRepository.findAll()).hasSize(990);

        // 원본이 전부 삭제된 뒤(예: 복원/초기화) 다시 재계산하면, 행이 사라지는 게 아니라
        // 완전 도메인이 그대로 유지되되 전부 count=0으로 덮어써져야 한다.
        winningNumberRepository.deleteAll();
        summaryRebuilder.rebuildAllSummaries();

        assertThat(frequencySummaryRepository.findAll())
                .hasSize(45)
                .allSatisfy(row -> assertThat(row.getFrequency()).isZero());
        assertThat(patternStatsSummaryRepository.findAll())
                .hasSize(19)
                .allSatisfy(row -> assertThat(row.getCountVal()).isZero());
        assertThat(companionPairSummaryRepository.findAll())
                .hasSize(990)
                .allSatisfy(row -> assertThat(row.getCoCount()).isZero());
    }

    @Test
    @DisplayName("H-01: 1라운드만 있어도 완전 도메인이 만들어지고 관측된 조합만 count>0이다")
    void rebuildAllSummaries_producesCompleteDomainForSingleRound() {
        winningNumberRepository.save(round(1, 1, 3, 5, 7, 9, 11, 2));

        summaryRebuilder.rebuildAllSummaries();

        List<FrequencySummary> frequencies = frequencySummaryRepository.findAll();
        assertThat(frequencies).hasSize(45);
        assertThat(frequencies.stream().filter(row -> row.getFrequency() > 0)).hasSize(6);

        List<PatternStatsSummary> patterns = patternStatsSummaryRepository.findAll();
        assertThat(patterns).hasSize(19);
        assertThat(patterns.stream().filter(row -> row.getCountVal() > 0)).hasSize(3); // odd 1개, high 1개, sum 1개

        List<CompanionPairSummary> pairs = companionPairSummaryRepository.findAll();
        assertThat(pairs).hasSize(990);
        assertThat(pairs.stream().filter(row -> row.getCoCount() > 0)).hasSize(15); // 6C2
    }

    @Test
    @DisplayName("다른 인스턴스가 락을 보유 중이면 재생성을 건너뛰고 기존 데이터를 그대로 둔다")
    void rebuildAllSummaries_skipsWhenLockAlreadyHeld() {
        winningNumberRepository.save(round(1, 1, 2, 3, 4, 5, 6, 7));

        Optional<SimpleLock> lock = lockProvider.lock(new LockConfiguration(
                Clock.system(KST).instant(), REBUILD_LOCK_NAME, Duration.ofMinutes(10), Duration.ZERO));
        assertThat(lock).isPresent();
        heldLock = lock.get();

        StatisticsSummaryRebuilder.RebuildOutcome outcome = summaryRebuilder.rebuildAllSummaries();

        // BE-STAT-01(docs/improvement.md): lock 경합은 이제 호출자가 구분할 수 있는 반환값이다.
        assertThat(outcome).isEqualTo(StatisticsSummaryRebuilder.RebuildOutcome.SKIPPED);
        assertThat(frequencySummaryRepository.findAll()).isEmpty();
        assertThat(patternStatsSummaryRepository.findAll()).isEmpty();
        assertThat(companionPairSummaryRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("KB-16: 재생성이 성공하면 프로젝션 상태(버전·마지막 회차·소스 행 수)를 원자적으로 남긴다")
    void rebuildAllSummaries_recordsProjectionStateOnSuccess() {
        winningNumberRepository.save(round(1, 1, 2, 3, 4, 5, 6, 7));
        winningNumberRepository.save(round(2, 2, 3, 4, 5, 6, 7, 8));

        summaryRebuilder.rebuildAllSummaries();

        StatisticsProjectionState state = statisticsProjectionStateRepository
                .findById(StatisticsProjectionState.SINGLETON_ID).orElseThrow();
        assertThat(state.getVersion()).isEqualTo(1);
        assertThat(state.getLastProcessedRound()).isEqualTo(2);
        assertThat(state.getSourceRowCount()).isEqualTo(2);
        assertThat(state.getSucceededAt()).isNotNull();

        winningNumberRepository.save(round(3, 3, 4, 5, 6, 7, 8, 9));
        summaryRebuilder.rebuildAllSummaries();

        StatisticsProjectionState updated = statisticsProjectionStateRepository
                .findById(StatisticsProjectionState.SINGLETON_ID).orElseThrow();
        assertThat(updated.getVersion()).isEqualTo(2);
        assertThat(updated.getLastProcessedRound()).isEqualTo(3);
        assertThat(updated.getSourceRowCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("KB-16: 원본이 비어 재생성하면 프로젝션 상태도 0으로 원자적으로 남긴다")
    void rebuildAllSummaries_emptySource_recordsZeroedProjectionState() {
        winningNumberRepository.save(round(1, 1, 2, 3, 4, 5, 6, 7));
        summaryRebuilder.rebuildAllSummaries();
        winningNumberRepository.deleteAll();

        summaryRebuilder.rebuildAllSummaries();

        StatisticsProjectionState state = statisticsProjectionStateRepository
                .findById(StatisticsProjectionState.SINGLETON_ID).orElseThrow();
        assertThat(state.getVersion()).isEqualTo(2);
        assertThat(state.getLastProcessedRound()).isEqualTo(0);
        assertThat(state.getSourceRowCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("KB-16: 다른 인스턴스가 락을 보유 중이면 프로젝션 상태도 갱신하지 않는다")
    void rebuildAllSummaries_skipsProjectionStateWhenLockAlreadyHeld() {
        winningNumberRepository.save(round(1, 1, 2, 3, 4, 5, 6, 7));

        Optional<SimpleLock> lock = lockProvider.lock(new LockConfiguration(
                Clock.system(KST).instant(), REBUILD_LOCK_NAME, Duration.ofMinutes(10), Duration.ZERO));
        assertThat(lock).isPresent();
        heldLock = lock.get();

        summaryRebuilder.rebuildAllSummaries();

        assertThat(statisticsProjectionStateRepository.findById(StatisticsProjectionState.SINGLETON_ID))
                .isEmpty();
    }

    private WinningNumber round(int r, int n1, int n2, int n3, int n4, int n5, int n6, int bonus) {
        return com.kraft.winningnumber.WinningNumberTestFactory.create(r, LocalDate.of(2026, 1, r),
                n1, n2, n3, n4, n5, n6, bonus,
                1_000_000_000L, 0L, 0, 0L, 0L,
                OffsetDateTime.now(Clock.system(KST)));
    }
}
