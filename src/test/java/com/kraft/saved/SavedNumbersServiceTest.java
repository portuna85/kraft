package com.kraft.saved;

import com.kraft.common.config.SavedProperties;
import com.kraft.common.error.ApiErrorCode;
import com.kraft.common.error.ApiException;
import com.kraft.common.lotto.LottoNumberCodec;
import com.kraft.community.user.CommunityUser;
import com.kraft.community.user.CommunityUserRepository;
import com.kraft.winningnumber.WinningNumberQueryService;
import com.kraft.winningnumber.WinningNumberResponse;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("저장 번호 서비스 단위 테스트")
class SavedNumbersServiceTest {

    @Mock
    private SavedNumberRepository savedNumberRepository;

    @Mock
    private SavedNumberClientLockRepository savedNumberClientLockRepository;

    @Mock
    private SavedNumberClientLockInitializer savedNumberClientLockInitializer;

    @Mock
    private CommunityUserRepository communityUserRepository;

    @Mock
    private WinningNumberQueryService winningNumberQueryService;

    private LottoNumberCodec lottoNumberCodec;
    private SavedProperties savedProperties;
    private Clock clock;
    private SavedNumbersService service;

    private static final String TOKEN_HASH = "abc123hash";
    private static final List<Integer> VALID_NUMBERS = List.of(3, 11, 19, 28, 34, 42);

    /** SavedNumber without auto-generated ID (field stays null → NPE when unboxed to long).
     *  Use this factory to create a persisted-like entity with a real ID for tests. */
    private SavedNumber savedEntity(String numbers, String label, String source) {
        try {
            SavedNumber entity = new SavedNumber(TOKEN_HASH, numbers, label, source,
                    java.time.OffsetDateTime.now(clock));
            var field = SavedNumber.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, 1L);
            return entity;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @BeforeEach
    void setUp() {
        lottoNumberCodec = new LottoNumberCodec();
        savedProperties = new SavedProperties(100);
        clock = Clock.fixed(Instant.parse("2026-06-13T10:00:00Z"), ZoneId.of("Asia/Seoul"));
        service = new SavedNumbersService(savedNumberRepository, savedNumberClientLockRepository,
                savedNumberClientLockInitializer, communityUserRepository, lottoNumberCodec, savedProperties,
                winningNumberQueryService, clock);
    }

    private CommunityUser communityUser(long id) {
        try {
            CommunityUser user = new CommunityUser("google", "provider-" + id, "닉네임", null,
                    java.time.OffsetDateTime.now(clock));
            var field = CommunityUser.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, id);
            return user;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("새 번호 저장 시 생성됨을 반환한다")
    void save_newNumbers_returnsCreatedTrue() {
        String encoded = lottoNumberCodec.toStorageValue(VALID_NUMBERS);
        given(savedNumberRepository.findByClientTokenHashOrderByCreatedAtDesc(TOKEN_HASH)).willReturn(List.of());
        given(savedNumberRepository.save(any(SavedNumber.class)))
                .willAnswer(inv -> savedEntity(encoded, "즐겨찾기", "MANUAL"));

        SaveNumberResult result = service.save(TOKEN_HASH, new CreateSavedNumberRequest(VALID_NUMBERS, "즐겨찾기", "MANUAL"));

        assertThat(result.created()).isTrue();
        assertThat(result.savedNumber().numbers()).containsExactlyElementsOf(VALID_NUMBERS);
        verify(savedNumberRepository).save(any(SavedNumber.class));
        // 잠금 행 생성·최근 사용 시각 갱신(P1-06)은 SavedNumberClientLockInitializer 내부로
        // 위임됐으므로, 저장 흐름이 그 위임 호출을 여전히 하는지만 확인한다.
        verify(savedNumberClientLockInitializer).ensureExists(TOKEN_HASH);
    }

    @Test
    @DisplayName("이미 저장된 번호 재저장 시 생성되지 않았음을 반환한다")
    void save_duplicateNumbers_returnsCreatedFalse() {
        String encoded = lottoNumberCodec.toStorageValue(VALID_NUMBERS);
        given(savedNumberRepository.findByClientTokenHashOrderByCreatedAtDesc(TOKEN_HASH))
                .willReturn(List.of(savedEntity(encoded, "기존", "MANUAL")));

        SaveNumberResult result = service.save(TOKEN_HASH, new CreateSavedNumberRequest(VALID_NUMBERS, null, null));

        assertThat(result.created()).isFalse();
    }

    @Test
    @DisplayName("저장 한도 초과 시 충돌 예외가 발생한다")
    void save_overLimit_throwsConflictApiException() {
        String otherEncoded = lottoNumberCodec.toStorageValue(List.of(1, 2, 3, 4, 5, 6));
        List<SavedNumber> full = java.util.stream.IntStream.range(0, 100)
                .mapToObj(i -> savedEntity(otherEncoded + i, null, "MANUAL"))
                .toList();
        given(savedNumberRepository.findByClientTokenHashOrderByCreatedAtDesc(TOKEN_HASH)).willReturn(full);

        assertThatThrownBy(() ->
                service.save(TOKEN_HASH, new CreateSavedNumberRequest(VALID_NUMBERS, null, null))
        )
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(apiEx.getCode()).isEqualTo("SAVED_LIMIT_REACHED");
                });
    }

