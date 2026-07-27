package com.kraft.recommend;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendationSetRepository extends JpaRepository<RecommendationSet, Long> {

    List<RecommendationSet> findByClientTokenHashOrderByCreatedAtDesc(String clientTokenHash);

    Optional<RecommendationSet> findByIdAndClientTokenHash(Long id, String clientTokenHash);
}
