package com.kraft.recommend;

import com.kraft.common.error.ApiException;
import com.kraft.common.lotto.LottoNumberCodec;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
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

    @BeforeEach
    void setUp() {
        service = new RecommendationSetHistoryService(recommendationSetRepository, recommendationItemRepository,
                new LottoNumberCodec());
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
        // M-8: 항목마다 save()를 개별 호출하던 것을 saveAll()로 한 번에 묶었다.
        verify(recommendationItemRepository).saveAll(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("KF-01: 로그인 계정 소유로 저장하면 owner_user_id로 저장되고 항목도 함께 저장된다")
    void persistForOwner_savesSetWithOwnerAndItems() {
        var captor = org.mockito.ArgumentCaptor.forClass(RecommendationSet.class);
        given(recommendationSetRepository.save(captor.capture())).willReturn(setEntity(1L, TOKEN_HASH));

        RecommendationItemView item = new RecommendationItemView(1, List.of(1, 2, 3, 4, 5, 6), null, List.of());
        Long id = service.persistForOwner(99L, "random", "uniform-random-v1", 1189,
                "historical-first-prize-v1", List.of(), List.of(), List.of(item), OffsetDateTime.now());

        assertThat(id).isEqualTo(1L);
        // KF-01: 계정 소유로 만든 행은 owner_user_id만 채우고 client_token_hash는 비워야
        // chk_recommendation_sets_owner_xor 제약(V33)을 만족한다.
        assertThat(captor.getValue().getOwnerUserId()).isEqualTo(99L);
        assertThat(captor.getValue().getClientTokenHash()).isNull();
        verify(recommendationItemRepository).saveAll(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("계정 귀속 시 해당 토큰의 모든 세트를 벌크 UPDATE로 소유권 이전한다")
    void claimAll_movesAllSetsToOwnerAndClearsToken() {
        OffsetDateTime claimedAt = OffsetDateTime.now();
        given(recommendationSetRepository.claimAllByClientTokenHash(TOKEN_HASH, 99L, claimedAt))
                .willReturn(2);

        int moved = service.claimAll(TOKEN_HASH, 99L, claimedAt);

        assertThat(moved).isEqualTo(2);
        verify(recommendationSetRepository).claimAllByClientTokenHash(TOKEN_HASH, 99L, claimedAt);
    }

    @Test
    @DisplayName("계정으로 귀속된 추천 세트 목록을 owner_user_id 기준으로 조회한다")
    void listForOwner_returnsOwnedSets() {
        given(recommendationSetRepository.findByOwnerUserIdOrderByCreatedAtDescIdDesc(
                org.mockito.ArgumentMatchers.eq(99L), org.mockito.ArgumentMatchers.any(PageRequest.class)))
                .willReturn(new PageImpl<>(List.of(setEntity(1L, null))));
        given(recommendationItemRepository.findBySetIdInOrderBySetIdAscPositionAsc(List.of(1L)))
                .willReturn(List.of());

        Page<RecommendationSetSummary> result = service.listForOwner(99L, 0, 50);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).id()).isEqualTo(1L);
    }

    @Test
    @DisplayName("KB-05: 세트가 여러 건이어도 아이템 조회는 세트별이 아니라 IN 배치 조회 한 번으로 끝낸다")
    void list_multipleSets_batchLoadsItemsInSingleQuery() {
        RecommendationSet set1 = setEntity(1L, TOKEN_HASH);
        RecommendationSet set2 = setEntity(2L, TOKEN_HASH);
        given(recommendationSetRepository.findByClientTokenHashOrderByCreatedAtDescIdDesc(
                org.mockito.ArgumentMatchers.eq(TOKEN_HASH), org.mockito.ArgumentMatchers.any(PageRequest.class)))
                .willReturn(new PageImpl<>(List.of(set1, set2)));
        given(recommendationItemRepository.findBySetIdInOrderBySetIdAscPositionAsc(List.of(1L, 2L)))
                .willReturn(List.of());

        Page<RecommendationSetSummary> result = service.list(TOKEN_HASH, 0, 50);

        assertThat(result.getContent()).extracting(RecommendationSetSummary::id).containsExactly(1L, 2L);
        verify(recommendationItemRepository, org.mockito.Mockito.never()).findBySetIdOrderByPosition(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("소유한 클라이언트 토큰이면 조용히 통과한다")
    void assertOwnedByDevice_ownedSet_doesNotThrow() {
        given(recommendationSetRepository.findById(1L)).willReturn(java.util.Optional.of(setEntity(1L, TOKEN_HASH)));

        service.assertOwnedByDevice(TOKEN_HASH, 1L);
    }

    @Test
    @DisplayName("다른 클라이언트의 세트를 검증하면 403 RECOMMENDATION_SET_NOT_OWNED를 던진다")
    void assertOwnedByDevice_notOwned_throwsApiException() {
        given(recommendationSetRepository.findById(1L))
                .willReturn(java.util.Optional.of(setEntity(1L, "other-hash")));

        assertThatThrownBy(() -> service.assertOwnedByDevice(TOKEN_HASH, 1L))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(apiEx.getCode()).isEqualTo("RECOMMENDATION_SET_NOT_OWNED");
                });
    }

    @Test
    @DisplayName("존재하지 않는 세트를 검증하면 404 RECOMMENDATION_SET_NOT_FOUND를 던진다")
    void assertOwnedByDevice_notFound_throwsApiException() {
        given(recommendationSetRepository.findById(1L)).willReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service.assertOwnedByDevice(TOKEN_HASH, 1L))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(apiEx.getCode()).isEqualTo("RECOMMENDATION_SET_NOT_FOUND");
                });
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
