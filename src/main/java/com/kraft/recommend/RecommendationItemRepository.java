package com.kraft.recommend;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecommendationItemRepository extends JpaRepository<RecommendationItem, Long> {

    List<RecommendationItem> findBySetIdOrderByPosition(Long setId);

    // KB-05: 세트별로 개별 조회하던 N+1을 없애기 위한 IN 배치 조회 — 호출부가 setId로 그룹핑한다.
    List<RecommendationItem> findBySetIdInOrderBySetIdAscPositionAsc(List<Long> setIds);

    void deleteBySetId(Long setId);

    // TD-014: 파생 delete는 대상 행을 전부 로드한 뒤 건별로 삭제한다 — 계정 탈퇴 시 세트
    // 수만큼 반복 호출하던 것을 IN절 벌크 DELETE 한 번으로 대체한다.
    @Modifying
    @Query("delete from RecommendationItem i where i.setId in :setIds")
    int deleteBySetIdIn(@Param("setIds") List<Long> setIds);
}
