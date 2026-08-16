package com.kraft.community.block;

import com.kraft.common.concurrency.TransientWriteRetrier;
import com.kraft.common.error.ApiException;
import com.kraft.community.user.CommunityUserRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("커뮤니티 사용자 차단 서비스 단위 테스트")
class CommunityBlockServiceTest {

    @Mock
    private CommunityUserBlockRepository communityUserBlockRepository;

    @Mock
    private CommunityUserRepository communityUserRepository;

    private CommunityBlockService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-28T00:00:00Z"), ZoneOffset.UTC);
        TransientWriteRetrier transientWriteRetrier = new TransientWriteRetrier(new SimpleMeterRegistry());
        service = new CommunityBlockService(
                communityUserBlockRepository, communityUserRepository, transientWriteRetrier, clock);
    }

    @Test
    @DisplayName("자기 자신을 차단하려 하면 400으로 거부된다")
    void block_self_throwsBadRequest() {
        assertThatThrownBy(() -> service.block(1L, 1L))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(apiEx.getCode()).isEqualTo("COMMUNITY_SELF_BLOCK_NOT_ALLOWED");
                });
        verify(communityUserBlockRepository, never()).upsertBlock(any(), any(), any());
    }

    @Test
    @DisplayName("존재하지 않는 사용자를 차단하려 하면 404를 던진다")
    void block_unknownUser_throwsNotFound() {
        given(communityUserRepository.existsById(2L)).willReturn(false);

        assertThatThrownBy(() -> service.block(1L, 2L))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(apiEx.getCode()).isEqualTo("COMMUNITY_USER_NOT_FOUND");
                });
    }

    /**
     * M-03: block()은 이제 존재 확인 없이 항상 upsertBlock을 호출한다 — 멱등성(이미
     * 차단됐어도 오류 없이 성공)은 이 DB-레벨 upsert(ON DUPLICATE KEY UPDATE)가 보장하므로
     * Mockito만으로는 검증할 수 없다. 동시 중복 PUT이 실제로 예외 없이 흡수되는지는
     * CommunityBlockConcurrencyTest(실 MariaDB)가 검증한다.
     */
    @Test
    @DisplayName("차단 시도는 항상 upsertBlock을 호출한다")
    void block_alwaysCallsUpsert() {
        given(communityUserRepository.existsById(2L)).willReturn(true);

        service.block(1L, 2L);

        verify(communityUserBlockRepository).upsertBlock(eq(1L), eq(2L), any(OffsetDateTime.class));
    }

    @Test
    @DisplayName("차단 해제는 존재 여부와 무관하게 항상 삭제를 시도하는 멱등 연산이다")
    void unblock_alwaysDeletes() {
        service.unblock(1L, 2L);

        verify(communityUserBlockRepository).deleteByBlockerUserIdAndBlockedUserId(1L, 2L);
    }

    @Test
    @DisplayName("차단 목록은 차단한 사용자 ID만 반환한다")
    void blockedUserIds_returnsBlockedIds() {
        given(communityUserBlockRepository.findTop100ByBlockerUserIdOrderByCreatedAtDesc(1L)).willReturn(List.of(
                new CommunityUserBlock(1L, 2L, Instant.now().atOffset(ZoneOffset.UTC)),
                new CommunityUserBlock(1L, 3L, Instant.now().atOffset(ZoneOffset.UTC))));

        List<Long> result = service.blockedUserIds(1L);

        assertThat(result).containsExactlyInAnyOrder(2L, 3L);
    }

    // C-1: 쓰기 경로(댓글·좋아요·북마크) 차단 강제는 방향과 무관해야 한다 — "내가 차단한
    // 사람"뿐 아니라 "나를 차단한 사람"의 글에도 상호작용할 수 없어야 차단이 실제로 보호가 된다.
    @Test
    @DisplayName("A가 B를 차단했으면 방향과 무관하게 상호 차단으로 판정한다")
    void isBlockedEitherWay_blockerToBlocked_true() {
        given(communityUserBlockRepository.existsByBlockerUserIdAndBlockedUserId(1L, 2L)).willReturn(true);

        assertThat(service.isBlockedEitherWay(1L, 2L)).isTrue();
        assertThat(service.isBlockedEitherWay(2L, 1L)).isTrue();
    }

    @Test
    @DisplayName("어느 쪽도 차단하지 않았으면 false다")
    void isBlockedEitherWay_noRelationship_false() {
        assertThat(service.isBlockedEitherWay(1L, 2L)).isFalse();
    }
}
