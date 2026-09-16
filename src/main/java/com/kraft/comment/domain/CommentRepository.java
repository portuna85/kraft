package com.kraft.comment.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    @Query("SELECT c FROM Comment c JOIN FETCH c.user WHERE c.post.id = :postId ORDER BY c.id ASC")
    List<Comment> findAllByPostIdAsc(@Param("postId") Long postId);

    /**
     * 파생 삭제(개별 조회 후 건별 DELETE)가 아니라 한 문장으로 지운다. 연관 캐스케이드·
     * {@code @PreRemove} 리스너가 없는 엔티티라 벌크 삭제로 바꿔도 잃는 동작이 없다
     * (개선 보고서 "파생 delete 메서드의 엔티티별 삭제"). {@code clearAutomatically}는 일부러
     * 켜지 않는다 — 영속성 컨텍스트 전체를 비워서, 이 메서드를 호출하기 전에 같은 트랜잭션에서
     * 읽어 둔 다른 엔티티(예: {@code ReportService.resolve()}가 미리 들고 있던 {@code Report})가
     * 조용히 detach되어 이후의 변경이 반영되지 않는 사고가 실제로 있었다.
     */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM Comment c WHERE c.post.id = :postId")
    void deleteAllByPostId(@Param("postId") Long postId);

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
