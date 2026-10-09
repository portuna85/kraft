package com.kraft.post.domain;

import com.kraft.post.dto.PostRowDto;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PostRepository extends JpaRepository<Post, Long> {

    /**
     * 일반 사용자에게 보이는 글의 조건. 목록·검색·인기글·고정·관련 글·sitemap·조회수 증가가 모두 쓴다.
     * 캐시를 사용자 구분 없이 공유하므로 관리자에게도 숨긴 글은 목록에서 뺀다.
     * {@code @SQLRestriction} 대신 쓰는 이유는 복구·영구 삭제·관리자 상세가 숨긴 행을 읽어야 해서다.
     * 누락은 {@code PostRepositoryVisibilityGuardTest}가 잡는다. 끝 공백은 이어 붙이기용.
     */
    String VISIBLE = "p.deletedAt IS NULL AND p.blindedAt IS NULL ";

    /** {@link #search}·{@link #searchWithoutCount}가 공유하는 SELECT. */
    String SEARCH_SELECT = "SELECT new com.kraft.post.dto.PostRowDto("
            + "p.id, p.title, u.name, p.createdAt, p.updatedAt, p.category, p.viewCount) "
            + "FROM Post p JOIN p.user u ";

    /** 검색 조건(분류 AND (제목 OR [본문])). COUNT 쿼리도 같은 조건을 쓴다. */
    String SEARCH_WHERE = "WHERE " + VISIBLE + "AND (:category IS NULL OR p.category = :category) "
            + "AND (:keyword IS NULL "
            + "     OR LOWER(p.title) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\' "
            + "     OR (:searchContent = true AND LOWER(p.content) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\'))";

    /**
     * 목록 검색·분류 조회. 본문(TEXT)은 SELECT하지 않는다.
     * <ul>
     * <li>정렬은 {@code pageable}에만 맡긴다 — 고정 ORDER BY를 두면 요청한 정렬이 무시된다.
     *     호출자는 {@code PostSortPolicy.effectiveSort}의 Sort를 넘겨야 한다.</li>
     * <li>{@code keyword}는 호출 전에 {@code %}·{@code _}를 이스케이프해 넘긴다({@code ESCAPE '\'}).</li>
     * <li>{@code searchContent}가 false(기본)면 제목만 본다 — 본문 LIKE가 가장 비싸다.</li>
     * </ul>
     */
    @Query(value = SEARCH_SELECT + SEARCH_WHERE,
            countQuery = "SELECT COUNT(p) FROM Post p " + SEARCH_WHERE)
    Page<PostRowDto> search(@Param("keyword") String keyword, @Param("category") Category category,
                             @Param("searchContent") boolean searchContent, Pageable pageable);

    /**
     * {@link #search}와 같지만 전체 건수를 세지 않는다. 검색어가 있으면 {@code LIKE '%kw%'}의 COUNT는
     * 항상 전체 스캔이라 검색 비용의 대부분이었다. 검색어 없는 목록은 COUNT가 싸므로 {@link #search}를 쓴다.
     * <p>
     * FULLTEXT로 바꾸지 않는다: MariaDB 기본 파서는 2글자 한국어와 부분 문자열을 찾지 못한다(2026-10-05 실험).
     * LOWER()는 H2 테스트가 대소문자를 구분해서 필요하다.
     */
    @Query(SEARCH_SELECT + SEARCH_WHERE)
    Slice<PostRowDto> searchWithoutCount(@Param("keyword") String keyword, @Param("category") Category category,
                                          @Param("searchContent") boolean searchContent, Pageable pageable);

    /** 인기글: {@code since} 이후 글 중 조회수 상위. 오래된 글의 독점을 막으려 기간으로 좁힌다. */
    @Query("SELECT new com.kraft.post.dto.PostRowDto("
            + "p.id, p.title, u.name, p.createdAt, p.updatedAt, p.category, p.viewCount) "
            + "FROM Post p JOIN p.user u WHERE " + VISIBLE + "AND p.createdAt >= :since "
            + "ORDER BY p.viewCount DESC, p.id DESC")
    List<PostRowDto> findTopByViewCountDesc(@Param("since") LocalDateTime since, Pageable pageable);

    /** 고정 기한이 남은 글(최신순). 페이지 계산을 흐트러뜨리지 않게 {@link #search}와 따로 조회한다. */
    @Query("SELECT new com.kraft.post.dto.PostRowDto("
            + "p.id, p.title, u.name, p.createdAt, p.updatedAt, p.category, p.viewCount) "
            + "FROM Post p JOIN p.user u WHERE " + VISIBLE + "AND p.pinnedUntil > :now "
            + "ORDER BY p.id DESC")
    List<PostRowDto> findPinned(@Param("now") LocalDateTime now, Pageable pageable);

    /**
     * {@code until}까지 고정한다. 상태 컬럼은 전용 UPDATE로만 바꾼다 — version·updatedAt을 건드리지 않아
     * "(수정됨)"이나 가짜 편집 충돌이 생기지 않는다. 보이지 않는 글이면 0.
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Post p SET p.pinnedUntil = :until WHERE p.id = :id AND " + VISIBLE)
    int pin(@Param("id") Long id, @Param("until") LocalDateTime until);

    /** 고정을 푼다(숨긴 글 포함). 고정된 글이 아니면 0. */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Post p SET p.pinnedUntil = NULL WHERE p.id = :id AND p.pinnedUntil IS NOT NULL")
    int unpin(@Param("id") Long id);

    /** 지금 고정 중인 글 수({@code excludeId}는 뺀다 — 이미 고정된 글의 기한을 바꾸는 경우). */
    @Query("SELECT COUNT(p) FROM Post p WHERE " + VISIBLE + "AND p.pinnedUntil > :now AND p.id <> :excludeId")
    long countPinnedExcluding(@Param("excludeId") Long excludeId, @Param("now") LocalDateTime now);

    /** sitemap용 최소 프로젝션 — 본문(TEXT)과 작성자를 읽지 않는다. */
    interface SitemapRow {
        Long getId();

        LocalDateTime getUpdatedAt();
    }

    @Query("SELECT p.id AS id, p.updatedAt AS updatedAt FROM Post p WHERE " + VISIBLE + "ORDER BY p.id")
    List<SitemapRow> findSitemapRows(Pageable pageable);

    /** 여러 id를 작성자와 함께 한 번에 조회한다(N+1 방지). */
    @Query("SELECT p FROM Post p JOIN FETCH p.user WHERE p.id IN :ids AND p.deletedAt IS NULL")
    List<Post> findAllByIdInWithUser(@Param("ids") List<Long> ids);

    /**
     * 조회수만 원자적으로 1 늘린다. 변경 감지에 맡기면 모든 컬럼이 UPDATE에 실려 겹친 편집을 되돌릴 수 있다.
     * {@code clearAutomatically}로 이후 읽는 엔티티가 새 조회수를 갖게 한다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Post p SET p.viewCount = p.viewCount + 1 WHERE p.id = :id AND " + VISIBLE)
    int increaseViewCount(@Param("id") Long id);

    /** 상세 화면용 단건 조회. 작성자를 함께 가져와 지연 로딩 쿼리를 없앤다. */
    @Query("SELECT p FROM Post p JOIN FETCH p.user WHERE p.id = :id")
    Optional<Post> findByIdWithUser(@Param("id") Long id);

    /** 상세 화면 하단의 관련 글: 같은 분류에서 현재 글을 빼고 최신순. */
    @Query("SELECT new com.kraft.post.dto.PostRowDto("
            + "p.id, p.title, u.name, p.createdAt, p.updatedAt, p.category, p.viewCount) "
            + "FROM Post p JOIN p.user u "
            + "WHERE " + VISIBLE + "AND p.category = :category AND p.id <> :excludeId "
            + "ORDER BY p.id DESC")
    List<PostRowDto> findRelated(@Param("category") Category category, @Param("excludeId") Long excludeId, Pageable pageable);

    /** 일반 사용자에게 보이는 글인지. 댓글 저장처럼 존재 여부만 필요한 곳이 쓴다. */
    @Query("SELECT COUNT(p) > 0 FROM Post p WHERE p.id = :id AND " + VISIBLE)
    boolean existsVisibleById(@Param("id") Long id);

    /**
     * 소프트 삭제. {@code deletedAt}은 {@code updatable = false}라 이 UPDATE로만 바꾼다. 이미 삭제됐으면 0.
     * 미리 읽어 둔 엔티티를 떼어 내지 않도록 {@code clearAutomatically}는 쓰지 않는다.
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Post p SET p.deletedAt = :now WHERE p.id = :id AND p.deletedAt IS NULL")
    int softDelete(@Param("id") Long id, @Param("now") LocalDateTime now);

    /** 관리자가 글을 숨긴다(전용 UPDATE). 이미 숨겨졌거나 삭제됐으면 0. */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Post p SET p.blindedAt = :now WHERE p.id = :id AND p.deletedAt IS NULL AND p.blindedAt IS NULL")
    int blind(@Param("id") Long id, @Param("now") LocalDateTime now);

    /** 숨김을 푼다. 숨겨진 글이 아니면 0을 돌려준다. */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Post p SET p.blindedAt = NULL WHERE p.id = :id AND p.deletedAt IS NULL AND p.blindedAt IS NOT NULL")
    int unblind(@Param("id") Long id);

    /** 소프트 삭제를 되돌린다. 삭제된 글이 아니면 0을 돌려준다. */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Post p SET p.deletedAt = NULL WHERE p.id = :id AND p.deletedAt IS NOT NULL")
    int restore(@Param("id") Long id);

    /** {@code threshold} 이전에 삭제된 글 id를 커서 뒤부터. 실패한 행에 갇히지 않게 offset 대신 커서를 쓴다. */
    @Query("SELECT p.id FROM Post p WHERE p.deletedAt < :threshold AND p.id > :afterId ORDER BY p.id")
    List<Long> findIdsDeletedBefore(@Param("threshold") LocalDateTime threshold,
                                    @Param("afterId") Long afterId, Pageable pageable);

    /** 영구 삭제 직전에 행을 잠가 읽는다 — 그 사이 관리자가 복구하는 경쟁을 막는다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Post p WHERE p.id = :id")
    Optional<Post> findByIdForPurge(@Param("id") Long id);
}
