package com.kraft.domain.post;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PostRepository extends JpaRepository<Post, Long> {

    /**
     * 게시글 목록 조회 (N+1 문제 해결 - Fetch Join 사용)
     * @return 작성자 정보가 포함된 게시글 목록 (최신순)
     */
    @Query("SELECT p FROM Post p JOIN FETCH p.author ORDER BY p.id DESC")
    List<Post> findAllDesc();

    /**
     * 게시글 목록 페이징 조회 (N+1 문제 해결)
     * @param pageable 페이징 정보
     * @return 작성자 정보가 포함된 게시글 페이지
     */
    @Query(value = "SELECT p FROM Post p JOIN FETCH p.author",
           countQuery = "SELECT COUNT(p) FROM Post p")
    Page<Post> findAllWithAuthor(Pageable pageable);

    /**
     * ID로 게시글 단건 조회 (N+1 문제 해결)
     * @param id 게시글 ID
     * @return 작성자 정보가 포함된 게시글
     */
    @Query("SELECT p FROM Post p JOIN FETCH p.author WHERE p.id = :id")
    Optional<Post> findByIdWithAuthor(Long id);

    /**
     * 특정 사용자의 게시글 목록 조회
     * @param authorId 작성자 ID
     * @return 게시글 목록
     */
    @Query("SELECT p FROM Post p WHERE p.author.id = :authorId ORDER BY p.id DESC")
    List<Post> findByAuthorId(Long authorId);

    /**
     * 제목으로 게시글 검색 (LIKE)
     * @param keyword 검색 키워드
     * @param pageable 페이지 정보
     * @return 검색 결과 페이지
     */
    @Query("SELECT p FROM Post p JOIN FETCH p.author WHERE p.title LIKE %:keyword% ORDER BY p.id DESC")
    Page<Post> searchByTitle(String keyword, Pageable pageable);

    /**
     * 제목 또는 내용으로 게시글 검색
     * @param keyword 검색 키워드
     * @param pageable 페이지 정보
     * @return 검색 결과 페이지
     */
    @Query(value = "SELECT p FROM Post p JOIN FETCH p.author WHERE p.title LIKE %:keyword% OR p.content LIKE %:keyword%",
           countQuery = "SELECT COUNT(p) FROM Post p WHERE p.title LIKE %:keyword% OR p.content LIKE %:keyword%")
    Page<Post> searchByTitleOrContent(String keyword, Pageable pageable);

    /**
     * 인기 게시글 조회 (조회수 기준)
     * @param pageable 페이지 정보
     * @return 인기 게시글 페이지
     */
    @Query(value = "SELECT p FROM Post p JOIN FETCH p.author ORDER BY p.viewCount DESC, p.id DESC",
           countQuery = "SELECT COUNT(p) FROM Post p")
    Page<Post> findPopularPosts(Pageable pageable);

    /**
     * 카테고리별 게시글 조회
     * @param categoryId 카테고리 ID
     * @param pageable 페이징 정보
     * @return 게시글 페이지
     */
    @Query(value = "SELECT p FROM Post p JOIN FETCH p.author WHERE p.category.id = :categoryId",
           countQuery = "SELECT COUNT(p) FROM Post p WHERE p.category.id = :categoryId")
    Page<Post> findByCategoryId(Long categoryId, Pageable pageable);
}
