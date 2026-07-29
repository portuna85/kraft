package com.kraft.recommend;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendationHistoryStateRepository extends JpaRepository<RecommendationHistoryState, Integer> {
}
