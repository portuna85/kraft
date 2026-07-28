package com.kraft.identity;

import com.kraft.common.error.ApiException;
import com.kraft.recommend.RecommendationSetHistoryService;
import com.kraft.saved.SavedNumberClaimResult;
import com.kraft.saved.SavedNumberClientLockInitializer;
import com.kraft.saved.SavedNumberClientLockRepository;
import com.kraft.saved.SavedNumbersService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("로그인 계정 귀속 서비스 단위 테스트")
class IdentityMergeServiceTest {

    @Mock
    private DeviceClaimRepository deviceClaimRepository;

    @Mock
    private SavedNumberClientLockRepository savedNumberClientLockRepository;

    @Mock
    private SavedNumberClientLockInitializer savedNumberClientLockInitializer;

    @Mock
    private SavedNumbersService savedNumbersService;

    @Mock
    private RecommendationSetHistoryService recommendationSetHistoryService;

    private IdentityMergeService service;

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-28T00:00:00Z"), ZoneOffset.UTC);
    private static final String TOKEN_HASH = "hash-1";

    @BeforeEach
    void setUp() {
        service = new IdentityMergeService(deviceClaimRepository, savedNumberClientLockRepository,
                savedNumberClientLockInitializer, savedNumbersService, recommendationSetHistoryService, CLOCK,
                new SimpleMeterRegistry());
    }

    @Test
    @DisplayName("처음 귀속하면 클레임 행을 만들고 저장 번호·추천 세트를 옮긴다")
    void claim_firstTime_recordsClaimAndMergesData() {
        given(deviceClaimRepository.findByDeviceTokenHash(TOKEN_HASH)).willReturn(Optional.empty());
        given(savedNumbersService.claimAll(TOKEN_HASH, 1L)).willReturn(new SavedNumberClaimResult(3, 1));
        given(recommendationSetHistoryService.claimAll(any(), any(), any())).willReturn(2);

        IdentityMergeResult result = service.claim(TOKEN_HASH, 1L);

        assertThat(result.mergedSavedNumberCount()).isEqualTo(3);
        assertThat(result.duplicateSavedNumberCount()).isEqualTo(1);
        assertThat(result.mergedRecommendationSetCount()).isEqualTo(2);
        verify(deviceClaimRepository).save(any());
        verify(savedNumberClientLockInitializer).ensureExists(TOKEN_HASH);
        verify(savedNumberClientLockRepository).lockByClientTokenHash(TOKEN_HASH);
    }

    @Test
    @DisplayName("같은 사용자가 재시도하면 클레임을 다시 만들지 않고 멱등하게 처리한다")
    void claim_sameUserRetry_isIdempotent() {
        given(deviceClaimRepository.findByDeviceTokenHash(TOKEN_HASH))
                .willReturn(Optional.of(new DeviceClaim(TOKEN_HASH, 1L, OffsetDateTime.now(CLOCK))));
        given(savedNumbersService.claimAll(TOKEN_HASH, 1L)).willReturn(new SavedNumberClaimResult(0, 0));
        given(recommendationSetHistoryService.claimAll(any(), any(), any())).willReturn(0);

        IdentityMergeResult result = service.claim(TOKEN_HASH, 1L);

        assertThat(result.mergedSavedNumberCount()).isEqualTo(0);
        verify(deviceClaimRepository, never()).save(any());
    }

    @Test
    @DisplayName("다른 사용자가 이미 귀속한 토큰이면 409 DEVICE_ALREADY_CLAIMED로 거부된다")
    void claim_alreadyClaimedByOtherUser_throwsConflict() {
        given(deviceClaimRepository.findByDeviceTokenHash(TOKEN_HASH))
                .willReturn(Optional.of(new DeviceClaim(TOKEN_HASH, 2L, OffsetDateTime.now(CLOCK))));

        assertThatThrownBy(() -> service.claim(TOKEN_HASH, 1L))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(apiEx.getCode()).isEqualTo("DEVICE_ALREADY_CLAIMED");
                });
        verify(savedNumbersService, never()).claimAll(any(), any());
    }
}
