package com.kraft.community.post;

import java.time.OffsetDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommunityPostRepository extends JpaRepository<CommunityPost, Long> {

    boolean existsByRecommendationSetId(Long recommendationSetId);

    // KB-04: 탈퇴 처리 시 기존 게시글의 작성자 표기를 일괄로 익명화한다.
    // 대량 JPQL update라 개별 행의 @Version은 증가하지 않는다 — 표기 정정일 뿐
    // 게시글 내용 자체의 낙관적 락 대상이 아니므로 의도된 동작이다.
    @Modifying
    @Query("update CommunityPost p set p.authorNameSnapshot = :name where p.ownerId = :ownerId")
    int renameAuthorForOwner(@Param("ownerId") Long ownerId, @Param("name") String name);

    @Query("SELECT p FROM CommunityPost p WHERE p.status = com.kraft.community.post.PostStatus.PUBLISHED "
            + "AND (:category IS NULL OR p.category = :category) "
            + "AND (:query IS NULL OR LOWER(p.title) LIKE LOWER(CONCAT('%', :query, '%')) "
            + "     OR LOWER(p.content) LIKE LOWER(CONCAT('%', :query, '%'))) "
            + "ORDER BY p.createdAt DESC, p.id DESC")
    Page<CommunityPost> findLatest(@Param("category") PostCategory category, @Param("query") String query,
                                    Pageable pageable);

    // popular_score 공식을 7일 이내 게시글로 범위를 좁혀 요청 시점에 계산한다
    // (사전 집계 랭킹 테이블 없이 — 이 규모에서는 매 요청 계산 비용이 무시할 만하다).
    @Query(value = "SELECT p.* FROM community_posts p JOIN community_post_metrics m ON m.post_id = p.id "
            + "WHERE p.status = 'PUBLISHED' AND p.created_at >= :since "
            + "AND (:category IS NULL OR p.category = :category) "
            + "AND (:query IS NULL OR LOWER(p.title) LIKE LOWER(CONCAT('%', :query, '%')) "
            + "     OR LOWER(p.content) LIKE LOWER(CONCAT('%', :query, '%'))) "
            + "ORDER BY (m.like_count * 3 + m.comment_count * 2 + LEAST(m.view_count, 1000) / 20) "
            + "  / POW(TIMESTAMPDIFF(SECOND, p.created_at, UTC_TIMESTAMP(6)) / 3600.0 + 2, 1.35) DESC",
            countQuery = "SELECT COUNT(*) FROM community_posts p "
                    + "WHERE p.status = 'PUBLISHED' AND p.created_at >= :since "
                    + "AND (:category IS NULL OR p.category = :category) "
                    + "AND (:query IS NULL OR LOWER(p.title) LIKE LOWER(CONCAT('%', :query, '%')) "
                    + "     OR LOWER(p.content) LIKE LOWER(CONCAT('%', :query, '%')))",
            nativeQuery = true)
    Page<CommunityPost> findWeeklyPopular(@Param("category") String category, @Param("query") String query,
                                           @Param("since") OffsetDateTime since, Pageable pageable);
}
