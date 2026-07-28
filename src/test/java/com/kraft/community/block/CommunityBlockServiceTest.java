package com.kraft.community.block;

import com.kraft.common.error.ApiException;
import com.kraft.community.user.CommunityUserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
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
        service = new CommunityBlockService(communityUserBlockRepository, communityUserRepository, clock);
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
        verify(communityUserBlockRepository, never()).save(any());
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

    @Test
    @DisplayName("차단이 없으면 새로 만든다")
    void block_whenAbsent_creates() {
        given(communityUserRepository.existsById(2L)).willReturn(true);
        given(communityUserBlockRepository.findByBlockerUserIdAndBlockedUserId(1L, 2L)).willReturn(Optional.empty());

        service.block(1L, 2L);

        verify(communityUserBlockRepository).save(any());
    }

    @Test
    @DisplayName("이미 차단했으면 멱등하게 아무 것도 하지 않는다(USER_ALREADY_BLOCKED 흡수)")
    void block_whenAlreadyBlocked_isIdempotent() {
        given(communityUserRepository.existsById(2L)).willReturn(true);
        given(communityUserBlockRepository.findByBlockerUserIdAndBlockedUserId(1L, 2L))
                .willReturn(Optional.of(new CommunityUserBlock(1L, 2L, Instant.now().atOffset(ZoneOffset.UTC))));

        service.block(1L, 2L);

        verify(communityUserBlockRepository, never()).save(any());
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
        given(communityUserBlockRepository.findByBlockerUserId(1L)).willReturn(List.of(
                new CommunityUserBlock(1L, 2L, Instant.now().atOffset(ZoneOffset.UTC)),
                new CommunityUserBlock(1L, 3L, Instant.now().atOffset(ZoneOffset.UTC))));

        List<Long> result = service.blockedUserIds(1L);

        assertThat(result).containsExactlyInAnyOrder(2L, 3L);
    }
}
