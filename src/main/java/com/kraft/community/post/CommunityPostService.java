package com.kraft.community.post;

import com.kraft.common.error.ApiErrorCode;
import com.kraft.common.error.ApiException;
import com.kraft.recommend.RecommendationSetHistoryService;
import com.kraft.recommend.RecommendationSetSummary;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.sql.SQLException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommunityPostService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int MIN_QUERY_LENGTH = 2;
    private static final int MAX_QUERY_LENGTH = 50;
    private static final int WEEKLY_WINDOW_DAYS = 7;

    private static final Logger log = LoggerFactory.getLogger(CommunityPostService.class);

    private final CommunityPostRepository communityPostRepository;
    private final CommunityPostMetricsRepository communityPostMetricsRepository;
    private final CommunityPostViewCounter communityPostViewCounter;
    private final RecommendationSetHistoryService recommendationSetHistoryService;
    private final Clock clock;
    private final Counter versionConflictCounter;
    private final Counter createdCounter;
    private final Counter viewCountDroppedCounter;
    private final MeterRegistry meterRegistry;
    // TD-022: list()가 요청마다 Timer.builder(...)를 새로 만들어 레지스트리를 조회하는
    // 대신, search 태그가 "true"/"false" 두 값뿐이므로 생성자에서 한 번씩만 등록해 둔다.
    private final Timer listTimer;
    private final Timer searchTimer;

    public CommunityPostService(CommunityPostRepository communityPostRepository,
                                 CommunityPostMetricsRepository communityPostMetricsRepository,
                                 CommunityPostViewCounter communityPostViewCounter,
                                 RecommendationSetHistoryService recommendationSetHistoryService,
                                 Clock clock,
                                 MeterRegistry meterRegistry) {
        this.communityPostRepository = communityPostRepository;
        this.communityPostMetricsRepository = communityPostMetricsRepository;
        this.communityPostViewCounter = communityPostViewCounter;
        this.recommendationSetHistoryService = recommendationSetHistoryService;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
        this.versionConflictCounter = Counter.builder("kraft_community_post_version_conflict_total")
                .description("게시글 낙관적 잠금 버전 충돌(409)로 거부된 수정 요청 수")
                .register(meterRegistry);
        this.viewCountDroppedCounter = Counter.builder("kraft_community_post_view_count_dropped_total")
                .description("DB 충돌 등으로 반영하지 못하고 버린 조회수 증가 수 — 조회 자체는 성공")
                .register(meterRegistry);
        this.createdCounter = Counter.builder("kraft_community_post_created_total")
                .description("생성된 게시글 수")
                .register(meterRegistry);
        this.listTimer = Timer.builder("kraft_community_post_list_duration_seconds")
                .description("커뮤니티 목록/검색 조회 지연")
                .tag("search", "false")
                .register(meterRegistry);
        this.searchTimer = Timer.builder("kraft_community_post_list_duration_seconds")
                .description("커뮤니티 목록/검색 조회 지연")
                .tag("search", "true")
                .register(meterRegistry);
    }

    @Transactional
    public CommunityPost create(Long ownerId, String authorNickname, String clientTokenHash,
                                 CreatePostRequest request) {
        PostCategory category = parseCategory(request.category());

        Long recommendationSetId = request.recommendationSetId();
        if (recommendationSetId != null) {
            // 소유권 교차검증: 우선 "지금 이 브라우저"의 익명 기기 토큰으로 만든 세트인지
            // 확인한다(계정 귀속 이전에도 같은 브라우저 세션이면 검증할 수 있다). 기기 토큰
            // 헤더가 없거나 그 토큰으로 못 찾으면(FORBIDDEN) 로그인 계정 소유로 재시도한다 —
            // claim(계정 귀속) 이후에는 세트의 clientTokenHash가 null로 지워져 기기 토큰
            // 조회가 항상 실패하므로, 이 폴백이 없으면 귀속된 세트를 영원히 첨부할 수 없다.
            // I-04: 소유권만 확인하면 되고 요약(디코드된 항목 목록)은 쓰지 않으므로 get()/
            // getForOwner() 대신 디코드를 건너뛰는 assert 변형을 쓴다 — 매 첨부 검증마다
            // 전체 아이템을 불필요하게 디코드해 레거시 데이터 디코드 실패에 노출되던 것을 없앤다.
            boolean ownedByDevice = false;
            if (clientTokenHash != null) {
                try {
                    recommendationSetHistoryService.assertOwnedByDevice(clientTokenHash, recommendationSetId);
                    ownedByDevice = true;
                } catch (ApiException e) {
                    if (e.getStatus() != HttpStatus.FORBIDDEN) {
                        throw e;
                    }
                }
            }
            if (!ownedByDevice) {
                recommendationSetHistoryService.assertOwnedByOwner(ownerId, recommendationSetId);
            }
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
    //
    // 조회수 증가는 CommunityPostViewCounter의 독립 트랜잭션으로 뺐다. 예전에는 둘이 한
    // 트랜잭션이라, 같은 글을 동시에 읽는 두 요청이 metrics UPDATE에서 충돌해 조회 자체가
    // 500이 됐다(운영에서 매일 발생, 상세 배경은 CommunityPostViewCounter 클래스 주석 참고).
    // 조회수는 부수 기능이므로 실패해도 본문 조회를 깨뜨리면 안 된다.
    //
    // 이 메서드에 @Transactional을 (readOnly라도) 다시 붙이지 말 것. 붙이면 counter의
    // REQUIRES_NEW가 바깥 트랜잭션 안에 중첩되어 요청 한 건이 커넥션을 2개씩 잡는다 —
    // 커넥션 풀(prod 15)이 동시 조회 8건에 고갈돼 이 핫 경로가 통째로 멈춘다. 실제로
    // CommunityPostRepositoryConcurrencyTest가 이 구성을 타임아웃으로 잡아냈다.
    // CommunityPost에는 지연 로딩 연관이 하나도 없어(기본 컬럼뿐) 준영속 반환이 안전하다.
    public CommunityPost get(Long postId, Long requesterId) {
        CommunityPost post = findById(postId);
        requireVisible(post, requesterId);
        incrementViewCountQuietly(postId);
        return post;
    }

    private void incrementViewCountQuietly(Long postId) {
        try {
            communityPostViewCounter.increment(postId);
        } catch (DataAccessException viewCountFailure) {
            // 조회수 1 손실 < 조회 500. 어떤 DB 예외든 여기서 끝내고 본문은 정상 반환한다.
            // 조용히 삼키면 회귀를 놓치므로 카운터로 남겨 대시보드에서 추적 가능하게 한다.
            viewCountDroppedCounter.increment();
            log.debug("조회수 증가 실패(무시): postId={} cause={}",
                    postId, viewCountFailure.getMostSpecificCause().toString());
        }
    }

    @Transactional(readOnly = true)
    public Page<CommunityPost> list(PostCategory category, String sort, String query, int page, int size) {
        int clampedPage = Math.max(0, page);
        int clampedSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        String normalizedQuery = normalizeAndEscapeQuery(query);
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
            sample.stop(normalizedQuery == null ? listTimer : searchTimer);
        }
    }

    @Transactional
    public CommunityPost update(Long ownerId, Long postId, UpdatePostRequest request) {
        CommunityPost post = findById(postId);
        requireOwner(post, ownerId);
        if (post.getVersion() != request.expectedVersion()) {
            throw versionConflict(null);
        }
        PostCategory category = parseCategory(request.category());
        post.update(request.title(), request.content(), category, OffsetDateTime.now(clock));
        try {
            return communityPostRepository.saveAndFlush(post);
        } catch (ObjectOptimisticLockingFailureException raceLostAfterCheck) {
            // 버전 사전 검증 이후에도 동시 수정이 끼어든 경우 — Hibernate의 자체 행 수
            // 검증이 감지한 낙관적 잠금 실패는 항상 버전 충돌이다.
            throw versionConflict(raceLostAfterCheck);
        } catch (JpaSystemException possibleRace) {
            // BE-COMM-01: JpaSystemException 자체는 Hibernate/JDBC의 광역 wrapper라
            // flush 시점의 무관한 DB 오류(커넥션 유실 등)까지 담을 수 있다. 실 MariaDB에서는
            // 버전 충돌 시 드라이버가 "Record has changed since last read"(1020)를 먼저
            // 던져 이 타입으로 온다는 사실을 Testcontainers 동시성 테스트로 확인했으므로,
            // 그 vendor code로 좁힌 경우에만 409로 변환하고 나머지는 재전파(500)한다.
            if (isRecordChangedRace(possibleRace)) {
                throw versionConflict(possibleRace);
            }
            throw possibleRace;
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
        } catch (ObjectOptimisticLockingFailureException raceLostAfterCheck) {
            throw versionConflict(raceLostAfterCheck);
        } catch (JpaSystemException possibleRace) {
            // BE-COMM-01: update()와 동일한 이유로 vendor code 1020으로 좁힌다.
            if (isRecordChangedRace(possibleRace)) {
                throw versionConflict(possibleRace);
            }
            throw possibleRace;
        }
    }

    public CommunityPostResponse toResponse(CommunityPost post) {
        CommunityPostMetrics metrics = communityPostMetricsRepository.findByPostId(post.getId()).orElse(null);
        RecommendationAttachmentView attachment = post.getRecommendationSetId() == null
                ? null
                : attachmentOrNull(post.getId(), post.getRecommendationSetId());
        return CommunityPostResponse.from(post, metrics, attachment);
    }

    // I-04: 이 메서드가 호출되는 시점에는 게시글이 이미 커밋돼 있다(create()의 @Transactional
    // 경계가 컨트롤러로 돌아오기 전에 끝난다). 첨부 렌더링만 실패한 것으로 이미 성공한 쓰기를
    // 5xx/4xx로 되돌려 보이면 안 되므로, 첨부 세트 디코드에 실패해도 게시글 자체는 정상
    // 응답하고 첨부만 비운다.
    private RecommendationAttachmentView attachmentOrNull(Long postId, Long recommendationSetId) {
        try {
            return toAttachmentView(recommendationSetHistoryService.getForAttachment(recommendationSetId));
        } catch (ApiException e) {
            log.warn("게시글 {}의 첨부 추천 세트 {} 렌더링 실패, 첨부 없이 응답합니다: {}",
                    postId, recommendationSetId, e.getMessage());
            return null;
        }
    }

    public Page<CommunityPostResponse> toResponsePage(Page<CommunityPost> posts) {
        List<Long> ids = posts.getContent().stream().map(CommunityPost::getId).toList();
        Map<Long, CommunityPostMetrics> metricsById = communityPostMetricsRepository.findAllById(ids).stream()
                .collect(java.util.stream.Collectors.toMap(CommunityPostMetrics::getPostId, m -> m));

        // KB-05: 첨부된 추천 세트가 있는 게시글마다 getForAttachment()를 개별 호출하던 N+1을
        // 없애기 위해 세트 ID를 모아 한 번에 배치 조회한다.
        List<Long> recommendationSetIds = posts.getContent().stream()
                .map(CommunityPost::getRecommendationSetId)
                .filter(java.util.Objects::nonNull)
                .toList();
        Map<Long, RecommendationSetSummary> attachmentsBySetId = recommendationSetHistoryService
                .getForAttachments(recommendationSetIds);

        return posts.map(post -> {
            // I-04: getForAttachments()가 디코드 실패한 세트를 맵에서 건너뛸 수 있으므로
            // (recommendationSetId != null인데도) null일 수 있다 — 그 게시글은 첨부 없이 표시한다.
            RecommendationSetSummary summary = post.getRecommendationSetId() == null
                    ? null
                    : attachmentsBySetId.get(post.getRecommendationSetId());
            RecommendationAttachmentView attachment = summary == null ? null : toAttachmentView(summary);
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
                .orElseThrow(() -> new ApiException(ApiErrorCode.COMMUNITY_POST_NOT_FOUND,
                        "게시글을 찾을 수 없습니다."));
    }

    /** 공개 상태가 아닌 게시글은 소유자 본인에게만 보이며 그 외에는 COMMUNITY_POST_NOT_VISIBLE이다. */
    private void requireVisible(CommunityPost post, Long requesterId) {
        if (post.getStatus() != PostStatus.PUBLISHED && !java.util.Objects.equals(post.getOwnerId(), requesterId)) {
            throw new ApiException(ApiErrorCode.COMMUNITY_POST_NOT_VISIBLE, "게시글을 찾을 수 없습니다.");
        }
    }

    private void requireOwner(CommunityPost post, Long ownerId) {
        if (!java.util.Objects.equals(post.getOwnerId(), ownerId)) {
            throw new ApiException(ApiErrorCode.COMMUNITY_POST_NOT_OWNER, "본인 게시글만 수정·삭제할 수 있습니다.");
        }
    }

    private static PostCategory parseCategory(String category) {
        try {
            return PostCategory.valueOf(category.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ApiException(ApiErrorCode.COMMUNITY_CATEGORY_INVALID,
                    "지원하지 않는 카테고리입니다: " + category);
        }
    }

    private static String normalizeAndEscapeQuery(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        String trimmed = query.trim();
        if (trimmed.length() < MIN_QUERY_LENGTH || trimmed.length() > MAX_QUERY_LENGTH) {
            throw new ApiException(ApiErrorCode.COMMUNITY_SEARCH_QUERY_INVALID,
                    "검색어는 " + MIN_QUERY_LENGTH + "~" + MAX_QUERY_LENGTH + "자여야 합니다.");
        }
        // 사용자 입력의 LIKE 메타문자를 리터럴로 취급한다. ESCAPE 문자는 저장 데이터에
        // 등장할 수 있는 역슬래시 대신 '!'로 고정해 SQL 모드(NO_BACKSLASH_ESCAPES)와
        // 무관한 계약을 만들고, escape 문자 자신을 먼저 이중화한다.
        return trimmed
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
    }

    // BE-COMM-01: MariaDB가 낙관적 잠금 충돌 시 던지는 "Record has changed since last
    // read"(error code 1020)만 버전 충돌로 인정한다. cause 체인을 타고 내려가 SQLException을
    // 찾고, 그 vendor code가 1020일 때만 true — 그 외 JpaSystemException(커넥션 유실,
    // 제약 위반 등)은 여기서 걸러지지 않아 호출부가 그대로 재전파한다. SQLState는 이 오류에서
    // 일반값("HY000")만 나와 원인을 구분하지 못해 vendor code만 본다.
    private static final int MARIADB_RECORD_CHANGED_ERROR_CODE = 1020;

    private static boolean isRecordChangedRace(JpaSystemException exception) {
        Throwable cause = exception.getCause();
        while (cause != null) {
            if (cause instanceof SQLException sqlException) {
                return sqlException.getErrorCode() == MARIADB_RECORD_CHANGED_ERROR_CODE;
            }
            cause = cause.getCause();
        }
        return false;
    }

    // B-08: update()/delete() 양쪽에서 사전 검증(버전 불일치)과 사후 경합(저장/삭제 시점에
    // 끼어든 동시 수정) 모두 같은 카운터 증가 + 같은 409 메시지를 냈다 — cause를 null로
    // 넘기면 사전 검증 경로, 실제 예외를 넘기면 사후 경합 경로다.
    private ApiException versionConflict(DataAccessException cause) {
        versionConflictCounter.increment();
        return cause == null
                ? new ApiException(ApiErrorCode.COMMUNITY_POST_VERSION_CONFLICT,
                        "다른 곳에서 먼저 수정되었습니다. 새로고침 후 다시 시도하세요.")
                : new ApiException(ApiErrorCode.COMMUNITY_POST_VERSION_CONFLICT,
                        "다른 곳에서 먼저 수정되었습니다. 새로고침 후 다시 시도하세요.", cause);
    }
}
