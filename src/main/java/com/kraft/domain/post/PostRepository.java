package com.kraft.domain.post;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PostRepository extends JpaRepository<Post, Long> {

    /**
     * 목록 검색·분류 조회. {@code keyword}는 제목 OR 본문에 대소문자 구분 없이 포함되면
     * 매치되고, {@code category}와 함께 지정하면 AND로 좁혀진다. 두 조건 모두 null이면
     * {@link #findAllDesc}과 동일하게 전체 목록을 최신순으로 반환한다.
     */
    @Query(value = "SELECT p FROM Post p JOIN FETCH p.user "
            + "WHERE (:category IS NULL OR p.category = :category) "
            + "AND (:keyword IS NULL "
            + "     OR LOWER(p.title) LIKE LOWER(CONCAT('%', :keyword, '%')) "
            + "     OR LOWER(p.content) LIKE LOWER(CONCAT('%', :keyword, '%'))) "
            + "ORDER BY p.id DESC",
            countQuery = "SELECT COUNT(p) FROM Post p "
                    + "WHERE (:category IS NULL OR p.category = :category) "
                    + "AND (:keyword IS NULL "
                    + "     OR LOWER(p.title) LIKE LOWER(CONCAT('%', :keyword, '%')) "
                    + "     OR LOWER(p.content) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Post> search(@Param("keyword") String keyword, @Param("category") Category category, Pageable pageable);

    @Query("SELECT p FROM Post p JOIN FETCH p.user ORDER BY p.viewCount DESC, p.id DESC")
    List<Post> findTopByViewCountDesc(Pageable pageable);
}
