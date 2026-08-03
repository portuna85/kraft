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

    private final RecommendationSetRepository recommendationSetRepository;
    private final RecommendationItemRepository recommendationItemRepository;
    private final LottoNumberCodec lottoNumberCodec;
    private final RecommendationSetAttachmentChecker attachmentChecker;

    // KB-05: 목록 API가 무제한 배열을 반환하던 문제를 막는 상한 — CommunityPostService와 동일한 값.
    private static final int MAX_PAGE_SIZE = 50;

    public RecommendationSetHistoryService(RecommendationSetRepository recommendationSetRepository,
                                            RecommendationItemRepository recommendationItemRepository,
                                            LottoNumberCodec lottoNumberCodec,
                                            RecommendationSetAttachmentChecker attachmentChecker) {
        this.recommendationSetRepository = recommendationSetRepository;
        this.recommendationItemRepository = recommendationItemRepository;
        this.lottoNumberCodec = lottoNumberCodec;
        this.attachmentChecker = attachmentChecker;
    }

    public Long persist(String clientTokenHash, String strategy, String algorithmVersion, int historyThroughRound,
                         String exclusionPolicyVersion,
                         List<Integer> locked, List<Integer> excluded, List<RecommendationItemView> items,
                         OffsetDateTime createdAt) {
        String lockedStorage = locked.isEmpty() ? null : lottoNumberCodec.toStorageValueSubset(locked);
        String excludedStorage = excluded.isEmpty() ? null : lottoNumberCodec.toStorageValueSubset(excluded);

        RecommendationSet set = recommendationSetRepository.save(new RecommendationSet(
                clientTokenHash, strategy, algorithmVersion, historyThroughRound, exclusionPolicyVersion,
                lockedStorage, excludedStorage, createdAt));

        // M-8: 항목마다 save()를 개별 호출하면 N+1이 되고, application-prod.yml의
        // hibernate.jdbc.batch_size도 개별 save()에는 적용되지 않는다 — saveAll()로 한 번에
        // 묶어야 배치 insert가 실제로 일어난다.
        List<RecommendationItem> entities = items.stream()
                .map(item -> new RecommendationItem(
                        set.getId(), item.position(), lottoNumberCodec.toStorageValueSubset(item.numbers()),
                        LottoBitmask.of(item.numbers()), item.score(),
                        item.explanationCodes().isEmpty()
                                ? null
                                : item.explanationCodes().stream().map(Enum::name).collect(Collectors.joining(","))))
                .toList();
        recommendationItemRepository.saveAll(entities);
        return set.getId();
    }

    @Transactional(readOnly = true)
    public Page<RecommendationSetSummary> list(String clientTokenHash, int page, int size) {
        Page<RecommendationSet> sets = recommendationSetRepository.findByClientTokenHashOrderByCreatedAtDesc(
                clientTokenHash, pageRequest(page, size));
        return mapWithBatchedItems(sets);
    }

    @Transactional(readOnly = true)
    public RecommendationSetSummary get(String clientTokenHash, long id) {
        RecommendationSet set = findOwnedByDevice(clientTokenHash, id);
        return toSummary(set);
    }

    @Transactional(readOnly = true)
    public Page<RecommendationSetSummary> listForOwner(Long ownerUserId, int page, int size) {
        Page<RecommendationSet> sets = recommendationSetRepository.findByOwnerUserIdOrderByCreatedAtDesc(
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

    /**
     * B-P0-2: claim(계정 귀속) 이후 세트는 clientTokenHash가 null로 지워지므로 익명 기기
     * 토큰으로는 더 이상 찾을 수 없다(의도된 동작 — claim 후에는 로그인 세션만 접근해야 한다).
     * {@code /api/v1/recommendation-sets/{id}}는 STATELESS 기기 토큰 체인이라 로그인 여부를
     * 알 수 없으므로, 로그인 사용자를 위한 별도 조회·삭제 경로(ownerUserId 기준)를 제공한다.
     */
    @Transactional(readOnly = true)
    public RecommendationSetSummary getForOwner(Long ownerUserId, long id) {
        RecommendationSet set = findOwnedByOwner(ownerUserId, id);
        return toSummary(set);
    }

    /**
     * 로그인 계정 귀속 — 추천 세트는 각각이 독립된 이력
     * 레코드라 저장 번호와 달리 중복 제거 없이 전부 옮긴다.
     */
    public int claimAll(String clientTokenHash, Long ownerUserId, OffsetDateTime claimedAt) {
        List<RecommendationSet> anonymous = recommendationSetRepository.findByClientTokenHashOrderByCreatedAtDesc(clientTokenHash);
        anonymous.forEach(set -> set.claimTo(ownerUserId, claimedAt));
        return anonymous.size();
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

        Map<Long, RecommendationSetSummary> result = new LinkedHashMap<>();
        for (Long id : distinctIds) {
            result.put(id, toSummary(setsById.get(id), itemsBySetId.getOrDefault(id, List.of())));
        }
        return result;
    }

    public void delete(String clientTokenHash, long id) {
        RecommendationSet set = findOwnedByDevice(clientTokenHash, id);
        deleteOwnedSet(set);
    }

    public void deleteForOwner(Long ownerUserId, long id) {
        RecommendationSet set = findOwnedByOwner(ownerUserId, id);
        deleteOwnedSet(set);
    }

    private void deleteOwnedSet(RecommendationSet set) {
        if (attachmentChecker.isAttachedToPost(set.getId())) {
            throw new ApiException(ApiErrorCode.RECOMMENDATION_SET_ATTACHED_TO_POST,
                    "커뮤니티 게시글에 첨부된 추천 세트는 삭제할 수 없습니다.");
        }
        recommendationItemRepository.deleteBySetId(set.getId());
        recommendationSetRepository.delete(set);
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
                set.getStrategy(),
                set.getAlgorithmVersion(),
                set.getHistoryThroughRound(),
                set.getExclusionPolicyVersion(),
                lottoNumberCodec.fromStorageValue(set.getLockedNumbers()),
                lottoNumberCodec.fromStorageValue(set.getExcludedNumbers()),
                set.getCreatedAt(),
                items);
    }

    private RecommendationItemView toItemView(RecommendationItem item) {
        return new RecommendationItemView(
                item.getPosition(),
                lottoNumberCodec.fromStorageValue(item.getNumbers()),
                item.getScore(),
                parseExplanationCodes(item.getExplanationCodes()));
    }

    private static List<ExplanationCode> parseExplanationCodes(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(value.split(",")).map(ExplanationCode::valueOf).toList();
    }
}
