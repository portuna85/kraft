package com.example.board.post;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    /** 포스트의 모든 댓글(상위+대댓글) 시간순 정렬 */
    List<Comment> findByPostIdOrderByCreatedAtAsc(Long postId);

    /** 상위 댓글만 페이징 (정렬은 Pageable로 넘기기 위해 OrderBy 제거 권장) */
    Page<Comment> findByPostIdAndParentIsNull(Long postId, Pageable pageable);

    /** 어떤 상위 댓글의 대댓글 목록 (항상 시간순 표시) */
    List<Comment> findByParentIdOrderByCreatedAtAsc(Long parentId);

    /** 포스트의 전체 댓글 수(상위+대댓글) */
    long countByPostId(Long postId);

    /** 상위 댓글 수(페이지 계산용) */
    long countByPostIdAndParentIsNull(Long postId);

    /** 특정 상위 댓글의 대댓글 수(“답글 n” 표시에 유용) */
    long countByParentId(Long parentId);
}
