package com.kraft.community.post;

import com.kraft.common.error.ApiException;
import com.kraft.recommend.RecommendationSetHistoryService;
import com.kraft.recommend.RecommendationSetSummary;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommunityPostService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int MIN_QUERY_LENGTH = 2;
    private static final int MAX_QUERY_LENGTH = 50;
    private static final int WEEKLY_WINDOW_DAYS = 7;

    private final CommunityPostRepository communityPostRepository;
    private final CommunityPostMetricsRepository communityPostMetricsRepository;
    private final RecommendationSetHistoryService recommendationSetHistoryService;
    private final Clock clock;
    private final Counter versionConflictCounter;
    private final Counter createdCounter;
    private final MeterRegistry meterRegistry;

    public CommunityPostService(CommunityPostRepository communityPostRepository,
                                 CommunityPostMetricsRepository communityPostMetricsRepository,
                                 RecommendationSetHistoryService recommendationSetHistoryService,
                                 Clock clock,
                                 MeterRegistry meterRegistry) {
        this.communityPostRepository = communityPostRepository;
        this.communityPostMetricsRepository = communityPostMetricsRepository;
        this.recommendationSetHistoryService = recommendationSetHistoryService;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
        this.versionConflictCounter = Counter.builder("kraft_community_post_version_conflict_total")
                .description("게시글 낙관적 잠금 버전 충돌(409)로 거부된 수정 요청 수")
                .register(meterRegistry);
        this.createdCounter = Counter.builder("kraft_community_post_created_total")
                .description("생성된 게시글 수")
                .register(meterRegistry);
    }

    @Transactional
    public CommunityPost create(Long ownerId, String authorNickname, String clientTokenHash,
                                 CreatePostRequest request) {
        PostCategory category = parseCategory(request.category());

        Long recommendationSetId = request.recommendationSetId();
        if (recommendationSetId != null) {
            // 소유권 교차검증: 로그인 사용자가 첨부하려는 세트가 "지금 이 브라우저"의 익명
            // 기기 토큰으로 만든 세트인지 확인한다. 계정 귀속 이전에도 같은 브라우저
            // 세션이면 검증할 수 있다.
            if (clientTokenHash == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "DEVICE_TOKEN_REQUIRED",
                        "추천 세트를 첨부하려면 X-Device-Token 헤더가 필요합니다.");
            }
            recommendationSetHistoryService.get(clientTokenHash, recommendationSetId);
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        CommunityPost post = communityPostRepository.save(new CommunityPost(
                ownerId, authorNickname, request.title(), request.content(), category, recommendationSetId,
                now, now));
        communityPostMetricsRepository.save(new CommunityPostMetrics(post.getId(), now));
        createdCounter.increment();
        return post;
    }

    // B-P0-8: view_count가 항상 0으로 남던 문제 — incrementViewCount는 이미 존재했으나
    // 어디서도 호출되지 않았다. 상세 조회가 가시성 검증을 통과할 때마다 1회 증가시킨다
    // (세션/중복 방지는 이번 범위에서 과설계라 생략 — 단순 조회수).
    @Transactional
    public CommunityPost get(Long postId, Long requesterId) {
        CommunityPost post = findById(postId);
        requireVisible(post, requesterId);
        communityPostMetricsRepository.incrementViewCount(postId);
        return post;
    }

    @Transactional(readOnly = true)
    public Page<CommunityPost> list(PostCategory category, String sort, String query, int page, int size) {
        int clampedPage = Math.max(0, page);
        int clampedSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        String normalizedQuery = normalizeQuery(query);
        PageRequest pageRequest = PageRequest.of(clampedPage, clampedSize, Sort.unsorted());
        // 목록/검색 지연 p50/p95/p99 산출용 — search 태그로 순수 목록 조회와
        // 검색어 있는 조회를 구분한다(검색은 LIKE 매칭이 섞여 지연 특성이 다르다).
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            if ("weekly_popular".equalsIgnoreCase(sort)) {
                OffsetDateTime since = OffsetDateTime.now(clock).minusDays(WEEKLY_WINDOW_DAYS);
                return communityPostRepository.findWeeklyPopular(
                        category == null ? null : category.name(), normalizedQuery, since, pageRequest);
            }
            return communityPostRepository.findLatest(category, normalizedQuery, pageRequest);
        } finally {
            sample.stop(Timer.builder("kraft_community_post_list_duration_seconds")
                    .description("커뮤니티 목록/검색 조회 지연")
                    .tag("search", normalizedQuery == null ? "false" : "true")
                    .register(meterRegistry));
        }
    }

    @Transactional
    public CommunityPost update(Long ownerId, Long postId, UpdatePostRequest request) {
        CommunityPost post = findById(postId);
        requireOwner(post, ownerId);
        if (post.getVersion() != request.expectedVersion()) {
            throw versionConflict(null);
        }
        post.update(request.title(), request.content(), OffsetDateTime.now(clock));
        try {
            return communityPostRepository.saveAndFlush(post);
        } catch (DataAccessException raceLostAfterCheck) {
            // 버전 사전 검증 이후에도 동시 수정이 끼어든 경우 — 광역 예외 대신
            // 이 리소스에 특정된 409로 변환한다. Hibernate의 자체 행 수 검증은
            // ObjectOptimisticLockingFailureException으로 오지만, 실 MariaDB에서는 드라이버가
            // "Record has changed since last read"(1020)를 먼저 던져 JpaSystemException으로도
            // 온다는 사실을 Testcontainers 동시성 테스트로 확인했다 — 두 경로 모두 이 시점에서는
            // 버전 경합 외의 원인이 있을 수 없으므로 DataAccessException을 폭넓게 잡는다.
            throw versionConflict(raceLostAfterCheck);
        }
    }

    /**
     * 일반 사용자의 삭제 — 하드 삭제 대신 {@code HIDDEN_BY_AUTHOR}로 상태만 바꾼다.
     * 본문·참조·감사 관계는 그대로 유지되고, 이후 목록/비소유자 조회에서만 제외된다.
     */
    @Transactional
    public void delete(Long ownerId, Long postId, long expectedVersion) {
        CommunityPost post = findById(postId);
        requireOwner(post, ownerId);
        if (post.getVersion() != expectedVersion) {
            throw versionConflict(null);
        }
        try {
            post.hideByAuthor(OffsetDateTime.now(clock));
            communityPostRepository.saveAndFlush(post);
        } catch (DataAccessException raceLostAfterCheck) {
            throw versionConflict(raceLostAfterCheck);
        }
    }

    public CommunityPostResponse toResponse(CommunityPost post) {
        CommunityPostMetrics metrics = communityPostMetricsRepository.findByPostId(post.getId()).orElse(null);
        RecommendationAttachmentView attachment = post.getRecommendationSetId() == null
                ? null
                : toAttachmentView(recommendationSetHistoryService.getForAttachment(post.getRecommendationSetId()));
        return CommunityPostResponse.from(post, metrics, attachment);
    }

    public Page<CommunityPostResponse> toResponsePage(Page<CommunityPost> posts) {
        List<Long> ids = posts.getContent().stream().map(CommunityPost::getId).toList();
        Map<Long, CommunityPostMetrics> metricsById = communityPostMetricsRepository.findAllById(ids).stream()
                .collect(java.util.stream.Collectors.toMap(CommunityPostMetrics::getPostId, m -> m));
        return posts.map(post -> {
            RecommendationAttachmentView attachment = post.getRecommendationSetId() == null
                    ? null
                    : toAttachmentView(recommendationSetHistoryService.getForAttachment(post.getRecommendationSetId()));
            return CommunityPostResponse.from(post, metricsById.get(post.getId()), attachment);
        });
    }

    private static RecommendationAttachmentView toAttachmentView(RecommendationSetSummary summary) {
        return new RecommendationAttachmentView(
                summary.id(), summary.strategy(), summary.algorithmVersion(), summary.historyThroughRound(),
                summary.exclusionPolicyVersion(),
                summary.items());
    }

    private CommunityPost findById(Long postId) {
        return communityPostRepository.findById(postId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "COMMUNITY_POST_NOT_FOUND",
                        "게시글을 찾을 수 없습니다."));
    }

    /** 공개 상태가 아닌 게시글은 소유자 본인에게만 보이며 그 외에는 COMMUNITY_POST_NOT_VISIBLE이다. */
    private void requireVisible(CommunityPost post, Long requesterId) {
        if (post.getStatus() != PostStatus.PUBLISHED && !post.getOwnerId().equals(requesterId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "COMMUNITY_POST_NOT_VISIBLE", "게시글을 찾을 수 없습니다.");
        }
    }

    private void requireOwner(CommunityPost post, Long ownerId) {
        if (!post.getOwnerId().equals(ownerId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "COMMUNITY_POST_NOT_OWNER", "본인 게시글만 수정·삭제할 수 있습니다.");
        }
    }

    private static PostCategory parseCategory(String category) {
        try {
            return PostCategory.valueOf(category.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "COMMUNITY_CATEGORY_INVALID",
                    "지원하지 않는 카테고리입니다: " + category);
        }
    }

    private static String normalizeQuery(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        String trimmed = query.trim();
        if (trimmed.length() < MIN_QUERY_LENGTH || trimmed.length() > MAX_QUERY_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "COMMUNITY_SEARCH_QUERY_INVALID",
                    "검색어는 " + MIN_QUERY_LENGTH + "~" + MAX_QUERY_LENGTH + "자여야 합니다.");
        }
        return trimmed;
    }

    // B-08: update()/delete() 양쪽에서 사전 검증(버전 불일치)과 사후 경합(저장/삭제 시점에
    // 끼어든 동시 수정) 모두 같은 카운터 증가 + 같은 409 메시지를 냈다 — cause를 null로
    // 넘기면 사전 검증 경로, 실제 예외를 넘기면 사후 경합 경로다.
    private ApiException versionConflict(DataAccessException cause) {
        versionConflictCounter.increment();
        return cause == null
                ? new ApiException(HttpStatus.CONFLICT, "COMMUNITY_POST_VERSION_CONFLICT",
                        "다른 곳에서 먼저 수정되었습니다. 새로고침 후 다시 시도하세요.")
                : new ApiException(HttpStatus.CONFLICT, "COMMUNITY_POST_VERSION_CONFLICT",
                        "다른 곳에서 먼저 수정되었습니다. 새로고침 후 다시 시도하세요.", cause);
    }
}
