package com.kraft.community.post;

import com.kraft.common.error.ApiErrorCode;
import com.kraft.common.error.ApiException;
import com.kraft.recommend.RecommendationSetHistoryService;
import com.kraft.recommend.RecommendationSetSummary;
import com.kraft.recommend.RecommendationStrategy;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.orm.jpa.JpaSystemException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("커뮤니티 게시글 서비스 단위 테스트")
class CommunityPostServiceTest {

    @Mock
    private CommunityPostRepository communityPostRepository;

    @Mock
    private CommunityPostMetricsRepository communityPostMetricsRepository;

    @Mock
    private CommunityPostViewCounter communityPostViewCounter;

    @Mock
    private RecommendationSetHistoryService recommendationSetHistoryService;

    private CommunityPostService service;
    private SimpleMeterRegistry meterRegistry;

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-28T00:00:00Z"), ZoneOffset.UTC);

    private static CommunityPost postEntity(long id, Long ownerId, PostStatus status) {
        try {
            CommunityPost post = new CommunityPost(ownerId, "글쓴이", "제목", "내용", PostCategory.GENERAL, null,
                    OffsetDateTime.now(CLOCK), OffsetDateTime.now(CLOCK));
            var idField = CommunityPost.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(post, id);
            var statusField = CommunityPost.class.getDeclaredField("status");
            statusField.setAccessible(true);
            statusField.set(post, status);
            return post;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        service = new CommunityPostService(communityPostRepository, communityPostMetricsRepository,
                communityPostViewCounter, recommendationSetHistoryService, CLOCK, meterRegistry);
    }

    @Test
    @DisplayName("유효하지 않은 카테고리는 COMMUNITY_CATEGORY_INVALID로 거부된다")
    void create_invalidCategory_throwsApiException() {
        assertThatThrownBy(() -> service.create(1L, "글쓴이", null,
                new CreatePostRequest("제목", "내용", "NOT_A_CATEGORY", null)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(apiEx.getCode()).isEqualTo("COMMUNITY_CATEGORY_INVALID");
                });
    }

    @Test
    @DisplayName("추천 세트 첨부 시 기기 토큰 소유권을 교차검증한다")
    void create_withAttachment_verifiesOwnershipThenPersists() {
        // I-04: assertOwnedByDevice는 void라 기본 목 동작(아무 것도 던지지 않음)이 곧 "소유권
        // 확인됨"이라 별도 스텁이 필요 없다.
        given(communityPostRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        CommunityPost post = service.create(1L, "글쓴이", "hash-1",
                new CreatePostRequest("제목", "내용", "RECOMMENDATION_SHARE", 5L));

        assertThat(post.getRecommendationSetId()).isEqualTo(5L);
        assertThat(post.getCategory()).isEqualTo(PostCategory.RECOMMENDATION_SHARE);
        verify(recommendationSetHistoryService, never()).assertOwnedByOwner(anyLong(), eq(5L));
    }

    // C-2-5: claim(계정 귀속) 이후 세트는 clientTokenHash가 null로 지워져 기기 토큰
    // 조회가 항상 FORBIDDEN을 던진다 — ownerId 기준 폴백이 없으면 귀속된 세트를
    // 영원히 첨부할 수 없었다.
    @Test
    @DisplayName("기기 토큰 헤더가 없으면 곧바로 계정 소유권으로 검증한다")
    void create_attachmentWithoutDeviceToken_fallsBackToOwnerOwnership() {
        given(communityPostRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        CommunityPost post = service.create(1L, "글쓴이", null,
                new CreatePostRequest("제목", "내용", "RECOMMENDATION_SHARE", 5L));

        assertThat(post.getRecommendationSetId()).isEqualTo(5L);
    }

    @Test
    @DisplayName("기기 토큰 소유권 검증이 FORBIDDEN이면 계정 소유권으로 재시도한다(귀속된 세트)")
    void create_attachmentDeviceOwnershipForbidden_fallsBackToOwnerOwnership() {
        willThrow(new ApiException(ApiErrorCode.RECOMMENDATION_SET_NOT_OWNED, "이 추천 세트에 대한 권한이 없습니다."))
                .given(recommendationSetHistoryService).assertOwnedByDevice("hash-1", 5L);
        given(communityPostRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        CommunityPost post = service.create(1L, "글쓴이", "hash-1",
                new CreatePostRequest("제목", "내용", "RECOMMENDATION_SHARE", 5L));

        assertThat(post.getRecommendationSetId()).isEqualTo(5L);
    }

    @Test
    @DisplayName("기기·계정 소유권이 둘 다 아니면 FORBIDDEN을 그대로 전파한다")
    void create_attachmentOwnershipFailsBothWays_throwsApiException() {
        willThrow(new ApiException(ApiErrorCode.RECOMMENDATION_SET_NOT_OWNED, "이 추천 세트에 대한 권한이 없습니다."))
                .given(recommendationSetHistoryService).assertOwnedByDevice("hash-1", 5L);
        willThrow(new ApiException(ApiErrorCode.RECOMMENDATION_SET_NOT_OWNED, "이 추천 세트에 대한 권한이 없습니다."))
                .given(recommendationSetHistoryService).assertOwnedByOwner(1L, 5L);

        assertThatThrownBy(() -> service.create(1L, "글쓴이", "hash-1",
                new CreatePostRequest("제목", "내용", "RECOMMENDATION_SHARE", 5L)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(apiEx.getCode()).isEqualTo("RECOMMENDATION_SET_NOT_OWNED");
                });
        verify(communityPostRepository, never()).save(any());
    }

    @Test
    @DisplayName("삭제는 하드 삭제가 아니라 상태를 HIDDEN_BY_AUTHOR로 바꾼다")
    void delete_softDeletesInsteadOfHardDelete() {
        CommunityPost post = postEntity(1L, 1L, PostStatus.PUBLISHED);
        given(communityPostRepository.findById(1L)).willReturn(Optional.of(post));
        given(communityPostRepository.saveAndFlush(post)).willReturn(post);

        service.delete(1L, 1L, 0L);

        assertThat(post.getStatus()).isEqualTo(PostStatus.HIDDEN_BY_AUTHOR);
        org.mockito.Mockito.verify(communityPostRepository, org.mockito.Mockito.never()).delete(any());
    }

    @Test
    @DisplayName("BE-COMM-01: 삭제 중 vendor code 1020(레코드 변경됨)은 409 버전 충돌로 변환된다")
    void delete_jpaSystemExceptionWithVendorCode1020_convertsToVersionConflict() {
        CommunityPost post = postEntity(1L, 1L, PostStatus.PUBLISHED);
        given(communityPostRepository.findById(1L)).willReturn(Optional.of(post));
        given(communityPostRepository.saveAndFlush(post)).willThrow(new JpaSystemException(new RuntimeException(
                new java.sql.SQLException("Record has changed since last read in table 'community_posts'", "HY000",
                        1020))));

        assertThatThrownBy(() -> service.delete(1L, 1L, 0L))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(apiEx.getCode()).isEqualTo("COMMUNITY_POST_VERSION_CONFLICT");
                });
    }

    @Test
    @DisplayName("BE-COMM-01: 삭제 중 무관한 JpaSystemException(커넥션 유실 등)은 409로 위장되지 않고 재전파된다")
    void delete_unrelatedJpaSystemException_propagatesInsteadOfBeingMisclassified() {
        CommunityPost post = postEntity(1L, 1L, PostStatus.PUBLISHED);
        given(communityPostRepository.findById(1L)).willReturn(Optional.of(post));
        given(communityPostRepository.saveAndFlush(post)).willThrow(new JpaSystemException(new RuntimeException(
                new java.sql.SQLException("Connection is closed", "08003", 0))));

        assertThatThrownBy(() -> service.delete(1L, 1L, 0L))
                .isInstanceOf(JpaSystemException.class)
                .isNotInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("BE-COMM-01: 삭제 중 SQLException이 없는 JpaSystemException(예: 제약 위반 래핑)도 재전파된다")
    void delete_jpaSystemExceptionWithoutSqlExceptionCause_propagates() {
        CommunityPost post = postEntity(1L, 1L, PostStatus.PUBLISHED);
        given(communityPostRepository.findById(1L)).willReturn(Optional.of(post));
        given(communityPostRepository.saveAndFlush(post))
                .willThrow(new JpaSystemException(new RuntimeException("unrelated failure")));

        assertThatThrownBy(() -> service.delete(1L, 1L, 0L))
                .isInstanceOf(JpaSystemException.class)
                .isNotInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("비공개 상태 게시글은 소유자가 아니면 COMMUNITY_POST_NOT_VISIBLE로 거부된다")
    void get_hiddenPost_nonOwner_throwsNotVisible() {
        CommunityPost post = postEntity(1L, 1L, PostStatus.HIDDEN_BY_AUTHOR);
        given(communityPostRepository.findById(1L)).willReturn(Optional.of(post));

        assertThatThrownBy(() -> service.get(1L, 2L))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(apiEx.getCode()).isEqualTo("COMMUNITY_POST_NOT_VISIBLE");
                });
    }

    @Test
    @DisplayName("비공개 상태 게시글도 소유자 본인에게는 보인다")
    void get_hiddenPost_owner_isVisible() {
        CommunityPost post = postEntity(1L, 1L, PostStatus.HIDDEN_BY_AUTHOR);
        given(communityPostRepository.findById(1L)).willReturn(Optional.of(post));

        CommunityPost result = service.get(1L, 1L);

        assertThat(result.getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("공개 게시글은 익명(비로그인) 요청자에게도 보인다")
    void get_publishedPost_anonymous_isVisible() {
        CommunityPost post = postEntity(1L, 1L, PostStatus.PUBLISHED);
        given(communityPostRepository.findById(1L)).willReturn(Optional.of(post));

        CommunityPost result = service.get(1L, null);

        assertThat(result.getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("B-P0-8: 가시성 검증을 통과한 상세 조회는 view_count를 1 증가시킨다")
    void get_visiblePost_incrementsViewCount() {
        CommunityPost post = postEntity(1L, 1L, PostStatus.PUBLISHED);
        given(communityPostRepository.findById(1L)).willReturn(Optional.of(post));

        service.get(1L, null);

        verify(communityPostViewCounter).increment(1L);
    }

    @Test
    @DisplayName("B-P0-8: 가시성 검증에 실패하면 view_count를 증가시키지 않는다")
    void get_notVisiblePost_doesNotIncrementViewCount() {
        CommunityPost post = postEntity(1L, 1L, PostStatus.HIDDEN_BY_AUTHOR);
        given(communityPostRepository.findById(1L)).willReturn(Optional.of(post));

        assertThatThrownBy(() -> service.get(1L, 2L)).isInstanceOf(ApiException.class);

        verify(communityPostViewCounter, never()).increment(any());
    }

    @Test
    @DisplayName("조회수 증가가 DB 충돌로 실패해도 상세 조회는 성공하고 드롭 카운터만 오른다")
    void get_viewCountIncrementFails_stillReturnsPost() {
        CommunityPost post = postEntity(1L, 1L, PostStatus.PUBLISHED);
        given(communityPostRepository.findById(1L)).willReturn(Optional.of(post));
        // 운영에서 실제로 관측된 예외 형태 — MariaDB의 ER_CHECKREAD("Record has changed
        // since last read")가 JpaSystemException으로 올라온다.
        willThrow(new JpaSystemException(new RuntimeException(
                "Record has changed since last read in table 'community_post_metrics'")))
                .given(communityPostViewCounter).increment(1L);

        CommunityPost result = service.get(1L, null);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(meterRegistry.counter("kraft_community_post_view_count_dropped_total").count())
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("검색어 길이가 범위를 벗어나면 COMMUNITY_SEARCH_QUERY_INVALID로 거부된다")
    void list_queryTooShort_throwsApiException() {
        assertThatThrownBy(() -> service.list(null, "latest", "a", 0, 20))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(apiEx.getCode()).isEqualTo("COMMUNITY_SEARCH_QUERY_INVALID");
                });
    }

    @Test
    @DisplayName("sort=weekly_popular이면 최근 7일 인기 쿼리로 위임한다")
    void list_weeklyPopularSort_delegatesToWeeklyPopularQuery() {
        Page<CommunityPost> page = new PageImpl<>(List.of());
        given(communityPostRepository.findWeeklyPopular(isNull(), isNull(), any(OffsetDateTime.class), any()))
                .willReturn(page);

        Page<CommunityPost> result = service.list(null, "weekly_popular", null, 0, 20);

        assertThat(result).isSameAs(page);
    }

    @Test
    @DisplayName("검색어의 %, _, !는 최신순 쿼리에서 LIKE 와일드카드가 아닌 리터럴로 이스케이프된다")
    void list_latestSort_escapesLikeMetacharacters() {
        Page<CommunityPost> page = new PageImpl<>(List.of());
        given(communityPostRepository.findLatest(isNull(), eq("50!%!_sale!!"), any())).willReturn(page);

        Page<CommunityPost> result = service.list(null, "latest", " 50%_sale! ", 0, 20);

        assertThat(result).isSameAs(page);
    }

    @Test
    @DisplayName("검색어의 %, _, !는 주간 인기 쿼리에서도 동일하게 이스케이프된다")
    void list_weeklyPopularSort_escapesLikeMetacharacters() {
        Page<CommunityPost> page = new PageImpl<>(List.of());
        given(communityPostRepository.findWeeklyPopular(
                isNull(), eq("50!%!_sale!!"), any(OffsetDateTime.class), any()))
                .willReturn(page);

        Page<CommunityPost> result = service.list(null, "weekly_popular", " 50%_sale! ", 0, 20);

        assertThat(result).isSameAs(page);
    }

    @Test
    @DisplayName("기본 정렬(latest)은 최신순 쿼리로 위임한다")
    void list_defaultSort_delegatesToLatestQuery() {
        Page<CommunityPost> page = new PageImpl<>(List.of());
        given(communityPostRepository.findLatest(eq(PostCategory.GENERAL), isNull(), any())).willReturn(page);

        Page<CommunityPost> result = service.list(PostCategory.GENERAL, "latest", null, 0, 20);

        assertThat(result).isSameAs(page);
    }

    @Test
    @DisplayName("TD-022: 검색어 유무에 따라 같은 이름·다른 search 태그의 타이머가 조회된다")
    void list_durationTimers_registeredWithStableNameAndSearchTag() {
        given(communityPostRepository.findLatest(any(), any(), any())).willReturn(new PageImpl<>(List.of()));

        service.list(null, "latest", null, 0, 20);
        service.list(null, "latest", "검색어", 0, 20);

        assertThat(meterRegistry.get("kraft_community_post_list_duration_seconds")
                .tag("search", "false").timer().count()).isEqualTo(1);
        assertThat(meterRegistry.get("kraft_community_post_list_duration_seconds")
                .tag("search", "true").timer().count()).isEqualTo(1);
    }

    @Test
    @DisplayName("toResponse는 집계와 첨부 정보를 함께 채운다")
    void toResponse_withMetricsAndAttachment_populatesResponse() {
        CommunityPost post = postEntity(1L, 1L, PostStatus.PUBLISHED);
        setRecommendationSetId(post, 5L);
        CommunityPostMetrics metrics = new CommunityPostMetrics(1L, OffsetDateTime.now(CLOCK));
        given(communityPostMetricsRepository.findByPostId(1L)).willReturn(Optional.of(metrics));
        given(recommendationSetHistoryService.getForAttachment(5L)).willReturn(
                new RecommendationSetSummary(5L, RecommendationStrategy.BALANCED, "balanced-v1", 1189,
                        "historical-first-prize-v1", List.of(), List.of(), OffsetDateTime.now(CLOCK), List.of()));

        CommunityPostResponse response = service.toResponse(post);

        assertThat(response.recommendationAttachment()).isNotNull();
        assertThat(response.recommendationAttachment().setId()).isEqualTo(5L);
        assertThat(response.likeCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("첨부가 없으면 recommendationAttachment은 null이다")
    void toResponse_withoutAttachment_attachmentIsNull() {
        CommunityPost post = postEntity(1L, 1L, PostStatus.PUBLISHED);
        given(communityPostMetricsRepository.findByPostId(1L)).willReturn(Optional.empty());

        CommunityPostResponse response = service.toResponse(post);

        assertThat(response.recommendationAttachment()).isNull();
        assertThat(response.likeCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("toResponsePage는 여러 게시글의 집계를 한 번에 배치 조회한다")
    void toResponsePage_batchLoadsMetrics() {
        CommunityPost post1 = postEntity(1L, 1L, PostStatus.PUBLISHED);
        CommunityPost post2 = postEntity(2L, 1L, PostStatus.PUBLISHED);
        Page<CommunityPost> page = new PageImpl<>(List.of(post1, post2));
        given(communityPostMetricsRepository.findAllById(List.of(1L, 2L))).willReturn(List.of(
                new CommunityPostMetrics(1L, OffsetDateTime.now(CLOCK)),
                new CommunityPostMetrics(2L, OffsetDateTime.now(CLOCK))));

        Page<CommunityPostResponse> result = service.toResponsePage(page);

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent()).allMatch(r -> r.recommendationAttachment() == null);
        verify(recommendationSetHistoryService, never()).getForAttachment(anyLong());
    }

    @Test
    @DisplayName("KB-05: toResponsePage는 첨부된 추천 세트를 게시글마다 개별 조회하지 않고 한 번에 배치 조회한다")
    void toResponsePage_batchLoadsAttachments() {
        CommunityPost post1 = postEntity(1L, 1L, PostStatus.PUBLISHED);
        setRecommendationSetId(post1, 5L);
        CommunityPost post2 = postEntity(2L, 1L, PostStatus.PUBLISHED);
        setRecommendationSetId(post2, 6L);
        Page<CommunityPost> page = new PageImpl<>(List.of(post1, post2));
        given(communityPostMetricsRepository.findAllById(List.of(1L, 2L))).willReturn(List.of());
        given(recommendationSetHistoryService.getForAttachments(List.of(5L, 6L))).willReturn(Map.of(
                5L, new RecommendationSetSummary(5L, RecommendationStrategy.BALANCED, "balanced-v1", 1189, "historical-first-prize-v1",
                        List.of(), List.of(), OffsetDateTime.now(CLOCK), List.of()),
                6L, new RecommendationSetSummary(6L, RecommendationStrategy.BALANCED, "balanced-v1", 1189, "historical-first-prize-v1",
                        List.of(), List.of(), OffsetDateTime.now(CLOCK), List.of())));

        Page<CommunityPostResponse> result = service.toResponsePage(page);

        assertThat(result.getContent()).extracting(r -> r.recommendationAttachment().setId())
                .containsExactly(5L, 6L);
        verify(recommendationSetHistoryService, never()).getForAttachment(anyLong());
        verify(recommendationSetHistoryService).getForAttachments(List.of(5L, 6L));
    }

    private static void setRecommendationSetId(CommunityPost post, Long recommendationSetId) {
        try {
            var field = CommunityPost.class.getDeclaredField("recommendationSetId");
            field.setAccessible(true);
            field.set(post, recommendationSetId);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
