package com.kraft.recommend;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecommendationSetRepository extends JpaRepository<RecommendationSet, Long> {

    // claimAll()이 귀속 대상 전체를 순회해야 하므로 무제한 버전을 유지한다.
    List<RecommendationSet> findByClientTokenHashOrderByCreatedAtDesc(String clientTokenHash);

    Page<RecommendationSet> findByClientTokenHashOrderByCreatedAtDesc(String clientTokenHash, Pageable pageable);

    Optional<RecommendationSet> findByIdAndClientTokenHash(Long id, String clientTokenHash);

    List<RecommendationSet> findByOwnerUserIdOrderByCreatedAtDesc(Long ownerUserId);

    @Query("select s.id from RecommendationSet s where s.ownerUserId = :ownerUserId")
    List<Long> findIdsByOwnerUserId(@Param("ownerUserId") Long ownerUserId);

    Page<RecommendationSet> findByOwnerUserIdOrderByCreatedAtDesc(Long ownerUserId, Pageable pageable);

    // TD-014: 계정 탈퇴 시 세트 수만큼 개별 delete(entity)를 반복하던 것을 벌크 DELETE
    // 한 번으로 대체한다. 호출부가 반드시 이 세트들의 아이템을 먼저 지워야 한다
    // (recommendation_items.set_id FK에 ON DELETE CASCADE가 없다 — V20 마이그레이션).
    @Modifying
    @Query("delete from RecommendationSet s where s.ownerUserId = :ownerUserId")
    int deleteByOwnerUserId(@Param("ownerUserId") Long ownerUserId);
}
