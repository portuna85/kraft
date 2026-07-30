package com.kraft.recommend;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendationSetRepository extends JpaRepository<RecommendationSet, Long> {

    // claimAll()이 귀속 대상 전체를 순회해야 하므로 무제한 버전을 유지한다.
    List<RecommendationSet> findByClientTokenHashOrderByCreatedAtDesc(String clientTokenHash);

    Page<RecommendationSet> findByClientTokenHashOrderByCreatedAtDesc(String clientTokenHash, Pageable pageable);

    Optional<RecommendationSet> findByIdAndClientTokenHash(Long id, String clientTokenHash);

    List<RecommendationSet> findByOwnerUserIdOrderByCreatedAtDesc(Long ownerUserId);

    Page<RecommendationSet> findByOwnerUserIdOrderByCreatedAtDesc(Long ownerUserId, Pageable pageable);
}
