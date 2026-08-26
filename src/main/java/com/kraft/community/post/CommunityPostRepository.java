package com.kraft.community.post;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommunityPostRepository extends JpaRepository<CommunityPost, Long> {

    boolean existsByRecommendationSetId(Long recommendationSetId);

    List<CommunityPost> findByOwnerId(Long ownerId);

    // KB-04: 탈퇴 처리 시 기존 게시글의 작성자 표기를 일괄로 익명화한다.
    // 대량 JPQL update라 개별 행의 @Version은 증가하지 않는다 — 표기 정정일 뿐
    // 게시글 내용 자체의 낙관적 락 대상이 아니므로 의도된 동작이다.
    @Modifying
    @Query("update CommunityPost p set p.authorNameSnapshot = :name where p.ownerId = :ownerId")
    int renameAuthorForOwner(@Param("ownerId") Long ownerId, @Param("name") String name);

    // DATA-DEL-01(docs/improvement.md): 탈퇴 처리가 이 사용자의 게시글 전체를 엔티티로 읽어
    // eraseForAccountDeletion()을 호출한 뒤 saveAllAndFlush하던 것을 단일 벌크 UPDATE로
    // 대체한다. CommunityPost.eraseForAccountDeletion(OffsetDateTime)과 정확히 같은 리터럴을
    // 쓴다 — 엔티티 메서드는 JPQL에서 호출할 수 없으므로 값을 그대로 옮겨 적었다. 엔티티
    // 쪽 문구를 바꾸면 이 쿼리도 함께 바꿔야 한다. renameAuthorForOwner와 같은 이유로
    // @Version은 증가하지 않는다 — 탈퇴 대상 계정 소유 게시글이라 동시 편집 경합 대상이
    // 아니다.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update CommunityPost p set p.ownerId = null, p.authorNameSnapshot = '삭제된 사용자', "
            + "p.title = '삭제된 게시글입니다.', p.content = '삭제된 게시글입니다.', "
            + "p.status = com.kraft.community.post.PostStatus.DELETED, p.recommendationSetId = null, "
            + "p.updatedAt = :updatedAt where p.ownerId = :ownerId")
    int eraseForAccountDeletion(@Param("ownerId") Long ownerId, @Param("updatedAt") OffsetDateTime updatedAt);

    @Query("SELECT p FROM CommunityPost p WHERE p.status = com.kraft.community.post.PostStatus.PUBLISHED "
            + "AND (:category IS NULL OR p.category = :category) "
            + "AND (:query IS NULL OR LOWER(p.title) LIKE LOWER(CONCAT('%', :query, '%')) ESCAPE '!' "
            + "     OR LOWER(p.content) LIKE LOWER(CONCAT('%', :query, '%')) ESCAPE '!') "
            + "ORDER BY p.createdAt DESC, p.id DESC")
    Page<CommunityPost> findLatest(@Param("category") PostCategory category, @Param("query") String query,
                                    Pageable pageable);

    // BE-PERF-01(docs/improvement.md): findLatest와 완전히 같은 조건·정렬이지만 List를 반환해
    // count 쿼리를 실행하지 않는다. 홈 요약처럼 총 개수(getTotalElements())가 필요 없는
    // 호출자 전용 — 실제 페이지네이션이 필요한 CommunityPostService.list()는 계속 위 findLatest를
    // 쓴다.
    @Query("SELECT p FROM CommunityPost p WHERE p.status = com.kraft.community.post.PostStatus.PUBLISHED "
            + "AND (:category IS NULL OR p.category = :category) "
            + "AND (:query IS NULL OR LOWER(p.title) LIKE LOWER(CONCAT('%', :query, '%')) ESCAPE '!' "
            + "     OR LOWER(p.content) LIKE LOWER(CONCAT('%', :query, '%')) ESCAPE '!') "
            + "ORDER BY p.createdAt DESC, p.id DESC")
    List<CommunityPost> findLatestPublished(@Param("category") PostCategory category, @Param("query") String query,
                                             Pageable pageable);

    // popular_score 공식을 7일 이내 게시글로 범위를 좁혀 요청 시점에 계산한다
    // (사전 집계 랭킹 테이블 없이 — 이 규모에서는 매 요청 계산 비용이 무시할 만하다).
    @Query(value = "SELECT p.* FROM community_posts p JOIN community_post_metrics m ON m.post_id = p.id "
            + "WHERE p.status = 'PUBLISHED' AND p.created_at >= :since "
            + "AND (:category IS NULL OR p.category = :category) "
            + "AND (:query IS NULL OR LOWER(p.title) LIKE LOWER(CONCAT('%', :query, '%')) ESCAPE '!' "
            + "     OR LOWER(p.content) LIKE LOWER(CONCAT('%', :query, '%')) ESCAPE '!') "
            + "ORDER BY (m.like_count * 3 + m.comment_count * 2 + LEAST(m.view_count, 1000) / 20) "
            + "  / POW(TIMESTAMPDIFF(SECOND, p.created_at, UTC_TIMESTAMP(6)) / 3600.0 + 2, 1.35) DESC, "
            + "  p.created_at DESC, p.id DESC",
            countQuery = "SELECT COUNT(*) FROM community_posts p "
                    + "WHERE p.status = 'PUBLISHED' AND p.created_at >= :since "
                    + "AND (:category IS NULL OR p.category = :category) "
                    + "AND (:query IS NULL OR LOWER(p.title) LIKE LOWER(CONCAT('%', :query, '%')) ESCAPE '!' "
                    + "     OR LOWER(p.content) LIKE LOWER(CONCAT('%', :query, '%')) ESCAPE '!')",
            nativeQuery = true)
    Page<CommunityPost> findWeeklyPopular(@Param("category") String category, @Param("query") String query,
                                           @Param("since") OffsetDateTime since, Pageable pageable);

    // BE-PERF-01: findWeeklyPopular와 완전히 같은 native SQL이지만 countQuery가 없어(List 반환)
    // count 쿼리를 실행하지 않는다. 총 개수가 필요 없는 홈 요약 전용 — CommunityPostService.list()는
    // 계속 위 findWeeklyPopular를 쓴다.
    @Query(value = "SELECT p.* FROM community_posts p JOIN community_post_metrics m ON m.post_id = p.id "
            + "WHERE p.status = 'PUBLISHED' AND p.created_at >= :since "
            + "AND (:category IS NULL OR p.category = :category) "
            + "AND (:query IS NULL OR LOWER(p.title) LIKE LOWER(CONCAT('%', :query, '%')) ESCAPE '!' "
            + "     OR LOWER(p.content) LIKE LOWER(CONCAT('%', :query, '%')) ESCAPE '!') "
            + "ORDER BY (m.like_count * 3 + m.comment_count * 2 + LEAST(m.view_count, 1000) / 20) "
            + "  / POW(TIMESTAMPDIFF(SECOND, p.created_at, UTC_TIMESTAMP(6)) / 3600.0 + 2, 1.35) DESC, "
            + "  p.created_at DESC, p.id DESC",
            nativeQuery = true)
    List<CommunityPost> findWeeklyPopularPublished(@Param("category") String category, @Param("query") String query,
                                                     @Param("since") OffsetDateTime since, Pageable pageable);
}