    @Test
    @DisplayName("존재하지 않는 번호 삭제 시 찾을 수 없음 예외가 발생한다")
    void delete_notFound_throwsNotFoundApiException() {
        given(savedNumberRepository.findByIdAndClientTokenHash(99L, TOKEN_HASH))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(TOKEN_HASH, 99L))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(apiEx.getCode()).isEqualTo("SAVED_NUMBER_NOT_FOUND");
                });
    }

    @Test
    @DisplayName("[B-05 객체 수준 인가] 실존하는 ID라도 다른 토큰 소유라면 조회되지 않아 삭제할 수 없다")
    void delete_idBelongsToDifferentTokenHash_throwsNotFoundApiException() {
        // id=1은 실제로 존재하지만 소유 토큰이 다르다 — 리포지토리 쿼리 자체가
        // (id, clientTokenHash) 복합 조건이라 다른 토큰으로 조회하면 항상 empty를 반환한다.
        // id 단독 조회(findById)로 바뀌면 이 테스트가 실패해 회귀를 잡는다.
        String attackerTokenHash = "attacker-token-hash";
        given(savedNumberRepository.findByIdAndClientTokenHash(1L, attackerTokenHash))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(attackerTokenHash, 1L))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(savedNumberRepository).findByIdAndClientTokenHash(1L, attackerTokenHash);
    }

    @Test
    @DisplayName("번호 목록 조회 시 해시 기준으로 내림차순 반환")
    void list_returnsItemsForTokenHash() {
        String encoded = lottoNumberCodec.toStorageValue(VALID_NUMBERS);
        given(savedNumberRepository.findByClientTokenHashOrderByCreatedAtDesc(TOKEN_HASH))
                .willReturn(List.of(savedEntity(encoded, null, "MANUAL")));

        List<SavedNumberResponse> result = service.list(TOKEN_HASH);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).numbers()).containsExactlyElementsOf(VALID_NUMBERS);
    }

    @Test
    @DisplayName("출처가 없으면 수동 저장으로 처리한다")
    void save_nullSource_defaultsToManual() {
        String encoded = lottoNumberCodec.toStorageValue(VALID_NUMBERS);
        given(savedNumberRepository.findByClientTokenHashOrderByCreatedAtDesc(TOKEN_HASH)).willReturn(List.of());
        given(savedNumberRepository.save(any(SavedNumber.class)))
                .willAnswer(inv -> savedEntity(encoded, null, "MANUAL"));

        SaveNumberResult result = service.save(TOKEN_HASH, new CreateSavedNumberRequest(VALID_NUMBERS, null, null));

        assertThat(result.savedNumber().source()).isEqualTo("MANUAL");
    }

    @Test
    @DisplayName("최신 회차 기준이면 최신 회차와 대조해 등수를 계산한다")
    void compareWithRound_latest_returnsMatchResults() {
        String encoded = lottoNumberCodec.toStorageValue(VALID_NUMBERS);
        given(savedNumberRepository.findByClientTokenHashOrderByCreatedAtDesc(TOKEN_HASH))
                .willReturn(List.of(savedEntity(encoded, null, "MANUAL")));
        WinningNumberResponse draw = new WinningNumberResponse(
                1234, LocalDate.of(2026, 6, 13), VALID_NUMBERS, 7,
                1_000_000_000L, 0L, 0, 0L, 0L
        );
        given(winningNumberQueryService.findLatest()).willReturn(Optional.of(draw));

        List<SavedNumberMatchResult> result = service.compareWithRound(TOKEN_HASH, "latest");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).matchedCount()).isEqualTo(6);
        assertThat(result.get(0).prizeTier()).isEqualTo("1등");
    }

    @Test
    @DisplayName("최신 회차 기준인데 집계된 회차가 없으면 404 예외가 발생한다")
    void compareWithRound_latestButNoRound_throwsNotFound() {
        given(winningNumberQueryService.findLatest()).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.compareWithRound(TOKEN_HASH, "latest"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(apiEx.getCode()).isEqualTo("ROUND_NOT_FOUND");
                });
    }

    @Test
    @DisplayName("명시적 회차 번호를 지정하면 해당 회차와 대조한다")
    void compareWithRound_explicitRound_callsGetByRound() {
        String encoded = lottoNumberCodec.toStorageValue(VALID_NUMBERS);
        given(savedNumberRepository.findByClientTokenHashOrderByCreatedAtDesc(TOKEN_HASH))
                .willReturn(List.of(savedEntity(encoded, null, "MANUAL")));
        WinningNumberResponse draw = new WinningNumberResponse(
                1234, LocalDate.of(2026, 6, 13), List.of(1, 2, 3, 4, 5, 6), 7,
                1_000_000_000L, 0L, 0, 0L, 0L
        );
        given(winningNumberQueryService.getByRound(1234)).willReturn(draw);

        List<SavedNumberMatchResult> result = service.compareWithRound(TOKEN_HASH, "1234");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).round()).isEqualTo(1234);
    }

    @Test
    @DisplayName("존재하지 않는 회차를 지정하면 404 예외가 그대로 전파된다")
    void compareWithRound_nonexistentRound_propagatesNotFound() {
        given(winningNumberQueryService.getByRound(anyInt()))
                .willThrow(new ApiException(ApiErrorCode.ROUND_NOT_FOUND, "999999회차 정보를 찾을 수 없습니다."));

        assertThatThrownBy(() -> service.compareWithRound(TOKEN_HASH, "999999"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(apiEx.getCode()).isEqualTo("ROUND_NOT_FOUND");
                });
    }

    @Test
    @DisplayName("잘못된 회차 파라미터는 400 예외가 발생한다")
    void compareWithRound_invalidRoundParam_throwsBadRequest() {
        assertThatThrownBy(() -> service.compareWithRound(TOKEN_HASH, "abc"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(apiEx.getCode()).isEqualTo("INVALID_ROUND");
                });

        assertThatThrownBy(() -> service.compareWithRound(TOKEN_HASH, "-1"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("INVALID_ROUND"));
    }

    @Test
    @DisplayName("저장된 번호가 없으면 빈 리스트를 반환한다")
    void compareWithRound_noSavedNumbers_returnsEmptyList() {
        given(savedNumberRepository.findByClientTokenHashOrderByCreatedAtDesc(TOKEN_HASH))
                .willReturn(List.of());
        WinningNumberResponse draw = new WinningNumberResponse(
                1234, LocalDate.of(2026, 6, 13), VALID_NUMBERS, 7,
                1_000_000_000L, 0L, 0, 0L, 0L
        );
        given(winningNumberQueryService.findLatest()).willReturn(Optional.of(draw));

        assertThat(service.compareWithRound(TOKEN_HASH, "latest")).isEmpty();
    }

    @Test
    @DisplayName("귀속 시 계정에 없는 조합은 계정 소유권으로 옮긴다")
    void claimAll_newCombination_movesToOwner() {
        String encoded = lottoNumberCodec.toStorageValue(VALID_NUMBERS);
        SavedNumber anonymous = savedEntity(encoded, null, "MANUAL");
        given(savedNumberRepository.findByClientTokenHashOrderByCreatedAtDesc(TOKEN_HASH))
                .willReturn(List.of(anonymous));
        given(savedNumberRepository.findByOwnerUserIdAndNumbersIn(99L, List.of(encoded))).willReturn(List.of());

        SavedNumberClaimResult result = service.claimAll(TOKEN_HASH, 99L);

        assertThat(result.mergedCount()).isEqualTo(1);
        assertThat(result.duplicateCount()).isEqualTo(0);
        assertThat(anonymous.getOwnerUserId()).isEqualTo(99L);
        assertThat(anonymous.getClientTokenHash()).isNull();
        verify(savedNumberRepository, never()).delete(any());
    }

    @Test
    @DisplayName("귀속 시 계정에 이미 같은 조합이 있으면 익명 쪽 중복 행을 삭제한다")
    void claimAll_duplicateCombination_deletesAnonymousRow() {
        String encoded = lottoNumberCodec.toStorageValue(VALID_NUMBERS);
        SavedNumber anonymous = savedEntity(encoded, null, "MANUAL");
        given(savedNumberRepository.findByClientTokenHashOrderByCreatedAtDesc(TOKEN_HASH))
                .willReturn(List.of(anonymous));
        given(savedNumberRepository.findByOwnerUserIdAndNumbersIn(99L, List.of(encoded)))
                .willReturn(List.of(savedEntity(encoded, null, "MANUAL")));

        SavedNumberClaimResult result = service.claimAll(TOKEN_HASH, 99L);

        assertThat(result.mergedCount()).isEqualTo(0);
        assertThat(result.duplicateCount()).isEqualTo(1);
        verify(savedNumberRepository).delete(anonymous);
    }

    @Test
    @DisplayName("계정으로 귀속된 저장 번호 목록을 owner_user_id 기준으로 조회한다")
    void listForOwner_returnsOwnedSavedNumbers() {
        String encoded = lottoNumberCodec.toStorageValue(VALID_NUMBERS);
        given(savedNumberRepository.findByOwnerUserIdOrderByCreatedAtDesc(99L))
                .willReturn(List.of(savedEntity(encoded, "즐겨찾기", "MANUAL")));

        List<SavedNumberResponse> result = service.listForOwner(99L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).numbers()).containsExactlyElementsOf(VALID_NUMBERS);
    }

    // --- B-P0-3: claim(계정 귀속) 이후에는 client_token_hash가 null로 지워져 기기 토큰 기준
    // 조회로는 저장 번호를 찾을 수 없다. owner_user_id 기준 대조·저장·삭제 경로를 검증한다. ---

    @Test
    @DisplayName("B-P0-3: claim된 저장 번호는 owner_user_id 기준으로 회차 대조가 된다(기기 토큰 목록에는 더 이상 없음)")
    void compareWithRoundForOwner_claimedNumbers_returnsMatchResults() {
        String encoded = lottoNumberCodec.toStorageValue(VALID_NUMBERS);
        given(savedNumberRepository.findByOwnerUserIdOrderByCreatedAtDesc(99L))
                .willReturn(List.of(savedEntity(encoded, null, "MANUAL")));
        WinningNumberResponse draw = new WinningNumberResponse(
                1234, LocalDate.of(2026, 6, 13), VALID_NUMBERS, 7, 1_000_000_000L, 0L, 0, 0L, 0L);
        given(winningNumberQueryService.findLatest()).willReturn(Optional.of(draw));

        List<SavedNumberMatchResult> result = service.compareWithRoundForOwner(99L, "latest");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).matchedCount()).isEqualTo(6);
    }

    @Test
    @DisplayName("B-P0-3: 계정 저장 시 계정에 이미 같은 조합이 있으면 생성되지 않았음을 반환한다(claim 후 dedup 복원)")
    void saveForOwner_duplicateNumbers_returnsCreatedFalse() {
        String encoded = lottoNumberCodec.toStorageValue(VALID_NUMBERS);
        given(communityUserRepository.lockById(99L)).willReturn(Optional.of(communityUser(99L)));
        given(savedNumberRepository.findByOwnerUserIdAndNumbers(99L, encoded))
                .willReturn(Optional.of(savedEntity(encoded, "기존", "MANUAL")));

        SaveNumberResult result = service.saveForOwner(99L, new CreateSavedNumberRequest(VALID_NUMBERS, null, null));

        assertThat(result.created()).isFalse();
        verify(savedNumberRepository, never()).save(any());
    }

    @Test
    @DisplayName("B-P0-3: 계정 저장 한도 초과 시 충돌 예외가 발생한다(claim 후 한도 복원)")
    void saveForOwner_overLimit_throwsConflictApiException() {
        given(communityUserRepository.lockById(99L)).willReturn(Optional.of(communityUser(99L)));
        given(savedNumberRepository.findByOwnerUserIdAndNumbers(99L,
                lottoNumberCodec.toStorageValue(VALID_NUMBERS))).willReturn(Optional.empty());
        given(savedNumberRepository.countByOwnerUserId(99L)).willReturn(100L);

        assertThatThrownBy(() ->
                service.saveForOwner(99L, new CreateSavedNumberRequest(VALID_NUMBERS, null, null)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(apiEx.getCode()).isEqualTo("SAVED_LIMIT_REACHED");
                });
    }

    @Test
    @DisplayName("B-P0-3: 새 조합은 계정 소유로 바로 저장된다")
    void saveForOwner_newCombination_returnsCreatedTrue() {
        String encoded = lottoNumberCodec.toStorageValue(VALID_NUMBERS);
        given(communityUserRepository.lockById(99L)).willReturn(Optional.of(communityUser(99L)));
        given(savedNumberRepository.findByOwnerUserIdAndNumbers(99L, encoded)).willReturn(Optional.empty());
        given(savedNumberRepository.countByOwnerUserId(99L)).willReturn(0L);
        given(savedNumberRepository.save(any(SavedNumber.class)))
                .willAnswer(inv -> savedEntity(encoded, null, "MANUAL"));

        SaveNumberResult result = service.saveForOwner(99L, new CreateSavedNumberRequest(VALID_NUMBERS, null, "MANUAL"));

        assertThat(result.created()).isTrue();
        verify(savedNumberClientLockInitializer, never()).ensureExists(any());
    }

    @Test
    @DisplayName("KB-14: 계정 저장은 owner 행을 레코드 락으로 잠근 뒤 dedup·한도를 확인한다")
    void saveForOwner_locksOwnerRowBeforeDedupCheck() {
        String encoded = lottoNumberCodec.toStorageValue(VALID_NUMBERS);
        given(communityUserRepository.lockById(99L)).willReturn(Optional.of(communityUser(99L)));
        given(savedNumberRepository.findByOwnerUserIdAndNumbers(99L, encoded)).willReturn(Optional.empty());
        given(savedNumberRepository.countByOwnerUserId(99L)).willReturn(0L);
        given(savedNumberRepository.save(any(SavedNumber.class)))
                .willAnswer(inv -> savedEntity(encoded, null, "MANUAL"));

        service.saveForOwner(99L, new CreateSavedNumberRequest(VALID_NUMBERS, null, "MANUAL"));

        var inOrder = org.mockito.Mockito.inOrder(communityUserRepository, savedNumberRepository);
        inOrder.verify(communityUserRepository).lockById(99L);
        inOrder.verify(savedNumberRepository).findByOwnerUserIdAndNumbers(99L, encoded);
    }

    @Test
    @DisplayName("KB-14: 존재하지 않는 계정으로 저장을 시도하면 404가 발생한다")
    void saveForOwner_ownerNotFound_throwsNotFound() {
        given(communityUserRepository.lockById(404L)).willReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.saveForOwner(404L, new CreateSavedNumberRequest(VALID_NUMBERS, null, null)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(apiEx.getCode()).isEqualTo("COMMUNITY_USER_NOT_FOUND");
                });
        verify(savedNumberRepository, never()).save(any());
    }

    @Test
    @DisplayName("KB-14: 유니크 키 경합으로 저장이 실패하면 500 대신 멱등 응답으로 흡수한다")
    void saveForOwner_uniqueKeyRace_absorbsAsIdempotentResponse() {
        String encoded = lottoNumberCodec.toStorageValue(VALID_NUMBERS);
        given(communityUserRepository.lockById(99L)).willReturn(Optional.of(communityUser(99L)));
        given(savedNumberRepository.findByOwnerUserIdAndNumbers(99L, encoded))
                .willReturn(Optional.empty())
                .willReturn(Optional.of(savedEntity(encoded, null, "MANUAL")));
        given(savedNumberRepository.countByOwnerUserId(99L)).willReturn(0L);
        given(savedNumberRepository.save(any(SavedNumber.class)))
                .willThrow(new DataIntegrityViolationException("uk_saved_owner_numbers"));

        SaveNumberResult result = service.saveForOwner(99L, new CreateSavedNumberRequest(VALID_NUMBERS, null, "MANUAL"));

        assertThat(result.created()).isFalse();
    }

    @Test
    @DisplayName("B-P0-3: claim된 저장 번호는 owner_user_id 기준으로 삭제할 수 있다(기기 토큰 삭제는 더 이상 불가)")
    void deleteForOwner_claimedNumber_deletesRow() {
        SavedNumber owned = savedEntity(lottoNumberCodec.toStorageValue(VALID_NUMBERS), null, "MANUAL");
        given(savedNumberRepository.findByIdAndOwnerUserId(1L, 99L)).willReturn(Optional.of(owned));

        service.deleteForOwner(99L, 1L);

        verify(savedNumberRepository).delete(owned);
    }

    @Test
    @DisplayName("B-P0-3: 다른 계정 소유의 저장 번호를 삭제하려 하면 404가 발생한다")
    void deleteForOwner_notOwned_throwsNotFound() {
        given(savedNumberRepository.findByIdAndOwnerUserId(1L, 42L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteForOwner(42L, 1L))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("SAVED_NUMBER_NOT_FOUND"));
    }
}
