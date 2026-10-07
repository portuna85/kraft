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
     * 일반 사용자에게 보여줄 글의 조건. 목록·검색·인기글·공지·관련 글·sitemap·조회수 증가가 모두 이
     * 조건을 거친다 — 소프트 삭제되거나 관리자가 숨긴 글이 어느 한 곳에서라도 새면 목록에 남는다.
     * 숨김은 관리자 여부와 무관하게 목록에서 뺀다: 목록·인기글·공지는 사용자 구분 없이 캐시를 공유하기
     * 때문이다. 관리자는 신고 화면이나 주소로 들어간다.
     * {@code @SQLRestriction}을 쓰지 않는 이유는 복구·영구 삭제·관리자 상세가 숨겨진 행을 일부러
     * 읽어야 하고, to-one 지연 로딩이 걸러진 행을 가리킬 수 있어서다. 이 조건을 빠뜨리지 않았는지는
     * {@code PostRepositoryVisibilityGuardTest}가 지킨다. 끝에 공백이 있어 바로 이어 붙인다.
     */
    String VISIBLE = "p.deletedAt IS NULL AND p.blindedAt IS NULL ";

    /** {@link #search}·{@link #searchWithoutCount}가 공유하는 SELECT. 두 쿼리가 어긋나지 않게 한 곳에 둔다. */
    String SEARCH_SELECT = "SELECT new com.kraft.post.dto.PostRowDto("
            + "p.id, p.title, u.name, p.createdAt, p.updatedAt, p.category, p.viewCount) "
            + "FROM Post p JOIN p.user u ";

    /** 검색 조건(분류 AND (제목 OR [본문])). COUNT 쿼리도 같은 조건을 쓴다. */
    String SEARCH_WHERE = "WHERE " + VISIBLE + "AND (:category IS NULL OR p.category = :category) "
            + "AND (:keyword IS NULL "
            + "     OR LOWER(p.title) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\' "
            + "     OR (:searchContent = true AND LOWER(p.content) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\'))";

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
    @Query(value = SEARCH_SELECT + SEARCH_WHERE,
            countQuery = "SELECT COUNT(p) FROM Post p " + SEARCH_WHERE)
    Page<PostRowDto> search(@Param("keyword") String keyword, @Param("category") Category category,
                             @Param("searchContent") boolean searchContent, Pageable pageable);

    /**
     * {@link #search}와 같은 조건·정렬이지만 전체 건수를 세지 않는다(BE-08). 검색어가 있으면
     * {@code LIKE '%kw%'}가 인덱스를 못 타서 COUNT는 매칭 여부와 상관없이 항상 테이블 전체를
     * 읽는다 — 결과 쿼리는 정렬 순서대로 {@code size + 1}개를 찾으면 멈추는데, COUNT는 그
     * 이점이 없어 검색 비용의 대부분이었다. Spring Data가 {@code size + 1}개를 읽어 다음
     * 페이지가 있는지({@link Slice#hasNext()})만 알려 준다.
     * <p>
     * <b>FULLTEXT로 바꾸지 않는다(BE-08 결론, 2026-10-05).</b> 운영과 같은 MariaDB 11.7.2에서 실험했다.
     * ngram 파서가 없고 {@code innodb_ft_min_token_size}가 3이라, 이 사이트의 핵심 검색어인 "로또"·"번호"
     * 같은 2글자 한국어는 {@code MATCH ... AGAINST('로또*')}로도 0건이고 "첨번"처럼 단어 중간을 찾는
     * 검색도 0건이다(LIKE는 모두 찾는다). 쓰려면 서버 설정을 바꿔 인덱스를 다시 만들어야 하고 그래도
     * 부분 문자열 검색은 되지 않는다. 비용은 5만 건(본문 125MB)에서 검색어가 하나도 안 맞는 최악의 경우
     * 제목만 약 0.7초, 본문까지 약 0.9초이고, 흔한 검색어는 size+1개를 찾으면 멈춰 약 0.5ms다. 검색은
     * 이미 IP당 분당 60회로 제한돼({@code WriteRateLimiters}) 스캔을 반복해 DB를 막기 어렵다. 게시글이
     * 수만 건을 넘기고 실제로 느려지면 FULLTEXT가 아니라 별도 검색 인덱스(부분 문자열용 n-gram 테이블 등)를
     * 검토한다. 이 쿼리의 LOWER()는 H2 테스트가 대소문자를 구분해서 필요하다.
     * <p>
     * 검색어 없는 전체·분류 목록은 인덱스로 세는 COUNT가 싸고 "총 N개"·번호 이동이 그 값에
     * 기대므로 계속 {@link #search}를 쓴다. 검색어 이스케이프·정렬 규칙은 {@link #search}와 같다.
     */
    @Query(SEARCH_SELECT + SEARCH_WHERE)
    Slice<PostRowDto> searchWithoutCount(@Param("keyword") String keyword, @Param("category") Category category,
                                          @Param("searchContent") boolean searchContent, Pageable pageable);

    /**
     * 인기글 후보를 최근 글로 좁힌다(전체 리뷰 2026-09-26 A-BE-10) — 누적 조회수만 보면
     * 오래전에 조회수를 많이 쌓은 글이 자리를 영영 독점해, 새 글이 아무리 좋아도 인기글에
     * 오를 수 없었다. {@code since} 이후 작성된 글 중 조회수 상위를 뽑는다.
     */
    @Query("SELECT new com.kraft.post.dto.PostRowDto("
            + "p.id, p.title, u.name, p.createdAt, p.updatedAt, p.category, p.viewCount) "
            + "FROM Post p JOIN p.user u WHERE " + VISIBLE + "AND p.createdAt >= :since "
            + "ORDER BY p.viewCount DESC, p.id DESC")
    List<PostRowDto> findTopByViewCountDesc(@Param("since") LocalDateTime since, Pageable pageable);

    /**
     * 목록 상단에 고정할 글(A-BE-05) — 관리자가 {@code pinned_until}을 정해 둔 글 중 아직 기한이 남은 것을
     * 최신순으로 뽑는다. 예전에는 최신 공지(NOTICE) 몇 개를 자동으로 고정했지만, 이제 분류와 무관하게 관리자가
     * 고른다. 별도 쿼리로 두는 이유는 {@link #search}의 {@code totalElements}·페이지 계산을 흐트러뜨리지
     * 않기 위해서다 — 정렬 로직에 섞으면 "몇 번째 페이지에 고정 글이 몇 개 끼어 있는가"를 계산해야 하는
     * 문제가 생긴다.
     */
    @Query("SELECT new com.kraft.post.dto.PostRowDto("
            + "p.id, p.title, u.name, p.createdAt, p.updatedAt, p.category, p.viewCount) "
            + "FROM Post p JOIN p.user u WHERE " + VISIBLE + "AND p.pinnedUntil > :now "
            + "ORDER BY p.id DESC")
    List<PostRowDto> findPinned(@Param("now") LocalDateTime now, Pageable pageable);

    /**
     * 글을 {@code until}까지 고정한다. 다른 상태 컬럼과 같은 이유로 전용 UPDATE만 쓴다 — version·updatedAt을
     * 건드리지 않아 고정만으로 "(수정됨)"이 붙거나 열려 있던 편집 탭이 가짜 충돌을 받지 않는다. 보이지 않는
     * (삭제·숨김) 글이면 0을 돌려준다.
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Post p SET p.pinnedUntil = :until WHERE p.id = :id AND " + VISIBLE)
    int pin(@Param("id") Long id, @Param("until") LocalDateTime until);

    /**
     * 고정을 푼다. 삭제·숨김 상태와 무관하게 푼다 — 숨긴 글의 고정도 풀 수 있어야 한다. 고정된 글이 아니면
     * 0을 돌려준다.
     */
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

    /**
     * 여러 id를 한 번에 조회한다(N+1 방지). 신고 목록이 페이지 안의 게시글 대상들을 한 번에
     * 묶어 조회할 때 쓴다(개선 보고서 "신고 목록의 대상별 조회").
     */
    @Query("SELECT p FROM Post p JOIN FETCH p.user WHERE p.id IN :ids AND p.deletedAt IS NULL")
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
    @Query("UPDATE Post p SET p.viewCount = p.viewCount + 1 WHERE p.id = :id AND " + VISIBLE)
    int increaseViewCount(@Param("id") Long id);

    /**
     * 상세 화면용 단건 조회. 작성자를 같은 쿼리로 가져온다 — {@code findById}로 읽으면 화면이
     * {@code post.getUser().getName()}을 부르는 순간 지연 로딩 쿼리가 하나 더 나갔다(BE-05).
     */
    @Query("SELECT p FROM Post p JOIN FETCH p.user WHERE p.id = :id")
    Optional<Post> findByIdWithUser(@Param("id") Long id);

    /**
     * 상세 화면 하단의 관련 게시글. 같은 분류에서 현재 글을 제외하고 최신순으로 뽑는다.
     */
    @Query("SELECT new com.kraft.post.dto.PostRowDto("
            + "p.id, p.title, u.name, p.createdAt, p.updatedAt, p.category, p.viewCount) "
            + "FROM Post p JOIN p.user u "
            + "WHERE " + VISIBLE + "AND p.category = :category AND p.id <> :excludeId "
            + "ORDER BY p.id DESC")
    List<PostRowDto> findRelated(@Param("category") Category category, @Param("excludeId") Long excludeId, Pageable pageable);

    /** 일반 사용자에게 보이는 글인지(삭제되지 않았는지). 댓글 저장처럼 존재 여부만 필요한 곳이 쓴다. */
    @Query("SELECT COUNT(p) > 0 FROM Post p WHERE p.id = :id AND " + VISIBLE)
    boolean existsVisibleById(@Param("id") Long id);

    /**
     * 소프트 삭제한다. {@code updatable = false}인 컬럼이라 엔티티로는 바꿀 수 없고, 이 전용 UPDATE만
     * 쓴다({@code Post.deletedAt} 주석). version·updatedAt을 건드리지 않는다. 이미 삭제된 글이면 0을
     * 돌려준다. {@code clearAutomatically}는 쓰지 않는다 — 신고 처리가 미리 읽어 둔 엔티티를 떼어 낸다.
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Post p SET p.deletedAt = :now WHERE p.id = :id AND p.deletedAt IS NULL")
    int softDelete(@Param("id") Long id, @Param("now") LocalDateTime now);

    /**
     * 관리자가 글을 숨긴다(신고 처리). {@link #softDelete}와 같은 이유로 전용 UPDATE만 쓴다. 이미 숨겨졌거나
     * 삭제된 글이면 0을 돌려준다.
     */
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

    /**
     * {@code threshold} 이전에 삭제된 글의 id를 커서({@code afterId}) 뒤부터 id 순으로 돌려준다.
     * 영구 삭제 작업이 묶음마다 이어 받는다 — 한 건이 실패해도 같은 행을 계속 다시 읽지 않도록 offset이
     * 아니라 커서로 나아간다.
     */
    @Query("SELECT p.id FROM Post p WHERE p.deletedAt < :threshold AND p.id > :afterId ORDER BY p.id")
    List<Long> findIdsDeletedBefore(@Param("threshold") LocalDateTime threshold,
                                    @Param("afterId") Long afterId, Pageable pageable);

    /** 영구 삭제 직전에 행을 잠가 읽는다 — 그 사이 관리자가 복구하는 경쟁을 막는다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Post p WHERE p.id = :id")
    Optional<Post> findByIdForPurge(@Param("id") Long id);
}
