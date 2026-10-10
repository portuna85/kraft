package com.kraft.comment.domain;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    /**
     * 최상위 댓글({@code parent IS NULL})의 id 커서 페이지(오름차순). {@code afterId}가 null이면 처음부터.
     * 답글은 {@link #findInitialRepliesGroupedByParentIdIn}·{@link #findRepliesByParentIdAsc}가 따로 가져온다.
     */
    @Query("SELECT c FROM Comment c JOIN FETCH c.user WHERE c.post.id = :postId AND c.parent IS NULL "
            + "AND (:afterId IS NULL OR c.id > :afterId) ORDER BY c.id ASC")
    List<Comment> findPageByPostIdAsc(@Param("postId") Long postId, @Param("afterId") Long afterId,
                                       Pageable pageable);

    /** 한 부모의 답글을 id 커서로 가져온다("답글 더 보기"). */
    @Query("SELECT c FROM Comment c JOIN FETCH c.user WHERE c.parent.id = :parentId "
            + "AND (:afterId IS NULL OR c.id > :afterId) ORDER BY c.id ASC")
    List<Comment> findRepliesByParentIdAsc(@Param("parentId") Long parentId, @Param("afterId") Long afterId,
                                            Pageable pageable);

    /**
     * 부모별 상위 {@code limitPerParent}개 답글 id와 그 부모의 전체 답글 수를 {@code ROW_NUMBER()}·{@code COUNT(*) OVER (PARTITION BY ...)}로
     * 한 번에 뽑는다(행: {@code [id, total]}). 엔티티는 {@link #findAllByIdInWithUser}로 따로 가져온다(네이티브 쿼리에 JOIN FETCH가 어렵다).
     */
    @Query(value = "SELECT id, total FROM ("
            + "SELECT id, ROW_NUMBER() OVER (PARTITION BY parent_id ORDER BY id ASC) AS rn, "
            + "COUNT(*) OVER (PARTITION BY parent_id) AS total "
            + "FROM comments WHERE parent_id IN :parentIds"
            + ") ranked WHERE rn <= :limitPerParent", nativeQuery = true)
    List<Object[]> findTopReplyIdsWithTotalPerParent(@Param("parentIds") List<Long> parentIds,
                                                      @Param("limitPerParent") int limitPerParent);

    /** 부모별 처음 답글(오름차순)과 전체 답글 수. 답글이 없는 부모는 두 맵 모두 키가 없다. */
    record InitialReplies(Map<Long, List<Comment>> repliesByParent, Map<Long, Long> totalByParent) {
        public static final InitialReplies EMPTY = new InitialReplies(Map.of(), Map.of());
    }

    default InitialReplies findInitialReplies(List<Long> parentIds, int limitPerParent) {
        if (parentIds.isEmpty()) {
            return InitialReplies.EMPTY;
        }
        List<Object[]> rows = findTopReplyIdsWithTotalPerParent(parentIds, limitPerParent);
        if (rows.isEmpty()) {
            return InitialReplies.EMPTY;
        }
        Map<Long, Long> totalById = new java.util.HashMap<>();
        for (Object[] row : rows) {
            totalById.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        Map<Long, List<Comment>> byParent = findAllByIdInWithUser(List.copyOf(totalById.keySet())).stream()
                .sorted(Comparator.comparing(Comment::getId))
                .collect(Collectors.groupingBy(c -> c.getParent().getId()));
        Map<Long, Long> totals = new java.util.HashMap<>();
        byParent.forEach((parentId, replies) -> totals.put(parentId, totalById.get(replies.get(0).getId())));
        return new InitialReplies(byParent, totals);
    }

    /**
     * 게시글의 답글만 먼저 지운다. MariaDB는 {@code FK_COMMENTS_PARENT}를 행 단위로 즉시 검사해, 한 문장으로
     * 지우면 부모가 답글보다 먼저 지워져 FK 위반(1451)이 날 수 있다(H2는 재현하지 못한다).
     * {@link #deleteAllByPostId} 전에 호출한다.
     */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM Comment c WHERE c.post.id = :postId AND c.parent IS NOT NULL")
    void deleteRepliesByPostId(@Param("postId") Long postId);

    /**
     * 한 문장 벌크 삭제(캐스케이드·리스너가 없는 엔티티). {@code clearAutomatically}를 켜면 같은 트랜잭션에서
     * 읽어 둔 엔티티가 detach되어 이후 변경이 사라지므로 켜지 않는다. 먼저 {@link #deleteRepliesByPostId}를 부른다.
     */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM Comment c WHERE c.post.id = :postId")
    void deleteAllByPostId(@Param("postId") Long postId);

    /** 최상위 댓글을 지우기 전에 그 답글을 지운다(DB cascade 없음 — {@link Comment}의 {@code parent} 참고). 답글에 호출하면 0건. */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM Comment c WHERE c.parent.id = :parentId")
    void deleteAllByParentId(@Param("parentId") Long parentId);

    long countByPostId(Long postId);

    /** 여러 id를 작성자와 함께 한 번에 조회한다(N+1 방지). */
    @Query("SELECT c FROM Comment c JOIN FETCH c.user WHERE c.id IN :ids")
    List<Comment> findAllByIdInWithUser(@Param("ids") List<Long> ids);

    /** 수정·삭제 경로용: 작성자와 게시글을 한 쿼리로 가져와 권한·공개 여부 검사의 지연 로딩을 없앤다. */
    @Query("SELECT c FROM Comment c JOIN FETCH c.user JOIN FETCH c.post WHERE c.id = :id")
    Optional<Comment> findByIdWithUserAndPost(@Param("id") Long id);

    @Query("SELECT c.post.id AS postId, COUNT(c) AS count FROM Comment c WHERE c.post.id IN :postIds GROUP BY c.post.id")
    List<PostCommentCount> countGroupedByPostIdIn(@Param("postIds") List<Long> postIds);

    /** 여러 게시글의 댓글 수를 postId → count 맵으로(N+1 방지). 댓글이 없는 글은 키가 없다. */
    default Map<Long, Long> countByPostIdIn(List<Long> postIds) {
        if (postIds.isEmpty()) {
            return Map.of();
        }
        return countGroupedByPostIdIn(postIds).stream()
                .collect(Collectors.toMap(PostCommentCount::getPostId, PostCommentCount::getCount));
    }

    interface PostCommentCount {
        Long getPostId();

        Long getCount();
    }

    @Query("SELECT c.parent.id AS parentId, COUNT(c) AS count FROM Comment c "
            + "WHERE c.parent.id IN :parentIds GROUP BY c.parent.id")
    List<ParentReplyCount> countGroupedByParentIdIn(@Param("parentIds") List<Long> parentIds);

    /** 부모 id별 답글 수 맵. 답글이 없는 부모는 키가 없다. */
    default Map<Long, Long> countRepliesByParentIdIn(List<Long> parentIds) {
        if (parentIds.isEmpty()) {
            return Map.of();
        }
        return countGroupedByParentIdIn(parentIds).stream()
                .collect(Collectors.toMap(ParentReplyCount::getParentId, ParentReplyCount::getCount));
    }

    interface ParentReplyCount {
        Long getParentId();

        Long getCount();
    }

    /** 관리자가 댓글을 숨긴다. {@code blindedAt}은 {@code updatable = false}라 이 UPDATE로만 바꾼다. 이미 숨겼으면 0. */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Comment c SET c.blindedAt = :now WHERE c.id = :id AND c.blindedAt IS NULL")
    int blind(@Param("id") Long id, @Param("now") java.time.LocalDateTime now);

    /** 숨김을 푼다. 숨겨진 댓글이 아니면 0을 돌려준다. */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Comment c SET c.blindedAt = NULL WHERE c.id = :id AND c.blindedAt IS NOT NULL")
    int unblind(@Param("id") Long id);
}
