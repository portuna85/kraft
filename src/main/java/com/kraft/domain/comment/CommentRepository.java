package com.kraft.domain.comment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    @Query("SELECT c FROM Comment c JOIN FETCH c.user WHERE c.post.id = :postId ORDER BY c.id ASC")
    List<Comment> findAllByPostIdAsc(@Param("postId") Long postId);

    void deleteAllByPostId(Long postId);

    long countByPostId(Long postId);

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
