package com.kraft.recommend;

import com.kraft.common.error.ApiException;
import com.kraft.common.lotto.LottoNumberCodec;
import com.kraft.winningnumber.WinningBallsOnly;
import com.kraft.winningnumber.WinningNumberRepository;
import com.kraft.winningnumber.WinningNumbersCollectedEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("로또 번호 추천 서비스 단위 테스트")
class LottoRecommendationServiceTest {

    @Mock
    private WinningNumberRepository winningNumberRepository;

    @Mock
    private CombinationScorer combinationScorer;

    @Mock
    private RecommendationSetHistoryService recommendationSetHistoryService;

    @Mock
    private RecommendationHistoryStateRepository historyStateRepository;

    @Mock
    private RecommendationHistoryState historyState;

    private LottoNumberCodec lottoNumberCodec;
    private LottoRecommendationService service;
    private SimpleMeterRegistry meterRegistry;

    private static final OffsetDateTime NOW =
            OffsetDateTime.now(ZoneId.of("Asia/Seoul"));

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
        lottoNumberCodec = new LottoNumberCodec();
        // 기본값은 회차 1만 등록된 "이력 준비 완료" 상태다(1회부터 연속, 누락 없음).
        // 이력이 비어 있으면 R2 fail-closed 정책상 recommend()가 항상 503을 던지므로,
        // 그 경로를 검증하는 InvariantEnforcement 테스트에서만 별도로 List.of()를 재설정한다.
        given(winningNumberRepository.findAllBalls())
                .willReturn(List.of(ballsOnly(1, 1, 2, 3, 4, 5, 6)));
        given(historyStateRepository.findById(1)).willReturn(Optional.of(historyState));
        given(historyState.getVersion()).willReturn(1L);
        meterRegistry = new SimpleMeterRegistry();
        service = new LottoRecommendationService(lottoNumberCodec, winningNumberRepository, combinationScorer,
                new BalancedScorer(), recommendationSetHistoryService, historyStateRepository,
                Clock.systemUTC(), meterRegistry);
        service.loadHistoricalCombinations();
    }

    // ── 기본 추천 동작 ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("기본 추천")
    class BasicRecommend {

        @Test
        @DisplayName("요청한 개수만큼 조합을 반환한다")
        void recommend_returnsRequestedCount() {
            RecommendNumbersRequest request = new RecommendNumbersRequest(5, null, false, null, null, null);

            RecommendNumbersResponse response = service.recommend(request, null);

            assertThat(response.recommendations()).hasSize(5);
        }

        @Test
        @DisplayName("요청이 없으면 기본값 1개를 반환한다")
        void recommend_nullRequest_returnsOneCombo() {
            RecommendNumbersResponse response = service.recommend(null, null);

            assertThat(response.recommendations()).hasSize(1);
        }

        @Test
        @DisplayName("각 조합은 정확히 6개의 번호를 포함한다")
        void recommend_eachComboHasSixNumbers() {
            RecommendNumbersRequest request = new RecommendNumbersRequest(3, null, false, null, null, null);

            service.recommend(request, null).recommendations()
                    .forEach(combo -> assertThat(combo).hasSize(6));
        }

        @Test
        @DisplayName("모든 번호는 1~45 범위 내에 있다")
        void recommend_allNumbersInValidRange() {
            RecommendNumbersRequest request = new RecommendNumbersRequest(3, null, false, null, null, null);

            service.recommend(request, null).recommendations()
                    .forEach(combo ->
                            assertThat(combo).allMatch(n -> n >= 1 && n <= 45));
        }

        @Test
        @DisplayName("각 조합 내 번호는 중복 없이 오름차순으로 정렬된다")
        void recommend_numbersAreSortedAndUnique() {
            RecommendNumbersRequest request = new RecommendNumbersRequest(3, null, false, null, null, null);

            service.recommend(request, null).recommendations().forEach(combo -> {
                assertThat(combo).doesNotHaveDuplicates();
                assertThat(combo).isSorted();
            });
        }

        @Test
        @DisplayName("당첨금 최대화 모드가 아니면 점수 계산기를 호출하지 않는다")
        void recommend_notMaximizePrize_scorerNotCalled() {
            service.recommend(new RecommendNumbersRequest(3, null, false, null, null, null), null);

            verify(combinationScorer, never()).score(anyList());
        }
    }

    @Nested
    @DisplayName("이력 버전 일관성")
    class HistoryVersionConsistency {

        @Test
        @DisplayName("DB 이력 버전이 스냅샷보다 새로우면 현재 요청은 503으로 닫고 스냅샷을 갱신한다")
        void recommend_whenHistoryVersionIsStale_failsClosedAndRefreshes() {
            given(historyState.getVersion()).willReturn(2L);

            assertThatThrownBy(() -> service.recommend(new RecommendNumbersRequest(1, null, null, null, null, null), null))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> {
                        ApiException apiEx = (ApiException) ex;
                        assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                        assertThat(apiEx.getCode()).isEqualTo("RECOMMENDATION_HISTORY_NOT_READY");
                    });

            verify(winningNumberRepository, atLeast(2)).findAllBalls();
            assertThat(service.historyStatus().ready()).isTrue();
            assertThat(service.historyStatus().snapshotVersion()).isEqualTo(2L);
        }

        @Test
        @DisplayName("다른 인스턴스가 신규 회차를 반영하면 stale 인스턴스는 성공하지 않고 스냅샷을 갱신한다")
        void recommend_whenAnotherInstanceAdvancesHistory_failsClosedThenRefreshes() {
            AtomicLong sharedVersion = new AtomicLong(1L);
            given(historyState.getVersion()).willAnswer(invocation -> sharedVersion.get());

            LottoRecommendationService otherInstance = new LottoRecommendationService(
                    lottoNumberCodec, winningNumberRepository, combinationScorer, new BalancedScorer(),
                    recommendationSetHistoryService, historyStateRepository, Clock.systemUTC(),
                    new SimpleMeterRegistry());
            otherInstance.loadHistoricalCombinations();

            given(winningNumberRepository.findAllBalls()).willReturn(List.of(
                    ballsOnly(1, 1, 2, 3, 4, 5, 6),
                    ballsOnly(2, 7, 8, 9, 10, 11, 12)));
            sharedVersion.set(2L);
            service.refreshHistoryCache();

            assertThatThrownBy(() -> otherInstance.recommend(
                    new RecommendNumbersRequest(1, null, null, null, null, null), null))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> assertThat(((ApiException) ex).getCode())
                            .isEqualTo("RECOMMENDATION_HISTORY_NOT_READY"));
            assertThat(otherInstance.historyStatus().ready()).isTrue();
            assertThat(otherInstance.historyStatus().snapshotVersion()).isEqualTo(2L);
        }
    }

    // ── 제외 번호 ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("제외 번호 처리")
    class ExcludedNumbers {

        @RepeatedTest(10)
        @DisplayName("제외 번호는 추천 조합에 포함되지 않는다")
        void recommend_excludedNumbersAbsentFromResult() {
            List<Integer> excluded = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
            RecommendNumbersRequest request = new RecommendNumbersRequest(3, excluded, false, null, null, null);

            service.recommend(request, null).recommendations()
                    .forEach(combo ->
                            assertThat(combo).doesNotContainAnyElementsOf(excluded));
        }

        @Test
        @DisplayName("후보가 6개 미만이 되도록 제외하면 과도한 제외 번호 예외가 발생한다")
        void recommend_tooManyExclusions_throwsApiException() {
            List<Integer> excluded = List.of(
                    1, 2, 3, 4, 5, 6, 7, 8, 9, 10,
                    11, 12, 13, 14, 15, 16, 17, 18, 19, 20,
                    21, 22, 23, 24, 25, 26, 27, 28, 29, 30,
                    31, 32, 33, 34, 35, 36, 37, 38, 39, 40
            ); // 40개 제외 → 후보 5개

            assertThatThrownBy(() ->
                    service.recommend(new RecommendNumbersRequest(1, excluded, false, null, null, null), null)
            )
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> {
                        ApiException apiEx = (ApiException) ex;
                        assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                        assertThat(apiEx.getCode()).isEqualTo("TOO_MANY_EXCLUSIONS");
                    });
        }

        @Test
        @DisplayName("후보 6개에서 2세트를 요청하면 조합 부족 예외가 발생한다")
        void recommend_countExceedsPossibleCombinations_throwsApiException() {
            // 39개 제외 → 후보 6개 → C(6,6)=1가지뿐
            List<Integer> excluded = List.of(
                    1, 2, 3, 4, 5, 6, 7, 8, 9, 10,
                    11, 12, 13, 14, 15, 16, 17, 18, 19, 20,
                    21, 22, 23, 24, 25, 26, 27, 28, 29, 30,
                    31, 32, 33, 34, 35, 36, 37, 38, 39
            );

            assertThatThrownBy(() ->
                    service.recommend(new RecommendNumbersRequest(2, excluded, false, null, null, null), null)
            )
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> {
                        ApiException apiEx = (ApiException) ex;
                        assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                        assertThat(apiEx.getCode()).isEqualTo("INSUFFICIENT_UNIQUE_COMBINATIONS");
                    });
        }
    }

    // ── 역대 당첨 조합 제외 ───────────────────────────────────────────────────

    @Nested
    @DisplayName("역대 1등 당첨 조합 제외")
    class HistoricalExclusion {

        @RepeatedTest(30)
        @DisplayName("역대 당첨 조합은 추천 결과에 절대 포함되지 않는다")
        void recommend_historicalCombinationsNeverReturned() {
            // 1~43번 조합은 모두 역대 당첨으로 등록 (극단 시나리오)
            // 실제로는 1,100개 수준이므로 특정 조합만 등록
            given(winningNumberRepository.findAllBalls()).willReturn(List.of(ballsOnly(1, 1, 2, 3, 4, 5, 6)));
            service.loadHistoricalCombinations();

            service.recommend(new RecommendNumbersRequest(5, null, false, null, null, null), null)
                    .recommendations()
                    .forEach(combo ->
                            assertThat(combo).isNotEqualTo(List.of(1, 2, 3, 4, 5, 6)));
        }

        @Test
        @DisplayName("역대 당첨 조합 로드 후 변경된 새 회차 이벤트를 받으면 갱신된다")
        void onCollected_dataChanged_refreshesHistoricalCombinations() {
            // 초기: setUp 기본값(회차 1)만 등록된 상태에서도 정상 추천된다
            assertThat(service.recommend(new RecommendNumbersRequest(1, null, false, null, null, null), null)
                    .recommendations()).hasSize(1);

            // 새 회차 수집 — 기존 회차 1 위에 이어지는 연속 회차(2)이므로 이력은 계속 ready 상태를 유지한다.
            given(winningNumberRepository.findAllBalls())
                    .willReturn(List.of(ballsOnly(1, 1, 2, 3, 4, 5, 6), ballsOnly(2, 3, 11, 19, 28, 34, 42)));

            service.onCollected(new WinningNumbersCollectedEvent(2, true));

            // 갱신 후 해당 조합 제외 확인 (반복 시도로 확률적 검증)
            for (int i = 0; i < 20; i++) {
                service.recommend(new RecommendNumbersRequest(1, null, false, null, null, null), null)
                        .recommendations()
                        .forEach(combo ->
                                assertThat(combo).isNotEqualTo(List.of(3, 11, 19, 28, 34, 42)));
            }
        }

        @Test
        @DisplayName("변경 없는 새 회차 이벤트를 받으면 저장소를 재조회하지 않는다")
        void onCollected_dataNotChanged_doesNotRefresh() {
            service.onCollected(new WinningNumbersCollectedEvent(1200, false));

            // loadHistoricalCombinations()에서 1회 호출되었으므로 총 1회
            verify(winningNumberRepository, org.mockito.Mockito.times(1)).findAllBalls();
        }

        @Test
        @DisplayName("역대 조합 판정은 45비트 비트마스크 기반이라 입력 순서와 무관하다")
        void isHistoricalFirstPrizeCombination_orderIndependent() {
            given(winningNumberRepository.findAllBalls())
                    .willReturn(List.of(ballsOnly(1, 3, 11, 19, 28, 34, 42)));
            service.loadHistoricalCombinations();

            assertThat(service.isHistoricalFirstPrizeCombination(List.of(3, 11, 19, 28, 34, 42))).isTrue();
            // 정렬되지 않은 순서로 넣어도(normalize가 정렬) 동일하게 판정되어야 한다
            assertThat(service.isHistoricalFirstPrizeCombination(List.of(42, 3, 34, 11, 28, 19))).isTrue();
        }

        @Test
        @DisplayName("비트마스크가 다른 조합끼리는 서로 다른 조합으로 오판되지 않는다")
        void isHistoricalFirstPrizeCombination_distinctCombosDoNotCollide() {
            given(winningNumberRepository.findAllBalls())
                    .willReturn(List.of(ballsOnly(1, 1, 2, 3, 4, 5, 6)));
            service.loadHistoricalCombinations();

            assertThat(service.isHistoricalFirstPrizeCombination(List.of(1, 2, 3, 4, 5, 6))).isTrue();
            assertThat(service.isHistoricalFirstPrizeCombination(List.of(7, 8, 9, 10, 11, 12))).isFalse();
            assertThat(service.isHistoricalFirstPrizeCombination(List.of(2, 3, 4, 5, 6, 7))).isFalse();
        }
    }

    // ── R2: 이력이 준비되지 않았으면 핵심 약속을 하지 않는다(fail-closed) ───────────
    // DB가 완전히 비어 있으면(배제 이력이 전혀 없음) 과거 1등 배제를 "확실히" 보장할 수
    // 없으므로, 조용히 정상 응답하는 대신 503으로 거절한다. 중간 누락 회차는 gauge로만
    // 노출한다 — 실 운영 DB는 1회부터 전체 이력을 수집하므로 드문 상황이고, 하드 게이트로
    // 걸면 부분 데이터만 시딩하는 통합 테스트 픽스처까지 전부 막혀 과도하다.

    @Nested
    @DisplayName("R2: 이력 미준비 상태에서 fail-closed")
    class HistoryReadiness {

        @Test
        @DisplayName("이력 DB가 비어 있으면 추천 요청은 503 RECOMMENDATION_HISTORY_NOT_READY로 거절된다")
        void recommend_emptyHistory_throwsServiceUnavailable() {
            given(winningNumberRepository.findAllBalls()).willReturn(List.of());
            service.loadHistoricalCombinations();

            assertThatThrownBy(() -> service.recommend(new RecommendNumbersRequest(1, null, false, null, null, null), null))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> {
                        ApiException apiEx = (ApiException) ex;
                        assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                        assertThat(apiEx.getCode()).isEqualTo("RECOMMENDATION_HISTORY_NOT_READY");
                    });
        }

        @Test
        @DisplayName("1회부터 최신 회차까지 중간에 누락된 회차가 있으면 추천은 503으로 거절되지만 데이터는 존재함이 gauge에 반영된다")
        void recommend_gapInHistory_isRejectedAsNotReady() {
            // 회차 1은 있지만 2가 누락되고 3부터 다시 있음 — firstMissingRound=2
            given(winningNumberRepository.findAllBalls()).willReturn(List.of(
                    ballsOnly(1, 1, 2, 3, 4, 5, 6),
                    ballsOnly(3, 7, 8, 9, 10, 11, 12)
            ));
            service.loadHistoricalCombinations();

            assertThatThrownBy(() -> service.recommend(new RecommendNumbersRequest(1, null, false, null, null, null), null))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> {
                        ApiException apiEx = (ApiException) ex;
                        assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                        assertThat(apiEx.getCode()).isEqualTo("RECOMMENDATION_HISTORY_NOT_READY");
                    });
            assertThat(meterRegistry.get("kraft_lotto_first_missing_round").gauge().value()).isEqualTo(2d);
            assertThat(meterRegistry.get("kraft_lotto_history_ready").gauge().value()).isEqualTo(0d);
            assertThat(meterRegistry.get("kraft_lotto_history_data_present").gauge().value()).isEqualTo(1d);
        }

        @Test
        @DisplayName("1회부터 최신 회차까지 빈틈이 없으면 추천이 정상 제공되고 준비 상태 gauge가 모두 1을 가리킨다")
        void recommend_continuousHistory_isReadyAndServes() {
            given(winningNumberRepository.findAllBalls()).willReturn(List.of(
                    ballsOnly(1, 1, 2, 3, 4, 5, 6),
                    ballsOnly(2, 7, 8, 9, 10, 11, 12)
            ));
            service.loadHistoricalCombinations();

            assertThat(service.recommend(new RecommendNumbersRequest(1, null, false, null, null, null), null)
                    .recommendations()).hasSize(1);
            assertThat(meterRegistry.get("kraft_lotto_first_missing_round").gauge().value()).isEqualTo(0d);
            assertThat(meterRegistry.get("kraft_lotto_history_ready").gauge().value()).isEqualTo(1d);
            assertThat(meterRegistry.get("kraft_lotto_history_data_present").gauge().value()).isEqualTo(1d);
            assertThat(meterRegistry.get("kraft_lotto_history_db_version").gauge().value())
                    .isEqualTo(historyState.getVersion());
            assertThat(meterRegistry.get("kraft_lotto_history_snapshot_age_seconds").gauge().value())
                    .isGreaterThanOrEqualTo(0d);
            assertThat(meterRegistry.get("kraft_lotto_history_refresh_duration_seconds").timer().count())
                    .isGreaterThanOrEqualTo(1L);
        }

        @Test
        @DisplayName("이력 DB가 비어 있으면 준비 상태·데이터 존재 gauge가 모두 0을 가리킨다")
        void historyReadyGauge_reflectsEmptyHistory() {
            given(winningNumberRepository.findAllBalls()).willReturn(List.of());
            service.loadHistoricalCombinations();

            assertThat(meterRegistry.get("kraft_lotto_history_ready").gauge().value()).isEqualTo(0d);
            assertThat(meterRegistry.get("kraft_lotto_history_round_count").gauge().value()).isEqualTo(0d);
            assertThat(meterRegistry.get("kraft_lotto_history_data_present").gauge().value()).isEqualTo(0d);
        }

        @Test
        @DisplayName("이력이 비어 있어도 특정 조합의 과거 1등 여부 조회 자체는 계속 동작한다")
        void isHistoricalFirstPrizeCombination_worksEvenWhenHistoryEmpty() {
            given(winningNumberRepository.findAllBalls()).willReturn(List.of());
            service.loadHistoricalCombinations();

            assertThat(service.isHistoricalFirstPrizeCombination(List.of(1, 2, 3, 4, 5, 6))).isFalse();
        }

        @Test
        @DisplayName("L-02: DB 버전 조회가 반복 실패해도 스크레이프마다 경고 로그를 남기지 않고 스로틀된다")
        void historyStatus_repeatedDbFailure_throttlesWarnLog() {
            ch.qos.logback.classic.Logger logger =
                    (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(LottoRecommendationService.class);
            ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                    new ch.qos.logback.core.read.ListAppender<>();
            appender.start();
            logger.addAppender(appender);
            try {
                given(historyStateRepository.findById(1)).willThrow(new RuntimeException("DB 연결 실패"));

                LottoRecommendationService.HistoryStatus first = service.historyStatus();
                LottoRecommendationService.HistoryStatus second = service.historyStatus();

                assertThat(first.ready()).isFalse();
                assertThat(first.databaseVersion()).isEqualTo(-1L);
                assertThat(second.ready()).isFalse();
                assertThat(appender.list).hasSize(1);
            } finally {
                logger.detachAppender(appender);
            }
        }
    }

    // ── R1: 과거 1등 배제를 시도 기반이 아닌 불변 조건으로 만든다 ──────────────────
    // 무작위 시도 횟수에 기대지 않고, 산술적 경계 계산과 제어된 난수 주입으로
    // "충돌 상한 도달 시에도 과거 1등 조합이 새어나가지 않는다"를 결정론적으로 증명한다.

    @Nested
    @DisplayName("R1: 충돌 상한 도달을 결정론적으로 검증")
    class InvariantEnforcement {

        @Test
        @DisplayName("정확히 6개 남았고 그 조합이 과거 1등이면 즉시 조합 부족 오류가 발생한다")
        void recommend_onlyRemainingComboIsHistorical_rejectsImmediately() {
            // 39개 제외 → 후보 6개(1~6) → C(6,6)=1가지뿐이며 그 조합이 과거 1등
            List<Integer> excluded = IntStream.rangeClosed(7, 45).boxed().collect(Collectors.toList());
            given(winningNumberRepository.findAllBalls())
                    .willReturn(List.of(ballsOnly(1, 1, 2, 3, 4, 5, 6)));
            service.loadHistoricalCombinations();

            assertThatThrownBy(() ->
                    service.recommend(new RecommendNumbersRequest(1, excluded, false, null, null, null), null)
            )
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> {
                        ApiException apiEx = (ApiException) ex;
                        assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                        assertThat(apiEx.getCode()).isEqualTo("INSUFFICIENT_UNIQUE_COMBINATIONS");
                    });
        }

        @Test
        @DisplayName("난수 소스가 항상 같은 과거 1등 조합만 만들어내면 그 조합 대신 명시적 오류로 끝난다")
        void recommend_randomSourceAlwaysHitsHistorical_throwsInsteadOfLeakingIt() {
            // 38개 제외 → 후보 7개(1~7). randomSource가 항상 0을 반환하면(스왑 없음)
            // 부분 Fisher-Yates는 매 시도 candidates의 앞 6개(1,2,3,4,5,6)만 반복 생성한다.
            // 그 조합을 과거 1등으로 등록하면 — 후보 7개 중 다른 6가지 조합(allowedPossible=6)이
            // 존재해 요청 단계 검사는 통과하지만, 이 난수 소스로는 절대 도달할 수 없다.
            List<Integer> excluded = IntStream.rangeClosed(8, 45).boxed().collect(Collectors.toList());
            given(winningNumberRepository.findAllBalls())
                    .willReturn(List.of(ballsOnly(1, 1, 2, 3, 4, 5, 6)));
            service.loadHistoricalCombinations();
            service.setRandomSource(bound -> 0);

            assertThatThrownBy(() ->
                    service.recommend(new RecommendNumbersRequest(1, excluded, false, null, null, null), null)
            )
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> {
                        ApiException apiEx = (ApiException) ex;
                        assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                        assertThat(apiEx.getCode()).isEqualTo("INSUFFICIENT_UNIQUE_COMBINATIONS");
                    });
        }

        @Test
        @DisplayName("count=10에 제외 번호가 많고 과거 조합과 고충돌해도 항상 요청 개수만큼 유일하고 배제된 조합을 반환한다")
        void recommend_highCollisionManyExclusions_stillSatisfiesInvariants() {
            // 45 - 15(제외) = 30개 후보. 그중 5개를 과거 1등으로 등록해 충돌 밀도를 높인다.
            List<Integer> excluded = IntStream.rangeClosed(1, 15).boxed().collect(Collectors.toList());
            List<WinningBallsOnly> historical = List.of(
                    ballsOnly(1, 16, 17, 18, 19, 20, 21),
                    ballsOnly(2, 22, 23, 24, 25, 26, 27),
                    ballsOnly(3, 28, 29, 30, 31, 32, 33),
                    ballsOnly(4, 34, 35, 36, 37, 38, 39),
                    ballsOnly(5, 40, 41, 42, 43, 44, 45)
            );
            given(winningNumberRepository.findAllBalls()).willReturn(historical);
            service.loadHistoricalCombinations();

            List<List<Integer>> combos = service.recommend(
                    new RecommendNumbersRequest(10, excluded, false, null, null, null), null).recommendations();

            assertThat(combos).hasSize(10);
            Set<Long> seenMasks = new HashSet<>();
            List<List<Integer>> historicalCombos = List.of(
                    List.of(16, 17, 18, 19, 20, 21),
                    List.of(22, 23, 24, 25, 26, 27),
                    List.of(28, 29, 30, 31, 32, 33),
                    List.of(34, 35, 36, 37, 38, 39),
                    List.of(40, 41, 42, 43, 44, 45)
            );
            for (List<Integer> combo : combos) {
                assertThat(combo).hasSize(6).doesNotHaveDuplicates().isSorted();
                assertThat(combo).doesNotContainAnyElementsOf(excluded);
                assertThat(historicalCombos).doesNotContain(combo);
                assertThat(seenMasks.add(combo.stream()
                        .mapToLong(n -> 1L << (n - 1)).reduce(0L, (a, b) -> a | b)))
                        .as("조합 간 중복 없음").isTrue();
            }
        }

        @Test
        @DisplayName("TD-002: balanced 전략도 요청 개수를 못 채우면 조용히 성공하지 않고 조합 부족 오류를 던진다")
        void recommend_balancedStrategy_deterministicRandomSource_throwsInsteadOfPartialResult() {
            // 38개 제외 → 후보 7개(1~7). 이력은 후보 범위 밖 조합(40~45)만 등록해 "이력 준비 완료"
            // 상태(R2 fail-closed 회피)는 유지하면서 allowedPossible=C(7,6)=7 >= count=2가 되게 한다.
            // validateFeasibility 사전 검사는 통과한다. 하지만 randomSource를 항상 0으로 고정하면
            // 부분 Fisher-Yates가 스왑 없이 매번 candidates 앞 6개(1~6)만 만들어내므로, 후보 풀은
            // 유일한 조합 1개에서 더 늘지 않는다 — count=2를 채우지 못한 채 과거에는 조용히
            // 1개짜리 결과를 성공으로 반환했지만, 이제는 명시적으로 실패해야 한다.
            List<Integer> excluded = IntStream.rangeClosed(8, 45).boxed().collect(Collectors.toList());
            given(winningNumberRepository.findAllBalls())
                    .willReturn(List.of(ballsOnly(1, 40, 41, 42, 43, 44, 45)));
            service.loadHistoricalCombinations();
            service.setRandomSource(bound -> 0);

            RecommendNumbersRequest request = new RecommendNumbersRequest(2, excluded, false, "balanced", null, null);

            assertThatThrownBy(() -> service.recommend(request, null))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> {
                        ApiException apiEx = (ApiException) ex;
                        assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                        assertThat(apiEx.getCode()).isEqualTo("INSUFFICIENT_UNIQUE_COMBINATIONS");
                    });
        }

        @Test
        @DisplayName("TD-002: balanced 전략도 count가 가능한 고유 조합 수를 초과하면 사전에 즉시 거절된다")
        void recommend_balancedStrategy_countExceedsAllowedPossible_rejectsImmediately() {
            // 39개 제외 → 후보 6개(1~6) → C(6,6)=1가지뿐이며 그 조합이 과거 1등.
            // allowedPossible=0이므로 balanced도 이제 random과 동일하게 사전 검사에서 즉시 실패해야 한다
            // (예전에는 balanced만 이 사전 검사를 건너뛰고 sampleBalanced까지 내려가 빈 결과로만 실패했음).
            List<Integer> excluded = IntStream.rangeClosed(7, 45).boxed().collect(Collectors.toList());
            given(winningNumberRepository.findAllBalls())
                    .willReturn(List.of(ballsOnly(1, 1, 2, 3, 4, 5, 6)));
            service.loadHistoricalCombinations();

            RecommendNumbersRequest request = new RecommendNumbersRequest(1, excluded, false, "balanced", null, null);

            assertThatThrownBy(() -> service.recommend(request, null))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> {
                        ApiException apiEx = (ApiException) ex;
                        assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                        assertThat(apiEx.getCode()).isEqualTo("INSUFFICIENT_UNIQUE_COMBINATIONS");
                    });
        }
    }

    // ── 속성 테스트: 모든 추천 결과가 지켜야 할 불변 조건 ─────────────────────────

    @Nested
    @DisplayName("속성 테스트 — 추천 결과 불변 조건")
    class PropertyBasedInvariants {

        @RepeatedTest(50)
        @DisplayName("무작위 요청에 대해 6개 유일·1~45 범위·제외 준수·과거 1등 배제·중복 없음이 항상 성립한다")
        void recommend_alwaysSatisfiesAllInvariants() {
            given(winningNumberRepository.findAllBalls())
                    .willReturn(List.of(ballsOnly(1, 1, 2, 3, 4, 5, 6), ballsOnly(2, 7, 14, 21, 28, 35, 42)));
            service.loadHistoricalCombinations();

            List<Integer> excluded = List.of(9, 18, 27, 36, 45);
            RecommendNumbersRequest request = new RecommendNumbersRequest(4, excluded, false, null, null, null);

            List<List<Integer>> combos = service.recommend(request, null).recommendations();

            assertThat(combos).hasSize(4);
            Set<Long> seenMasks = new HashSet<>();
            for (List<Integer> combo : combos) {
                assertThat(combo).hasSize(6);
                assertThat(combo).doesNotHaveDuplicates();
                assertThat(combo).isSorted();
                assertThat(combo).allMatch(n -> n >= 1 && n <= 45);
                assertThat(combo).doesNotContainAnyElementsOf(excluded);
                assertThat(combo).isNotEqualTo(List.of(1, 2, 3, 4, 5, 6));
                assertThat(combo).isNotEqualTo(List.of(7, 14, 21, 28, 35, 42));
                assertThat(seenMasks.add(combo.stream()
                        .mapToLong(n -> 1L << (n - 1)).reduce(0L, (a, b) -> a | b)))
                        .as("조합 간 중복 없음").isTrue();
            }
        }
    }

    // ── 당첨금 최대화 모드 ────────────────────────────────────────────────────

    @Nested
    @DisplayName("당첨금 최대화 모드")
    class MaximizePrize {

        @BeforeEach
        void setUpScorer() {
            // 기본적으로 모든 조합에 0점 반환 (각 테스트에서 override 가능)
            given(combinationScorer.score(anyList())).willReturn(0);
        }

        @Test
        @DisplayName("당첨금 최대화 모드이면 후보 풀 크기만큼 점수 계산기를 호출한다")
        void recommend_maximizePrize_callsScorerForCandidatePool() {
            service.recommend(new RecommendNumbersRequest(1, null, true, null, null, null), null);

            // 조합 1개당 후보 50개 생성 → score 50회 호출
            verify(combinationScorer, atLeast(50)).score(anyList());
        }

        @Test
        @DisplayName("당첨금 최대화 모드에서 3세트를 요청하면 점수 계산기를 최소 150회 호출한다")
        void recommend_maximizePrize_multipleCount_callsScorerPerCombo() {
            service.recommend(new RecommendNumbersRequest(3, null, true, null, null, null), null);

            verify(combinationScorer, atLeast(150)).score(anyList());
        }

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("당첨금 최대화 모드이면 가장 높은 점수의 조합을 반환한다")
        void recommend_maximizePrize_returnsHighestScoredCandidate() {
            // 점수 = 조합 번호의 합계 → 결정론적이고 검증 가능한 스코어
            given(combinationScorer.score(anyList())).willAnswer(inv -> {
                List<Integer> combo = inv.getArgument(0);
                return combo.stream().mapToInt(Integer::intValue).sum();
            });

            List<Integer> result = service.recommend(new RecommendNumbersRequest(1, null, true, null, null, null), null)
                    .recommendations().get(0);

            ArgumentCaptor<List<Integer>> captor = ArgumentCaptor.forClass(List.class);
            verify(combinationScorer, atLeast(50)).score(captor.capture());

            int maxScore = captor.getAllValues().stream()
                    .mapToInt(combo -> combo.stream().mapToInt(Integer::intValue).sum())
                    .max().orElse(0);
            int resultScore = result.stream().mapToInt(Integer::intValue).sum();

            assertThat(resultScore).isEqualTo(maxScore);
        }

        @Test
        @DisplayName("당첨금 최대화 모드에서도 반환 조합은 유효한 로또 번호 형식이다")
        void recommend_maximizePrize_returnsValidCombos() {
            service.recommend(new RecommendNumbersRequest(3, null, true, null, null, null), null)
                    .recommendations()
                    .forEach(combo -> {
                        assertThat(combo).hasSize(6);
                        assertThat(combo).doesNotHaveDuplicates();
                        assertThat(combo).isSorted();
                        assertThat(combo).allMatch(n -> n >= 1 && n <= 45);
                    });
        }

        @Test
        @DisplayName("당첨금 최대화 모드에서도 제외 번호는 결과에 포함되지 않는다")
        void recommend_maximizePrize_respectsExcludedNumbers() {
            List<Integer> excluded = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);

            service.recommend(new RecommendNumbersRequest(3, excluded, true, null, null, null), null)
                    .recommendations()
                    .forEach(combo ->
                            assertThat(combo).doesNotContainAnyElementsOf(excluded));
        }

        @Test
        @DisplayName("당첨금 최대화 모드에서도 역대 당첨 조합은 제외된다")
        void recommend_maximizePrize_historicalExclusionStillApplies() {
            // 역대 당첨 조합은 generateOne 단계에서 재추첨 처리되므로
            // scorer 호출 자체가 이루어지지 않음 → @BeforeEach의 기본 mock(0점)으로 충분
            given(winningNumberRepository.findAllBalls())
                    .willReturn(List.of(ballsOnly(1, 1, 2, 3, 4, 5, 6)));
            service.loadHistoricalCombinations();

            Set<List<Integer>> results = new HashSet<>();
            for (int i = 0; i < 20; i++) {
                results.addAll(service.recommend(
                        new RecommendNumbersRequest(1, null, true, null, null, null), null).recommendations());
            }

            assertThat(results).doesNotContain(List.of(1, 2, 3, 4, 5, 6));
        }
    }

    // ── 신규: strategy 필드, 고정 번호, BALANCED 전략 ────────────────────────────

    @Nested
    @DisplayName("strategy 필드")
    class StrategyField {

        @Test
        @DisplayName("strategy=balanced이면 balanced-v1 알고리즘 버전으로 응답한다")
        void recommend_balancedStrategy_returnsBalancedAlgorithmVersion() {
            RecommendNumbersRequest request = new RecommendNumbersRequest(3, null, null, "balanced", null, null);

            RecommendNumbersResponse response = service.recommend(request, null);

            assertThat(response.strategy()).isEqualTo(RecommendationStrategy.BALANCED);
            assertThat(response.algorithmVersion()).isEqualTo(BalancedScorer.VERSION);
            assertThat(response.items()).hasSize(3);
            response.items().forEach(item -> assertThat(item.explanationCodes()).isNotEmpty());
        }

        @Test
        @DisplayName("TD-022: 전략별 소요시간 타이머가 요청 후에도 같은 이름·태그로 조회된다")
        void recommend_strategyDurationTimer_registeredWithStableNameAndTag() {
            service.recommend(new RecommendNumbersRequest(1, null, null, "balanced", null, null), null);

            assertThat(meterRegistry.get("kraft_lotto_recommend_strategy_duration_seconds")
                    .tag("strategy", "balanced")
                    .timer()
                    .count())
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("알 수 없는 strategy 값은 400 INVALID_RECOMMENDATION_STRATEGY로 거절된다")
        void recommend_unknownStrategy_throwsApiException() {
            RecommendNumbersRequest request = new RecommendNumbersRequest(1, null, null, "not_a_strategy", null, null);

            assertThatThrownBy(() -> service.recommend(request, null))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> {
                        ApiException apiEx = (ApiException) ex;
                        assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                        assertThat(apiEx.getCode()).isEqualTo("INVALID_RECOMMENDATION_STRATEGY");
                    });
        }

        @Test
        @DisplayName("strategy가 reduceSharedWinnerRisk보다 우선한다")
        void recommend_strategyOverridesLegacyBooleanField() {
            // reduceSharedWinnerRisk=true지만 strategy=random을 함께 보내면 random이 이긴다
            RecommendNumbersRequest request = new RecommendNumbersRequest(1, null, true, "random", null, null);

            RecommendNumbersResponse response = service.recommend(request, null);

            assertThat(response.strategy()).isEqualTo(RecommendationStrategy.RANDOM);
            verify(combinationScorer, never()).score(anyList());
        }
    }

    @Nested
    @DisplayName("고정 번호(lockedNumbers)")
    class LockedNumbers {

        @RepeatedTest(10)
        @DisplayName("고정 번호는 모든 결과 조합에 포함된다")
        void recommend_lockedNumbersAlwaysIncluded() {
            List<Integer> locked = List.of(1, 2, 3);
            RecommendNumbersRequest request = new RecommendNumbersRequest(3, null, null, null, locked, null);

            service.recommend(request, null).recommendations()
                    .forEach(combo -> assertThat(combo).containsAll(locked));
        }

        @Test
        @DisplayName("고정 번호가 6개 이상이면 TOO_MANY_LOCKED_NUMBERS로 거절된다")
        void recommend_tooManyLocked_throwsApiException() {
            List<Integer> locked = List.of(1, 2, 3, 4, 5, 6);
            RecommendNumbersRequest request = new RecommendNumbersRequest(1, null, null, null, locked, null);

            assertThatThrownBy(() -> service.recommend(request, null))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> {
                        ApiException apiEx = (ApiException) ex;
                        assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                        assertThat(apiEx.getCode()).isEqualTo("TOO_MANY_LOCKED_NUMBERS");
                    });
        }

        @Test
        @DisplayName("고정 번호와 제외 번호가 겹치면 LOCKED_EXCLUDED_CONFLICT로 거절된다")
        void recommend_lockedExcludedOverlap_throwsApiException() {
            RecommendNumbersRequest request =
                    new RecommendNumbersRequest(1, List.of(1, 2), null, null, List.of(2, 3), null);

            assertThatThrownBy(() -> service.recommend(request, null))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> {
                        ApiException apiEx = (ApiException) ex;
                        assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                        assertThat(apiEx.getCode()).isEqualTo("LOCKED_EXCLUDED_CONFLICT");
                    });
        }
    }

    @Nested
    @DisplayName("익명 이력 영속화")
    class AnonymousHistoryPersistence {

        @Test
        @DisplayName("clientTokenHash가 있으면 추천 세트를 영속화하고 setId를 응답에 채운다")
        void recommend_withClientTokenHash_persistsAndReturnsSetId() {
            given(recommendationSetHistoryService.persist(
                    org.mockito.ArgumentMatchers.eq("hash-1"), org.mockito.ArgumentMatchers.anyString(),
                    org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyInt(),
                    org.mockito.ArgumentMatchers.eq(LottoRecommendationService.EXCLUSION_POLICY_VERSION),
                    anyList(), anyList(), org.mockito.ArgumentMatchers.anyList(),
                    org.mockito.ArgumentMatchers.any()))
                    .willReturn(42L);

            RecommendNumbersRequest request = new RecommendNumbersRequest(1, null, null, null, null, null);
            RecommendNumbersResponse response = service.recommend(request, "hash-1");

            assertThat(response.setId()).isEqualTo(42L);
            assertThat(response.createdAt()).isNotNull();
            assertThat(response.items()).hasSize(1);
        }

        @Test
        @DisplayName("clientTokenHash가 없으면 영속화하지 않고 setId/items/createdAt이 비어있지 않은 items만 채운다")
        void recommend_withoutClientTokenHash_skipsPersistence() {
            RecommendNumbersRequest request = new RecommendNumbersRequest(1, null, null, null, null, null);

            RecommendNumbersResponse response = service.recommend(request, null);

            assertThat(response.setId()).isNull();
            assertThat(response.createdAt()).isNull();
            assertThat(response.items()).hasSize(1);
            verify(recommendationSetHistoryService, never()).persist(
                    org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt(),
                    org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.any());
        }
    }
}
