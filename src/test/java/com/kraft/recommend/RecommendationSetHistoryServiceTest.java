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
                    1189, null, null, OffsetDateTime.now());
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
                new LottoNumberCodec(), attachmentChecker);
    }

    @Test
    @DisplayName("추천 세트를 저장하고 항목도 함께 저장한다")
    void persist_savesSetAndItems() {
        given(recommendationSetRepository.save(org.mockito.ArgumentMatchers.any()))
                .willReturn(setEntity(1L, TOKEN_HASH));

        RecommendationItemView item = new RecommendationItemView(1, List.of(1, 2, 3, 4, 5, 6), null, List.of());
        Long id = service.persist(TOKEN_HASH, "random", "uniform-random-v1", 1189,
                List.of(), List.of(), List.of(item), OffsetDateTime.now());

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
}
