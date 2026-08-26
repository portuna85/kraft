package com.kraft.recommend;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecommendationSetRepository extends JpaRepository<RecommendationSet, Long> {

    // DB-REC-01(docs/improvement.md): createdAt만으로는 동일 timestamp에서 tie가 가능하므로
    // id를 2차 정렬키로 추가했다 — idx_recommendation_sets_owner_created/
    // idx_recommendation_sets_client_created(V19, V35)와 정렬 방향이 일치해야 filesort가 없다.
    Page<RecommendationSet> findByClientTokenHashOrderByCreatedAtDescIdDesc(String clientTokenHash, Pageable pageable);

    // DATA-REC-01(docs/improvement.md): 이전에는 익명 토큰의 세트 전체를 엔티티로 읽어
    // 메모리에서 claimTo()로 필드를 바꾼 뒤 dirty-checking에 맡겼다 — 세트 수만큼
    // 엔티티가 영속성 컨텍스트에 쌓였다. claimTo()가 순수 필드 대입 셋(ownerUserId·
    // claimedAt·clientTokenHash)뿐이라 도메인 이벤트나 라이프사이클 콜백에 기대지
    // 않으므로, 단일 벌크 UPDATE로 대체해도 동작이 같다.
    //
    // flushAutomatically + clearAutomatically를 **둘 다** 켠다 — 하나만으로는 실제 호출
    // 맥락에서 두 가지 다른 사고가 난다.
    // - clearAutomatically만 켜면(flush 없이): 유일한 호출부(IdentityMergeService.claim())가
    //   같은 트랜잭션에서 이 메서드 직전에 savedNumbersService.claimAll()을 부르는데, 그쪽은
    //   SavedNumber.claimTo()로 엔티티를 바꾼 뒤 flush 없이 dirty-checking에 맡긴다.
    //   clearAutomatically의 em.clear()가 그 미반영 SavedNumber 변경을 DB에 한 번도 쓰지
    //   못한 채 통째로 날려버려 "저장 번호 귀속이 조용히 사라지는" 회귀가 났다
    //   (IdentityMergeApiTest로 발견).
    // - clearAutomatically를 아예 안 쓰면: 벌크 UPDATE는 영속성 컨텍스트를 거치지 않으므로,
    //   같은 트랜잭션에서 이미 로드된(1차 캐시에 있는) RecommendationSet이 있으면 이후
    //   findById 등이 DB의 최신 값 대신 캐시된 옛 값(귀속 전 ownerUserId=null)을 돌려준다.
    //   `persist()`로 저장한 세트를 같은 트랜잭션에서 곧바로 claimAll한 뒤 소유권을 확인하는
    //   경로(CommunityPostCommentApiTest의 I-04)가 이 stale read로 403(NOT_OWNED)을 냈다.
    // flushAutomatically(먼저 flush)로 SavedNumber 변경을 안전하게 DB에 반영한 다음
    // clearAutomatically(그 다음 clear)로 캐시를 비우면 두 문제 모두 없다.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RecommendationSet s set s.ownerUserId = :ownerUserId, s.claimedAt = :claimedAt, "
            + "s.clientTokenHash = null where s.clientTokenHash = :clientTokenHash")
    int claimAllByClientTokenHash(@Param("clientTokenHash") String clientTokenHash,
                                   @Param("ownerUserId") Long ownerUserId,
                                   @Param("claimedAt") OffsetDateTime claimedAt);

    Optional<RecommendationSet> findByIdAndClientTokenHash(Long id, String clientTokenHash);

    List<RecommendationSet> findByOwnerUserIdOrderByCreatedAtDescIdDesc(Long ownerUserId);

    @Query("select s.id from RecommendationSet s where s.ownerUserId = :ownerUserId")
    List<Long> findIdsByOwnerUserId(@Param("ownerUserId") Long ownerUserId);

    Page<RecommendationSet> findByOwnerUserIdOrderByCreatedAtDescIdDesc(Long ownerUserId, Pageable pageable);

    // TD-014: 계정 탈퇴 시 세트 수만큼 개별 delete(entity)를 반복하던 것을 벌크 DELETE
    // 한 번으로 대체한다. 호출부가 반드시 이 세트들의 아이템을 먼저 지워야 한다
    // (recommendation_items.set_id FK에 ON DELETE CASCADE가 없다 — V20 마이그레이션).
    @Modifying
    @Query("delete from RecommendationSet s where s.ownerUserId = :ownerUserId")
    int deleteByOwnerUserId(@Param("ownerUserId") Long ownerUserId);
}
