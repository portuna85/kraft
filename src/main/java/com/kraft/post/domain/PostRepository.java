package com.kraft.post.domain;

import com.kraft.post.dto.PostRowDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
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
     * <p>
     * 정렬은 고정 ORDER BY 없이 {@code pageable}에 전적으로 맡긴다(B10) — 여기에 고정
     * {@code ORDER BY p.id DESC}를 두면 Spring Data가 pageable의 Sort를 그 뒤에 덧붙일
     * 뿐이라, id가 고유한 이상 요청한 정렬(viewCount·updatedAt)이 실제 반환 순서에
     * 반영되지 않는다. 호출자({@code PostService.findAllDesc})가
     * {@code PostSortPolicy.effectiveSort}로 만든 Sort를 담은 pageable을 넘겨야
     * 정렬이 보장된다 — 정렬이 없는 pageable을 그대로 넘기면 순서가 정의되지 않는다.
     * <p>
     * {@code keyword}는 호출 전에 {@code PostService.normalize}가 {@code %}·{@code _}를
     * 이스케이프해 넘긴다(개선 보고서 A-BE-02 1단계) — 그러지 않으면 사용자가 입력한 그
     * 문자가 그대로 와일드카드로 해석되어({@code q=%}는 전체 목록과 같고 {@code q=_}는
     * 모든 글과 일치) 검색이 사실상 무력화된다. {@code ESCAPE '\'}가 그 이스케이프를
     * 실제로 해석하게 한다.
     * <p>
     * {@code searchContent}가 false면 제목만 본다(A-BE-02 2단계, 기본값). 본문(TEXT)까지
     * 뒤지는 것은 선행 와일드카드 LIKE 전체 스캔 비용이 가장 큰 부분이라, 필요할 때만
     * 켜게 한다({@code PostService.findAllDesc}가 기본값을 정한다).
     */
    @Query(value = "SELECT new com.kraft.post.dto.PostRowDto("
            + "p.id, p.title, u.name, p.createdAt, p.updatedAt, p.category, p.viewCount) "
            + "FROM Post p JOIN p.user u "
            + "WHERE (:category IS NULL OR p.category = :category) "
            + "AND (:keyword IS NULL "
            + "     OR LOWER(p.title) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\' "
            + "     OR (:searchContent = true AND LOWER(p.content) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\'))",
            countQuery = "SELECT COUNT(p) FROM Post p "
                    + "WHERE (:category IS NULL OR p.category = :category) "
                    + "AND (:keyword IS NULL "
                    + "     OR LOWER(p.title) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\' "
                    + "     OR (:searchContent = true AND LOWER(p.content) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\'))")
    Page<PostRowDto> search(@Param("keyword") String keyword, @Param("category") Category category,
                             @Param("searchContent") boolean searchContent, Pageable pageable);

    /**
     * 인기글 후보를 최근 글로 좁힌다(전체 리뷰 2026-09-26 A-BE-10) — 누적 조회수만 보면
     * 오래전에 조회수를 많이 쌓은 글이 자리를 영영 독점해, 새 글이 아무리 좋아도 인기글에
     * 오를 수 없었다. {@code since} 이후 작성된 글 중 조회수 상위를 뽑는다.
     */
    @Query("SELECT new com.kraft.post.dto.PostRowDto("
            + "p.id, p.title, u.name, p.createdAt, p.updatedAt, p.category, p.viewCount) "
            + "FROM Post p JOIN p.user u WHERE p.createdAt >= :since "
            + "ORDER BY p.viewCount DESC, p.id DESC")
    List<PostRowDto> findTopByViewCountDesc(@Param("since") LocalDateTime since, Pageable pageable);

    /**
     * 목록 상단에 고정할 최근 공지(A-BE-05). 별도 쿼리로 두는 이유는 {@link #search}의
     * {@code totalElements}·페이지 계산을 흐트러뜨리지 않기 위해서다 — 정렬 로직에 섞으면
     * "몇 번째 페이지에 공지가 몇 개 끼어 있는가"를 계산해야 하는 문제가 생긴다.
     */
    @Query("SELECT new com.kraft.post.dto.PostRowDto("
            + "p.id, p.title, u.name, p.createdAt, p.updatedAt, p.category, p.viewCount) "
            + "FROM Post p JOIN p.user u WHERE p.category = com.kraft.post.domain.Category.NOTICE "
            + "ORDER BY p.id DESC")
    List<PostRowDto> findPinnedNotices(Pageable pageable);

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
            + "p.id, p.title, u.name, p.createdAt, p.updatedAt, p.category, p.viewCount) "
            + "FROM Post p JOIN p.user u "
            + "WHERE p.category = :category AND p.id <> :excludeId "
            + "ORDER BY p.id DESC")
    List<PostRowDto> findRelated(@Param("category") Category category, @Param("excludeId") Long excludeId, Pageable pageable);
}
