package com.kraft.winningnumber;

import com.kraft.common.error.ApiException;
import com.kraft.common.lotto.LottoNumberCodec;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("당첨 번호 명령 서비스 단위 테스트")
class WinningNumberCommandServiceTest {

    @Mock
    private WinningNumberRepository repository;

    private WinningNumberCommandService service;

    private static final LocalDate DRAW_DATE = LocalDate.of(2024, 1, 6);
    private static final List<Integer> NUMBERS = List.of(1, 2, 3, 4, 5, 6);
    private static final int BONUS = 7;

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-06-14T00:00:00Z"), ZoneId.of("Asia/Seoul"));
        service = new WinningNumberCommandService(repository, new LottoNumberCodec(), clock,
                new WinningNumberInsertExecutor(repository), VALIDATOR);
    }

    private WinningNumberUpsertRequest request(List<Integer> numbers, int bonus) {
        return new WinningNumberUpsertRequest(1, DRAW_DATE, numbers, bonus,
                1_000_000_000L, null, null, null, null);
    }

    private WinningNumber buildEntity() {
        return WinningNumberTestFactory.create(1, DRAW_DATE, 1, 2, 3, 4, 5, 6, BONUS,
                1_000_000_000L, 0L, 0, 0L, 0L,
                java.time.OffsetDateTime.now(java.time.Clock.systemDefaultZone()));
    }

    @Test
    @DisplayName("보너스 번호가 본번호와 중복이면 잘못된 요청 예외가 발생한다")
    void upsert_bonusDuplicatesMainNumber_throwsBadRequest() {
        assertThatThrownBy(() -> service.upsert(request(NUMBERS, 1)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(apiEx.getCode()).isEqualTo("INVALID_BONUS_NUMBER");
                });
    }

    @Test
    @DisplayName("신규 회차 저장 시 변경됨을 반환한다")
    void upsertWithResult_newRound_changedTrue() {
        given(repository.findByRound(1)).willReturn(Optional.empty());
        given(repository.save(any())).willAnswer(inv -> inv.getArgument(0));

        WinningNumberUpsertResult result = service.upsertWithResult(request(NUMBERS, BONUS));

        assertThat(result.changed()).isTrue();
    }

    @Test
    @DisplayName("변경 없는 기존 회차 저장 시 변경되지 않음을 반환한다")
    void upsertWithResult_existingUnchanged_changedFalse() {
        WinningNumber existing = buildEntity();
        given(repository.findByRound(1)).willReturn(Optional.of(existing));
        given(repository.save(any())).willAnswer(inv -> inv.getArgument(0));

        WinningNumberUpsertResult result = service.upsertWithResult(request(NUMBERS, BONUS));

        assertThat(result.changed()).isFalse();
    }

    @Test
    @DisplayName("1등 금액 변경 시 변경됨을 반환한다")
    void upsertWithResult_prizeAmountChanged_changedTrue() {
        WinningNumber existing = buildEntity();
        given(repository.findByRound(1)).willReturn(Optional.of(existing));
        given(repository.save(any())).willAnswer(inv -> inv.getArgument(0));

        WinningNumberUpsertRequest changedRequest = new WinningNumberUpsertRequest(
                1, DRAW_DATE, NUMBERS, BONUS, 2_000_000_000L, null, null, null, null);

        WinningNumberUpsertResult result = service.upsertWithResult(changedRequest);

        assertThat(result.changed()).isTrue();
    }

    @Test
    @DisplayName("2등 금액만 변경 시 변경됨을 반환한다")
    void upsertWithResult_secondPrizeChanged_changedTrue() {
        WinningNumber existing = buildEntity();
        given(repository.findByRound(1)).willReturn(Optional.of(existing));
        given(repository.save(any())).willAnswer(inv -> inv.getArgument(0));

        WinningNumberUpsertRequest changedRequest = new WinningNumberUpsertRequest(
                1, DRAW_DATE, NUMBERS, BONUS, 1_000_000_000L, 50_000_000L, null, null, null);

        WinningNumberUpsertResult result = service.upsertWithResult(changedRequest);

        assertThat(result.changed()).isTrue();
    }

    @Test
    @DisplayName("2등 당첨자 수만 변경 시 변경됨을 반환한다")
    void upsertWithResult_secondWinnersChanged_changedTrue() {
        WinningNumber existing = buildEntity();
        given(repository.findByRound(1)).willReturn(Optional.of(existing));
        given(repository.save(any())).willAnswer(inv -> inv.getArgument(0));

        WinningNumberUpsertRequest changedRequest = new WinningNumberUpsertRequest(
                1, DRAW_DATE, NUMBERS, BONUS, 1_000_000_000L, null, 5, null, null);

        WinningNumberUpsertResult result = service.upsertWithResult(changedRequest);

        assertThat(result.changed()).isTrue();
    }

    @Test
    @DisplayName("총 판매금액만 변경 시 변경됨을 반환한다")
    void upsertWithResult_totalSalesChanged_changedTrue() {
        WinningNumber existing = buildEntity();
        given(repository.findByRound(1)).willReturn(Optional.of(existing));
        given(repository.save(any())).willAnswer(inv -> inv.getArgument(0));

        WinningNumberUpsertRequest changedRequest = new WinningNumberUpsertRequest(
                1, DRAW_DATE, NUMBERS, BONUS, 1_000_000_000L, null, null, 80_000_000_000L, null);

        WinningNumberUpsertResult result = service.upsertWithResult(changedRequest);

        assertThat(result.changed()).isTrue();
    }

    @Test
    @DisplayName("1등 누적금액만 변경 시 변경됨을 반환한다")
    void upsertWithResult_firstAccumAmountChanged_changedTrue() {
        WinningNumber existing = buildEntity();
        given(repository.findByRound(1)).willReturn(Optional.of(existing));
        given(repository.save(any())).willAnswer(inv -> inv.getArgument(0));

        WinningNumberUpsertRequest changedRequest = new WinningNumberUpsertRequest(
                1, DRAW_DATE, NUMBERS, BONUS, 1_000_000_000L, null, null, null, 3_000_000_000L);

        WinningNumberUpsertResult result = service.upsertWithResult(changedRequest);

        assertThat(result.changed()).isTrue();
    }

    @Test
    @DisplayName("추첨일이 미래(현재+1일 초과)면 LOTTO_SOURCE_INVALID_DATE로 거부된다")
    void upsertWithResult_futureDrawDate_throwsInvalidDate() {
        WinningNumberUpsertRequest futureRequest = new WinningNumberUpsertRequest(
                1, LocalDate.of(2026, 6, 20), NUMBERS, BONUS,
                1_000_000_000L, null, null, null, null);

        assertThatThrownBy(() -> service.upsertWithResult(futureRequest))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(apiEx.getCode()).isEqualTo("LOTTO_SOURCE_INVALID_DATE");
                });
    }

    @Test
    @DisplayName("음수 2등 당첨금은 LOTTO_SOURCE_VALIDATION_ERROR로 거부된다")
    void upsertWithResult_negativeSecondPrize_throwsValidationError() {
        WinningNumberUpsertRequest negativeRequest = new WinningNumberUpsertRequest(
                1, DRAW_DATE, NUMBERS, BONUS, 1_000_000_000L, -1L, null, null, null);

        assertThatThrownBy(() -> service.upsertWithResult(negativeRequest))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(apiEx.getCode()).isEqualTo("LOTTO_SOURCE_VALIDATION_ERROR");
                });
    }

    @Test
    @DisplayName("동시 삽입 경쟁으로 고유 제약 위반 시 갱신으로 재해석하여 멱등 처리한다")
    void upsertWithResult_concurrentInsertRace_fallsBackToUpdate() {
        WinningNumber concurrentlyInserted = buildEntity();
        given(repository.findByRound(1))
                .willReturn(Optional.empty())
                .willReturn(Optional.of(concurrentlyInserted));
        given(repository.save(any()))
                .willThrow(new DataIntegrityViolationException("uk_winning_numbers_round"))
                .willAnswer(inv -> inv.getArgument(0));

        WinningNumberUpsertResult result = service.upsertWithResult(request(NUMBERS, BONUS));

        assertThat(result.changed()).isFalse();
        assertThat(result.response().round()).isEqualTo(1);
    }
}
