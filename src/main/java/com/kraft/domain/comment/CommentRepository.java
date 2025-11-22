package com.kraft.domain.comment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    /**
     * 특정 게시글의 댓글 목록 조회 (N+1 문제 해결)
     * @param postId 게시글 ID
     * @return 댓글 목록 (오래된 순)
     */
    @Query("SELECT c FROM Comment c JOIN FETCH c.author WHERE c.post.id = :postId ORDER BY c.id ASC")
    List<Comment> findByPostIdWithAuthor(Long postId);

    /**
     * 특정 게시글의 부모 댓글만 조회 (대댓글 제외)
     * @param postId 게시글 ID
     * @return 부모 댓글 목록
     */
    @Query("SELECT c FROM Comment c JOIN FETCH c.author WHERE c.post.id = :postId AND c.parent IS NULL ORDER BY c.id ASC")
    List<Comment> findParentCommentsByPostId(Long postId);

    /**
     * 특정 게시글의 부모 댓글 페이징 조회
     * @param postId 게시글 ID
     * @param pageable 페이징 정보
     * @return 부모 댓글 페이지
     */
    @Query(value = "SELECT c FROM Comment c JOIN FETCH c.author WHERE c.post.id = :postId AND c.parent IS NULL",
           countQuery = "SELECT COUNT(c) FROM Comment c WHERE c.post.id = :postId AND c.parent IS NULL")
    Page<Comment> findParentCommentsByPostId(Long postId, Pageable pageable);

    /**
     * 특정 댓글의 답글 목록 조회
     * @param parentId 부모 댓글 ID
     * @return 답글 목록
     */
    @Query("SELECT c FROM Comment c JOIN FETCH c.author WHERE c.parent.id = :parentId ORDER BY c.id ASC")
    List<Comment> findRepliesByParentId(Long parentId);

    /**
     * 특정 게시글의 댓글 목록 페이징 조회
     * @param postId 게시글 ID
     * @param pageable 페이징 정보
     * @return 댓글 페이지
     */
    @Query(value = "SELECT c FROM Comment c JOIN FETCH c.author WHERE c.post.id = :postId",
           countQuery = "SELECT COUNT(c) FROM Comment c WHERE c.post.id = :postId")
    Page<Comment> findByPostIdWithAuthor(Long postId, Pageable pageable);

    /**
     * 특정 사용자의 댓글 목록 조회
     * @param authorId 작성자 ID
     * @return 댓글 목록 (최신순)
     */
    @Query("SELECT c FROM Comment c JOIN FETCH c.post WHERE c.author.id = :authorId ORDER BY c.id DESC")
    List<Comment> findByAuthorIdWithPost(Long authorId);

    /**
     * 특정 게시글의 댓글 수 조회 (답글 포함)
     * @param postId 게시글 ID
     * @return 댓글 수
     */
    long countByPostId(Long postId);
}

