package com.kraft.recommend;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendationItemRepository extends JpaRepository<RecommendationItem, Long> {

    List<RecommendationItem> findBySetIdOrderByPosition(Long setId);

    // KB-05: 세트별로 개별 조회하던 N+1을 없애기 위한 IN 배치 조회 — 호출부가 setId로 그룹핑한다.
    List<RecommendationItem> findBySetIdInOrderBySetIdAscPositionAsc(List<Long> setIds);

    void deleteBySetId(Long setId);
}
