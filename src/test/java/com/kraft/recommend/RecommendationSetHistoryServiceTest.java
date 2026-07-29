package com.kraft.recommend;

import com.kraft.common.error.ApiException;
import com.kraft.common.lotto.LottoNumberCodec;
import java.time.OffsetDateTime;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("추천 세트 이력 서비스 단위 테스트")
class RecommendationSetHistoryServiceTest {

    @Mock
    private RecommendationSetRepository recommendationSetRepository;

    @Mock
    private RecommendationItemRepository recommendationItemRepository;

    @Mock
    private RecommendationSetAttachmentChecker attachmentChecker;

    private RecommendationSetHistoryService service;

    private static final String TOKEN_HASH = "hash-1";

    private static RecommendationSet setEntity(long id, String clientTokenHash) {
        try {
            RecommendationSet entity = new RecommendationSet(clientTokenHash, "random", "uniform-random-v1",
                    1189, "historical-first-prize-v1", null, null, OffsetDateTime.now());
            var field = RecommendationSet.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
            return entity;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    /** B-P0-2: claim된(계정 귀속) 세트를 흉내낸다 — clientTokenHash는 null, ownerUserId만 있다. */
    private static RecommendationSet claimedSetEntity(long id, Long ownerUserId) {
        RecommendationSet entity = setEntity(id, null);
        entity.claimTo(ownerUserId, OffsetDateTime.now());
        return entity;
    }

    @BeforeEach
    void setUp() {
        service = new RecommendationSetHistoryService(recommendationSetRepository, recommendationItemRepository,
                new LottoNumberCodec(), attachmentChecker);
    }

    @Test
    @DisplayName("추천 세트를 저장하고 항목도 함께 저장한다")
    void persist_savesSetAndItems() {
        given(recommendationSetRepository.save(org.mockito.ArgumentMatchers.any()))
                .willReturn(setEntity(1L, TOKEN_HASH));

        RecommendationItemView item = new RecommendationItemView(1, List.of(1, 2, 3, 4, 5, 6), null, List.of());
        Long id = service.persist(TOKEN_HASH, "random", "uniform-random-v1", 1189,
                "historical-first-prize-v1", List.of(), List.of(), List.of(item), OffsetDateTime.now());

        assertThat(id).isEqualTo(1L);
        verify(recommendationItemRepository).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("소유한 클라이언트 토큰으로 조회하면 세트를 반환한다")
    void get_ownedSet_returnsSummary() {
        given(recommendationSetRepository.findById(1L)).willReturn(Optional.of(setEntity(1L, TOKEN_HASH)));
        given(recommendationItemRepository.findBySetIdOrderByPosition(1L)).willReturn(List.of());

        RecommendationSetSummary summary = service.get(TOKEN_HASH, 1L);

        assertThat(summary.id()).isEqualTo(1L);
        assertThat(summary.strategy()).isEqualTo("random");
    }

    @Test
    @DisplayName("존재하지 않는 세트를 조회하면 404 RECOMMENDATION_SET_NOT_FOUND를 던진다")
    void get_notFound_throwsApiException() {
        given(recommendationSetRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(TOKEN_HASH, 1L))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(apiEx.getCode()).isEqualTo("RECOMMENDATION_SET_NOT_FOUND");
                });
    }

    @Test
    @DisplayName("다른 클라이언트의 세트를 조회하면 403 RECOMMENDATION_SET_NOT_OWNED를 던진다")
    void get_notOwned_throwsApiException() {
        given(recommendationSetRepository.findById(1L)).willReturn(Optional.of(setEntity(1L, "other-hash")));

        assertThatThrownBy(() -> service.get(TOKEN_HASH, 1L))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(apiEx.getCode()).isEqualTo("RECOMMENDATION_SET_NOT_OWNED");
                });
    }

    @Test
    @DisplayName("B-P0-2: claim된 세트는 귀속된 owner_user_id로 조회할 수 있다")
    void getForOwner_claimedSetWithMatchingOwnerUserId_returnsSummary() {
        given(recommendationSetRepository.findById(1L)).willReturn(Optional.of(claimedSetEntity(1L, 99L)));
        given(recommendationItemRepository.findBySetIdOrderByPosition(1L)).willReturn(List.of());

        RecommendationSetSummary summary = service.getForOwner(99L, 1L);

        assertThat(summary.id()).isEqualTo(1L);
    }

    @Test
    @DisplayName("B-P0-2: claim된 세트를 옛 기기 토큰으로 조회하면 여전히 403이다(의도된 동작 — 토큰은 null로 지워짐)")
    void get_claimedSet_stillRejectedByOldDeviceToken() {
        given(recommendationSetRepository.findById(1L)).willReturn(Optional.of(claimedSetEntity(1L, 99L)));

        assertThatThrownBy(() -> service.get(TOKEN_HASH, 1L))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("RECOMMENDATION_SET_NOT_OWNED"));
    }

    @Test
    @DisplayName("다른 계정 소유의 세트를 owner_user_id로 조회하면 403 RECOMMENDATION_SET_NOT_OWNED를 던진다")
    void getForOwner_notOwned_throwsApiException() {
        given(recommendationSetRepository.findById(1L)).willReturn(Optional.of(claimedSetEntity(1L, 42L)));

        assertThatThrownBy(() -> service.getForOwner(99L, 1L))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(apiEx.getCode()).isEqualTo("RECOMMENDATION_SET_NOT_OWNED");
                });
    }

    @Test
    @DisplayName("삭제 시 항목을 먼저 지우고 세트를 지운다")
    void delete_removesItemsThenSet() {
        RecommendationSet entity = setEntity(1L, TOKEN_HASH);
        given(recommendationSetRepository.findById(1L)).willReturn(Optional.of(entity));
        given(attachmentChecker.isAttachedToPost(1L)).willReturn(false);

        service.delete(TOKEN_HASH, 1L);

        verify(recommendationItemRepository).deleteBySetId(1L);
        verify(recommendationSetRepository).delete(entity);
    }

    @Test
    @DisplayName("커뮤니티 게시글에 첨부된 추천 세트는 삭제 시 409로 거부된다")
    void delete_attachedToPost_throwsConflict() {
        RecommendationSet entity = setEntity(1L, TOKEN_HASH);
        given(recommendationSetRepository.findById(1L)).willReturn(Optional.of(entity));
        given(attachmentChecker.isAttachedToPost(1L)).willReturn(true);

        assertThatThrownBy(() -> service.delete(TOKEN_HASH, 1L))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(apiEx.getCode()).isEqualTo("RECOMMENDATION_SET_ATTACHED_TO_POST");
                });
        verify(recommendationItemRepository, org.mockito.Mockito.never()).deleteBySetId(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("B-P0-2: claim된 세트를 owner_user_id로 삭제할 수 있다")
    void deleteForOwner_claimedSet_deletesItemsThenSet() {
        RecommendationSet entity = claimedSetEntity(1L, 99L);
        given(recommendationSetRepository.findById(1L)).willReturn(Optional.of(entity));
        given(attachmentChecker.isAttachedToPost(1L)).willReturn(false);

        service.deleteForOwner(99L, 1L);

        verify(recommendationItemRepository).deleteBySetId(1L);
        verify(recommendationSetRepository).delete(entity);
    }

    @Test
    @DisplayName("계정 귀속 시 해당 토큰의 모든 세트를 소유권 이전하고 client_token_hash를 비운다")
    void claimAll_movesAllSetsToOwnerAndClearsToken() {
        RecommendationSet set1 = setEntity(1L, TOKEN_HASH);
        RecommendationSet set2 = setEntity(2L, TOKEN_HASH);
        given(recommendationSetRepository.findByClientTokenHashOrderByCreatedAtDesc(TOKEN_HASH))
                .willReturn(List.of(set1, set2));
        OffsetDateTime claimedAt = OffsetDateTime.now();

        int moved = service.claimAll(TOKEN_HASH, 99L, claimedAt);

        assertThat(moved).isEqualTo(2);
        assertThat(set1.getOwnerUserId()).isEqualTo(99L);
        assertThat(set1.getClientTokenHash()).isNull();
        assertThat(set1.getClaimedAt()).isEqualTo(claimedAt);
        assertThat(set2.getOwnerUserId()).isEqualTo(99L);
    }

    @Test
    @DisplayName("계정으로 귀속된 추천 세트 목록을 owner_user_id 기준으로 조회한다")
    void listForOwner_returnsOwnedSets() {
        given(recommendationSetRepository.findByOwnerUserIdOrderByCreatedAtDesc(99L))
                .willReturn(List.of(setEntity(1L, null)));
        given(recommendationItemRepository.findBySetIdInOrderBySetIdAscPositionAsc(List.of(1L)))
                .willReturn(List.of());

        List<RecommendationSetSummary> result = service.listForOwner(99L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(1L);
    }

    @Test
    @DisplayName("KB-05: 세트가 여러 건이어도 아이템 조회는 세트별이 아니라 IN 배치 조회 한 번으로 끝낸다")
    void list_multipleSets_batchLoadsItemsInSingleQuery() {
        RecommendationSet set1 = setEntity(1L, TOKEN_HASH);
        RecommendationSet set2 = setEntity(2L, TOKEN_HASH);
        given(recommendationSetRepository.findByClientTokenHashOrderByCreatedAtDesc(TOKEN_HASH))
                .willReturn(List.of(set1, set2));
        given(recommendationItemRepository.findBySetIdInOrderBySetIdAscPositionAsc(List.of(1L, 2L)))
                .willReturn(List.of());

        List<RecommendationSetSummary> result = service.list(TOKEN_HASH);

        assertThat(result).extracting(RecommendationSetSummary::id).containsExactly(1L, 2L);
        verify(recommendationItemRepository, org.mockito.Mockito.never()).findBySetIdOrderByPosition(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("KB-05: getForAttachments는 게시글 여러 건의 첨부 세트를 IN 배치 조회 한 번으로 가져온다")
    void getForAttachments_batchLoadsMultipleSets() {
        RecommendationSet set1 = setEntity(5L, TOKEN_HASH);
        RecommendationSet set2 = setEntity(6L, TOKEN_HASH);
        given(recommendationSetRepository.findAllById(List.of(5L, 6L))).willReturn(List.of(set1, set2));
        given(recommendationItemRepository.findBySetIdInOrderBySetIdAscPositionAsc(List.of(5L, 6L)))
                .willReturn(List.of());

        var result = service.getForAttachments(List.of(5L, 6L));

        assertThat(result).hasSize(2);
        assertThat(result.get(5L).id()).isEqualTo(5L);
        assertThat(result.get(6L).id()).isEqualTo(6L);
        verify(recommendationSetRepository, org.mockito.Mockito.never()).findById(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("KB-05: getForAttachments에 존재하지 않는 세트 id가 섞이면 404를 던진다(단건 버전과 동일한 계약)")
    void getForAttachments_missingSet_throwsNotFound() {
        given(recommendationSetRepository.findAllById(List.of(5L, 6L)))
                .willReturn(List.of(setEntity(5L, TOKEN_HASH)));

        assertThatThrownBy(() -> service.getForAttachments(List.of(5L, 6L)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(apiEx.getCode()).isEqualTo("RECOMMENDATION_SET_NOT_FOUND");
                });
    }

    @Test
    @DisplayName("KB-05: getForAttachments에 빈 목록을 넘기면 리포지토리를 호출하지 않고 빈 맵을 반환한다")
    void getForAttachments_emptyIds_returnsEmptyMapWithoutQuerying() {
        var result = service.getForAttachments(List.of());

        assertThat(result).isEmpty();
        verify(recommendationSetRepository, org.mockito.Mockito.never()).findAllById(org.mockito.ArgumentMatchers.anyList());
    }
}
