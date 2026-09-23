package com.kraft.comment.domain;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    /**
     * id 커서 기반 페이지 조회. {@code afterId}가 null이면 가장 오래된 댓글부터, 있으면 그
     * id보다 큰(= 더 나중에 쓰인) 댓글부터 오름차순으로 최대 {@code pageable.getPageSize()}개를
     * 반환한다. 상세 화면의 댓글 전체 로딩을 대체한다(개선 보고서 "댓글 전체 로딩").
     * <p>
     * 최상위 댓글({@code parent IS NULL})만 커서 페이지네이션한다 — 답글은 이 페이지에 실린
     * 최상위 댓글들을 대상으로 {@link #findRepliesByParentIdIn}이 별도로, 부모별 커서 없이
     * 한 번에(전체 상한만 두고) 가져온다(2단계 댓글).
     */
    @Query("SELECT c FROM Comment c JOIN FETCH c.user WHERE c.post.id = :postId AND c.parent IS NULL "
            + "AND (:afterId IS NULL OR c.id > :afterId) ORDER BY c.id ASC")
    List<Comment> findPageByPostIdAsc(@Param("postId") Long postId, @Param("afterId") Long afterId,
                                       Pageable pageable);

    /**
     * 한 페이지에 실린 최상위 댓글들의 답글을 한 번에 배치로 가져온다(N+1 방지,
     * {@link #findAllByIdInWithUser}와 같은 관례). 부모별로 나눠 페이지네이션하지는 않는다 —
     * 이 게시판 규모에서 한 댓글에 달리는 답글 수가 그 정도로 많지는 않다. 다만 {@code pageable}로
     * 이 페이지 전체(여러 부모 합산)에서 가져오는 답글 총량에 상한을 둔다(B08) — 악의적으로
     * 한 댓글에 답글을 대량으로 단 경우에도 응답 크기가 무한정 늘어나지 않는다.
     */
    @Query("SELECT c FROM Comment c JOIN FETCH c.user WHERE c.parent.id IN :parentIds "
            + "ORDER BY c.parent.id ASC, c.id ASC")
    List<Comment> findRepliesByParentIdIn(@Param("parentIds") List<Long> parentIds, Pageable pageable);

    /**
     * 게시글의 답글만 먼저 지운다. {@code deleteAllByPostId}를 부모·답글 구분 없이 한 문장으로
     * 실행하면, MariaDB(InnoDB)는 {@code FK_COMMENTS_PARENT}를 행 단위로 즉시 검사하기 때문에
     * 삭제 순서에 따라 부모가 자신의 답글보다 먼저 지워져 FK 위반(1451)이 날 수 있다. H2는 문장
     * 끝에서만 제약을 검사해 이 순서 문제를 재현하지 못한다(개선 보고서 "답글이 있는 게시글
     * 삭제와 자기참조 FK", {@code PostDeleteWithRepliesMariaDbTest}). 그래서 게시글을 지울 때는
     * 이 메서드로 답글을 먼저 비운 뒤 {@link #deleteAllByPostId}를 호출한다.
     */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM Comment c WHERE c.post.id = :postId AND c.parent IS NOT NULL")
    void deleteRepliesByPostId(@Param("postId") Long postId);

    /**
     * 파생 삭제(개별 조회 후 건별 DELETE)가 아니라 한 문장으로 지운다. 연관 캐스케이드·
     * {@code @PreRemove} 리스너가 없는 엔티티라 벌크 삭제로 바꿔도 잃는 동작이 없다
     * (개선 보고서 "파생 delete 메서드의 엔티티별 삭제"). {@code clearAutomatically}는 일부러
     * 켜지 않는다 — 영속성 컨텍스트 전체를 비워서, 이 메서드를 호출하기 전에 같은 트랜잭션에서
     * 읽어 둔 다른 엔티티(예: {@code ReportService.resolve()}가 미리 들고 있던 {@code Report})가
     * 조용히 detach되어 이후의 변경이 반영되지 않는 사고가 실제로 있었다.
     * <p>
     * 호출 전 {@link #deleteRepliesByPostId}로 답글을 먼저 비워야 한다 — 이 메서드 혼자서는
     * 부모·답글이 섞인 게시글에서 MariaDB FK 위반이 날 수 있다.
     */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM Comment c WHERE c.post.id = :postId")
    void deleteAllByPostId(@Param("postId") Long postId);

    /**
     * 최상위 댓글을 지우기 전에 그 답글을 먼저 지운다(2단계 댓글, {@code CommentService.delete()}).
     * DB에 {@code ON DELETE CASCADE}를 걸지 않은 이유는 {@link Comment}의 {@code parent} 필드
     * 주석 참고 — {@code deleteAllByPostId}와 같은 이유로 애플리케이션 계층에서 명시적으로
     * 처리한다. 답글에는 답글이 없으므로(3단계 금지) 이 메서드를 답글 자신에 호출해도
     * 안전하게 0건을 지운다.
     */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM Comment c WHERE c.parent.id = :parentId")
    void deleteAllByParentId(@Param("parentId") Long parentId);

    long countByPostId(Long postId);

    /**
     * 여러 id를 한 번에 조회한다(N+1 방지). 신고 목록이 페이지 안의 댓글 대상들을 한 번에
     * 묶어 조회할 때 쓴다(개선 보고서 "신고 목록의 대상별 조회").
     */
    @Query("SELECT c FROM Comment c JOIN FETCH c.user WHERE c.id IN :ids")
    List<Comment> findAllByIdInWithUser(@Param("ids") List<Long> ids);

    @Query("SELECT c.post.id AS postId, COUNT(c) AS count FROM Comment c WHERE c.post.id IN :postIds GROUP BY c.post.id")
    List<PostCommentCount> countGroupedByPostIdIn(@Param("postIds") List<Long> postIds);

    /**
     * 목록 화면처럼 여러 게시글의 댓글 수를 한 번에 조회할 때, 게시글마다 별도 쿼리를 던지지
     * 않도록(N+1 방지) 배치 집계 쿼리 결과를 postId → count 맵으로 모아 돌려준다. 댓글이
     * 없는 게시글은 결과에 행 자체가 없으므로, 호출부에서 {@code getOrDefault(id, 0L)}로
     * 다뤄야 한다.
     */
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
}
