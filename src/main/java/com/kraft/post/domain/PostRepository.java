package com.kraft.post.domain;

import com.kraft.post.dto.PostRowDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PostRepository extends JpaRepository<Post, Long> {

    /**
     * 목록 검색·분류 조회. {@code keyword}는 제목 OR 본문에 대소문자 구분 없이 포함되면
     * 매치되고, {@code category}와 함께 지정하면 AND로 좁혀진다. 두 조건 모두 null이면
     * {@link #findAllDesc}과 동일하게 전체 목록을 최신순으로 반환한다.
     * <p>
     * 목록 화면은 제목·작성자·날짜·분류·조회수만 쓰므로 {@link PostRowDto}로 직접 SELECT해
     * {@code content}(TEXT) 컬럼과 작성자 엔티티 전체를 결과에 싣지 않는다(개선 보고서
     * "게시판 목록의 불필요한 열과 집계"). {@code content}는 WHERE 절 매칭에는 여전히
     * 쓰이지만 SELECT 목록에는 없다.
     */
    @Query(value = "SELECT new com.kraft.post.dto.PostRowDto("
            + "p.id, p.title, u.name, p.updatedAt, p.category, p.viewCount) "
            + "FROM Post p JOIN p.user u "
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
    Page<PostRowDto> search(@Param("keyword") String keyword, @Param("category") Category category, Pageable pageable);

    @Query("SELECT new com.kraft.post.dto.PostRowDto("
            + "p.id, p.title, u.name, p.updatedAt, p.category, p.viewCount) "
            + "FROM Post p JOIN p.user u ORDER BY p.viewCount DESC, p.id DESC")
    List<PostRowDto> findTopByViewCountDesc(Pageable pageable);

    /**
     * 여러 id를 한 번에 조회한다(N+1 방지). 신고 목록이 페이지 안의 게시글 대상들을 한 번에
     * 묶어 조회할 때 쓴다(개선 보고서 "신고 목록의 대상별 조회").
     */
    @Query("SELECT p FROM Post p JOIN FETCH p.user WHERE p.id IN :ids")
    List<Post> findAllByIdInWithUser(@Param("ids") List<Long> ids);

    /**
     * 조회수만 원자적으로 1 늘린다. 엔티티를 읽어 필드를 바꾸고 변경 감지에 맡기면 Hibernate가
     * 제목·본문·분류·감사 필드까지 함께 UPDATE에 실어, 겹친 편집 트랜잭션의 저장 내용을
     * 열람만으로 되돌릴 수 있다(개선 보고서 F02·F11). 이 벌크 UPDATE는 view_count 컬럼 하나만
     * 건드리고 {@code @LastModifiedDate}도 발동시키지 않는다.
     * <p>
     * {@code clearAutomatically}로 영속성 컨텍스트를 비워, 이 호출 뒤에 읽는 엔티티가 늘어난
     * 조회수를 그대로 갖게 한다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Post p SET p.viewCount = p.viewCount + 1 WHERE p.id = :id")
    int increaseViewCount(@Param("id") Long id);

    /**
     * 상세 화면 하단의 관련 게시글. 같은 분류에서 현재 글을 제외하고 최신순으로 뽑는다.
     */
    @Query("SELECT new com.kraft.post.dto.PostRowDto("
            + "p.id, p.title, u.name, p.updatedAt, p.category, p.viewCount) "
            + "FROM Post p JOIN p.user u "
            + "WHERE p.category = :category AND p.id <> :excludeId "
            + "ORDER BY p.id DESC")
    List<PostRowDto> findRelated(@Param("category") Category category, @Param("excludeId") Long excludeId, Pageable pageable);
}
