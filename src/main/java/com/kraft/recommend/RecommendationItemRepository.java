package com.kraft.recommend;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendationItemRepository extends JpaRepository<RecommendationItem, Long> {

    List<RecommendationItem> findBySetIdOrderByPosition(Long setId);

    void deleteBySetId(Long setId);
}
