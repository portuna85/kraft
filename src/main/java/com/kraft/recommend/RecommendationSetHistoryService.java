package com.kraft.recommend;

import com.kraft.common.error.ApiErrorCode;
import com.kraft.common.error.ApiException;
import com.kraft.common.lotto.LottoBitmask;
import com.kraft.common.lotto.LottoNumberCodec;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 추천 세트의 영속화·조회·삭제를 담당한다. 생성 로직({@link LottoRecommendationService})과
 * 분리한 이유는 두 가지다: 책임이 다르고(생성 알고리즘 vs 저장소 접근), 별도 빈으로 둬야
 * {@code @Transactional}이 프록시를 거쳐 실제로 적용된다(같은 빈 안에서 this.method()로
 * 호출하면 Spring AOP 프록시를 우회해 트랜잭션이 걸리지 않는다).
 */
@Service
@Transactional
public class RecommendationSetHistoryService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationSetHistoryService.class);

    private final RecommendationSetRepository recommendationSetRepository;
    private final RecommendationItemRepository recommendationItemRepository;
    private final LottoNumberCodec lottoNumberCodec;

    // KB-05: 목록 API가 무제한 배열을 반환하던 문제를 막는 상한 — CommunityPostService와 동일한 값.
    private static final int MAX_PAGE_SIZE = 50;

    public RecommendationSetHistoryService(RecommendationSetRepository recommendationSetRepository,
                                            RecommendationItemRepository recommendationItemRepository,
                                            LottoNumberCodec lottoNumberCodec) {
        this.recommendationSetRepository = recommendationSetRepository;
        this.recommendationItemRepository = recommendationItemRepository;
        this.lottoNumberCodec = lottoNumberCodec;
    }

    public Long persist(String clientTokenHash, String strategy, String algorithmVersion, int historyThroughRound,
                         String exclusionPolicyVersion,
                         List<Integer> locked, List<Integer> excluded, List<RecommendationItemView> items,
                         OffsetDateTime createdAt) {
        return persistInternal(new RecommendationSet(
                clientTokenHash, strategy, algorithmVersion, historyThroughRound, exclusionPolicyVersion,
                lockedStorage(locked), excludedStorage(excluded), createdAt), items);
    }

    /**
     * KF-01(docs/improvement.md): 로그인 계정 소유로 직접 영속화한다 — {@link #persist}와
     * 달리 device-token 해시가 아니라 인증된 계정 id로 소유권을 정한다. 호출부
     * ({@code LottoRecommendationService.recommendForOwner})가 서버가 확정한
     * {@code ownerUserId}만 넘겨야 한다 — 클라이언트가 보낸 계정 식별자를 여기로
     * 흘려보내면 안 된다.
     */
    public Long persistForOwner(Long ownerUserId, String strategy, String algorithmVersion, int historyThroughRound,
                                 String exclusionPolicyVersion,
                                 List<Integer> locked, List<Integer> excluded, List<RecommendationItemView> items,
                                 OffsetDateTime createdAt) {
        return persistInternal(new RecommendationSet(
                ownerUserId, strategy, algorithmVersion, historyThroughRound, exclusionPolicyVersion,
                lockedStorage(locked), excludedStorage(excluded), createdAt), items);
    }

    private String lockedStorage(List<Integer> locked) {
        return locked.isEmpty() ? null : lottoNumberCodec.toStorageValueSubset(locked);
    }

    private String excludedStorage(List<Integer> excluded) {
        return excluded.isEmpty() ? null : lottoNumberCodec.toStorageValueSubset(excluded);
    }

    private Long persistInternal(RecommendationSet set, List<RecommendationItemView> items) {
        RecommendationSet saved = recommendationSetRepository.save(set);

        // M-8: 항목마다 save()를 개별 호출하면 N+1이 되고, application-prod.yml의
        // hibernate.jdbc.batch_size도 개별 save()에는 적용되지 않는다 — saveAll()로 한 번에
        // 묶어야 배치 insert가 실제로 일어난다.
        List<RecommendationItem> entities = items.stream()
                .map(item -> new RecommendationItem(
                        saved.getId(), item.position(), lottoNumberCodec.toStorageValueSubset(item.numbers()),
                        LottoBitmask.of(item.numbers()), item.score(),
                        item.explanationCodes().isEmpty()
                                ? null
                                : item.explanationCodes().stream().map(Enum::name).collect(Collectors.joining(","))))
                .toList();
        recommendationItemRepository.saveAll(entities);
        return saved.getId();
    }

    @Transactional(readOnly = true)
    public Page<RecommendationSetSummary> list(String clientTokenHash, int page, int size) {
        Page<RecommendationSet> sets = recommendationSetRepository.findByClientTokenHashOrderByCreatedAtDescIdDesc(
                clientTokenHash, pageRequest(page, size));
        return mapWithBatchedItems(sets);
    }

    /**
     * I-04: {@link #get(String, long)}과 동일한 소유권 검증이지만 {@link #toSummary}의 항목
     * 디코드를 건너뛴다. 호출부(예: 게시글 첨부 시 소유권만 확인하고 요약은 버리는 경로)가
     * 디코드 결과를 쓰지 않는데도 매번 전체 아이템을 조회·디코드해 레거시 데이터 디코드
     * 실패에 불필요하게 노출되던 것을 없앤다.
     */
    @Transactional(readOnly = true)
    public void assertOwnedByDevice(String clientTokenHash, long id) {
        findOwnedByDevice(clientTokenHash, id);
    }

    @Transactional(readOnly = true)
    public Page<RecommendationSetSummary> listForOwner(Long ownerUserId, int page, int size) {
        Page<RecommendationSet> sets = recommendationSetRepository.findByOwnerUserIdOrderByCreatedAtDescIdDesc(
                ownerUserId, pageRequest(page, size));
        return mapWithBatchedItems(sets);
    }

    private static PageRequest pageRequest(int page, int size) {
        int clampedPage = Math.max(0, page);
        int clampedSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        return PageRequest.of(clampedPage, clampedSize, Sort.unsorted());
    }

    // KB-05: list()/listForOwner()도 페이지 안의 세트마다 아이템을 개별 조회하면 N+1이 되므로,
    // 페이지 내용의 세트 ID를 모아 IN 배치 조회 1회로 그룹핑한 뒤 세트당 O(1) 맵 조회로 요약을 만든다.
    private Page<RecommendationSetSummary> mapWithBatchedItems(Page<RecommendationSet> sets) {
        List<RecommendationSet> content = sets.getContent();
        if (content.isEmpty()) {
            return sets.map(this::toSummary);
        }
        List<Long> setIds = content.stream().map(RecommendationSet::getId).toList();
        Map<Long, List<RecommendationItemView>> itemsBySetId = itemViewsBySetId(setIds);
        return sets.map(set -> toSummary(set, itemsBySetId.getOrDefault(set.getId(), List.of())));
    }

    /** I-04: {@link #assertOwnedByDevice(String, long)}의 계정 소유 버전. */
    @Transactional(readOnly = true)
    public void assertOwnedByOwner(Long ownerUserId, long id) {
        findOwnedByOwner(ownerUserId, id);
    }

    /**
     * 로그인 계정 귀속 — 추천 세트는 각각이 독립된 이력
     * 레코드라 저장 번호와 달리 중복 제거 없이 전부 옮긴다.
     *
     * DATA-REC-01(docs/improvement.md): 세트 전체를 엔티티로 읽어 메모리에서
     * 순회하지 않는다 — 단일 벌크 UPDATE로 처리한다(RecommendationSetRepository의
     * claimAllByClientTokenHash 주석 참고).
     */
    public int claimAll(String clientTokenHash, Long ownerUserId, OffsetDateTime claimedAt) {
        return recommendationSetRepository.claimAllByClientTokenHash(clientTokenHash, ownerUserId, claimedAt);
    }

    /**
     * 소유권 검증 없이 세트를 조회한다 — 커뮤니티 게시글에 첨부된 추천 세트를 방문자에게
     * 보여줄 때 쓴다. 게시글이 공개된 이상 첨부된 추천 정보도 공개 데이터로 취급한다
     * 첨부는 생성 당시 결과의 불변 스냅샷으로 유지한다.
     */
    @Transactional(readOnly = true)
    public RecommendationSetSummary getForAttachment(long id) {
        RecommendationSet set = recommendationSetRepository.findById(id)
                .orElseThrow(() -> new ApiException(ApiErrorCode.RECOMMENDATION_SET_NOT_FOUND,
                        "추천 세트를 찾을 수 없습니다."));
        return toSummary(set);
    }

    /**
     * KB-05: {@link #getForAttachment(long)}의 배치 버전 — 게시글 목록 렌더링(CommunityPostService
     * .toResponsePage)에서 첨부된 추천 세트가 있는 게시글마다 개별 조회하던 N+1을 없앤다.
     * 요청한 id 중 하나라도 없으면 단건 버전과 동일하게 404를 던진다(게시글에 달린 첨부는
     * FK로 실존이 보장되므로 정상 경로에서는 발생하지 않아야 하는 불변조건).
     */
    @Transactional(readOnly = true)
    public Map<Long, RecommendationSetSummary> getForAttachments(List<Long> setIds) {
        List<Long> distinctIds = setIds.stream().distinct().toList();
        if (distinctIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, RecommendationSet> setsById = recommendationSetRepository.findAllById(distinctIds).stream()
                .collect(Collectors.toMap(RecommendationSet::getId, set -> set));
        for (Long id : distinctIds) {
            if (!setsById.containsKey(id)) {
                throw new ApiException(ApiErrorCode.RECOMMENDATION_SET_NOT_FOUND, "추천 세트를 찾을 수 없습니다.");
            }
        }
        Map<Long, List<RecommendationItemView>> itemsBySetId = itemViewsBySetId(distinctIds);

        // I-04: 목록 렌더링에서 세트 하나의 디코드 실패(레거시 strategy/explanation_codes 값)가
        // 페이지 전체를 깨뜨리면 안 된다 — 그 게시글만 첨부 없이 표시하고 나머지는 정상 진행한다.
        Map<Long, RecommendationSetSummary> result = new LinkedHashMap<>();
        for (Long id : distinctIds) {
            try {
                result.put(id, toSummary(setsById.get(id), itemsBySetId.getOrDefault(id, List.of())));
            } catch (ApiException e) {
                log.warn("추천 세트 {} 첨부 렌더링 실패, 목록에서는 첨부 없이 표시합니다: {}", id, e.getMessage());
            }
        }
        return result;
    }

    private RecommendationSet findById(long id) {
        return recommendationSetRepository.findById(id)
                .orElseThrow(() -> new ApiException(ApiErrorCode.RECOMMENDATION_SET_NOT_FOUND,
                        "추천 세트를 찾을 수 없습니다."));
    }

    private RecommendationSet findOwnedByDevice(String clientTokenHash, long id) {
        RecommendationSet set = findById(id);
        if (!clientTokenHash.equals(set.getClientTokenHash())) {
            throw new ApiException(ApiErrorCode.RECOMMENDATION_SET_NOT_OWNED,
                    "이 추천 세트에 대한 권한이 없습니다.");
        }
        return set;
    }

    private RecommendationSet findOwnedByOwner(Long ownerUserId, long id) {
        RecommendationSet set = findById(id);
        if (!ownerUserId.equals(set.getOwnerUserId())) {
            throw new ApiException(ApiErrorCode.RECOMMENDATION_SET_NOT_OWNED,
                    "이 추천 세트에 대한 권한이 없습니다.");
        }
        return set;
    }

    private Map<Long, List<RecommendationItemView>> itemViewsBySetId(List<Long> setIds) {
        return recommendationItemRepository.findBySetIdInOrderBySetIdAscPositionAsc(setIds).stream()
                .collect(Collectors.groupingBy(RecommendationItem::getSetId, LinkedHashMap::new,
                        Collectors.mapping(this::toItemView, Collectors.toList())));
    }

    private RecommendationSetSummary toSummary(RecommendationSet set) {
        List<RecommendationItemView> items = recommendationItemRepository.findBySetIdOrderByPosition(set.getId())
                .stream()
                .map(this::toItemView)
                .toList();
        return toSummary(set, items);
    }

    private RecommendationSetSummary toSummary(RecommendationSet set, List<RecommendationItemView> items) {
        return new RecommendationSetSummary(
                set.getId(),
                strategyFromWire(set),
                set.getAlgorithmVersion(),
                set.getHistoryThroughRound(),
                set.getExclusionPolicyVersion(),
                lottoNumberCodec.fromStorageValue(set.getLockedNumbers()),
                lottoNumberCodec.fromStorageValue(set.getExcludedNumbers()),
                set.getCreatedAt(),
                items);
    }

    // I-04: RecommendationStrategy.fromWire()는 저장값이 현재 enum 상수와 정확히 일치하지
    // 않으면 원시 IllegalStateException을 던진다 — 호출부(대부분 GlobalExceptionHandler의
    // 범용 catch-all)까지 그대로 새면 "예상하지 못한 서버 오류가 발생했습니다"라는 원인 불명의
    // 500이 된다. 여기서 잡아 어떤 세트의 어떤 값이 문제인지 로그에 남기고, 클라이언트에는
    // 복구 가능한 오류(RECOMMENDATION_SET_UNAVAILABLE)로 번역한다.
    private static RecommendationStrategy strategyFromWire(RecommendationSet set) {
        try {
            return RecommendationStrategy.fromWire(set.getStrategy());
        } catch (IllegalStateException e) {
            throw new ApiException(ApiErrorCode.RECOMMENDATION_SET_UNAVAILABLE,
                    "추천 세트 " + set.getId() + "의 저장된 전략 값을 해석할 수 없습니다: " + set.getStrategy(), e);
        }
    }

    private RecommendationItemView toItemView(RecommendationItem item) {
        return new RecommendationItemView(
                item.getPosition(),
                lottoNumberCodec.fromStorageValue(item.getNumbers()),
                item.getScore(),
                parseExplanationCodes(item.getExplanationCodes(), item.getSetId()));
    }

    // I-04: ExplanationCode.valueOf()도 같은 이유로 원시 IllegalArgumentException을 던진다 —
    // 동일하게 래핑한다.
    private static List<ExplanationCode> parseExplanationCodes(String value, long setId) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            return java.util.Arrays.stream(value.split(",")).map(ExplanationCode::valueOf).toList();
        } catch (IllegalArgumentException e) {
            throw new ApiException(ApiErrorCode.RECOMMENDATION_SET_UNAVAILABLE,
                    "추천 세트 " + setId + "의 저장된 근거 코드 값을 해석할 수 없습니다: " + value, e);
        }
    }
}
