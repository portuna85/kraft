package com.kraft.community.post;

import com.kraft.common.error.ApiException;
import com.kraft.recommend.RecommendationSetHistoryService;
import com.kraft.recommend.RecommendationSetSummary;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("커뮤니티 게시글 서비스 단위 테스트")
class CommunityPostServiceTest {

    @Mock
    private CommunityPostRepository communityPostRepository;

    @Mock
    private CommunityPostMetricsRepository communityPostMetricsRepository;

    @Mock
    private RecommendationSetHistoryService recommendationSetHistoryService;

    private CommunityPostService service;

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
        service = new CommunityPostService(communityPostRepository, communityPostMetricsRepository,
                recommendationSetHistoryService, CLOCK, new SimpleMeterRegistry());
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
    @DisplayName("추천 세트를 첨부하려는데 기기 토큰이 없으면 400 DEVICE_TOKEN_REQUIRED로 거부된다")
    void create_attachmentWithoutDeviceToken_throwsApiException() {
        assertThatThrownBy(() -> service.create(1L, "글쓴이", null,
                new CreatePostRequest("제목", "내용", "GENERAL", 5L)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(apiEx.getCode()).isEqualTo("DEVICE_TOKEN_REQUIRED");
                });
    }

    @Test
    @DisplayName("추천 세트 첨부 시 기기 토큰 소유권을 교차검증한다")
    void create_withAttachment_verifiesOwnershipThenPersists() {
        given(recommendationSetHistoryService.get("hash-1", 5L))
                .willReturn(new RecommendationSetSummary(5L, "random", "uniform-random-v1", 1189,
                        List.of(), List.of(), OffsetDateTime.now(CLOCK), List.of()));
        given(communityPostRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        CommunityPost post = service.create(1L, "글쓴이", "hash-1",
                new CreatePostRequest("제목", "내용", "RECOMMENDATION_SHARE", 5L));

        assertThat(post.getRecommendationSetId()).isEqualTo(5L);
        assertThat(post.getCategory()).isEqualTo(PostCategory.RECOMMENDATION_SHARE);
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
    @DisplayName("기본 정렬(latest)은 최신순 쿼리로 위임한다")
    void list_defaultSort_delegatesToLatestQuery() {
        Page<CommunityPost> page = new PageImpl<>(List.of());
        given(communityPostRepository.findLatest(eq(PostCategory.GENERAL), isNull(), any())).willReturn(page);

        Page<CommunityPost> result = service.list(PostCategory.GENERAL, "latest", null, 0, 20);

        assertThat(result).isSameAs(page);
    }

    @Test
    @DisplayName("toResponse는 집계와 첨부 정보를 함께 채운다")
    void toResponse_withMetricsAndAttachment_populatesResponse() {
        CommunityPost post = postEntity(1L, 1L, PostStatus.PUBLISHED);
        setRecommendationSetId(post, 5L);
        CommunityPostMetrics metrics = new CommunityPostMetrics(1L, OffsetDateTime.now(CLOCK));
        given(communityPostMetricsRepository.findByPostId(1L)).willReturn(Optional.of(metrics));
        given(recommendationSetHistoryService.getForAttachment(5L)).willReturn(
                new RecommendationSetSummary(5L, "balanced", "balanced-v1", 1189, List.of(), List.of(),
                        OffsetDateTime.now(CLOCK), List.of()));

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
