package com.kraft.post.domain;

import com.kraft.config.JpaConfig;
import com.kraft.post.dto.PostRowDto;
import com.kraft.user.domain.EmailAttributeConverter;
import com.kraft.user.domain.Role;
import com.kraft.user.domain.User;
import com.kraft.user.domain.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link PostRepository} 통합 테스트. {@code @DataJpaTest}는 Entity/Repository 관련 빈만 스캔하므로 {@code @EnableJpaAuditing}이 선언된 {@link JpaConfig}는 기본으로 포함되지 않는다 —
 * {@code @Import}로 명시해 감사 필드({@code createdAt}/{@code updatedAt})까지 실제로 채워지는지 검증한다.
 */
@DataJpaTest
@Import({JpaConfig.class, EmailAttributeConverter.class})
class PostRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private UserRepository userRepository;

    private User user;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        user = userRepository.save(
                User.builder().name("tester").email("tester@example.com").password("pw").role(Role.USER).build());
    }

    /**
     * search 자체는 고정 ORDER BY를 두지 않고 pageable의 Sort에 정렬을 전적으로 맡긴다 — 정렬 보정({@code PostSortPolicy.effectiveSort})은 {@code PostService.findAllDesc}가 담당하므로,
     * 이 테스트는 그 서비스가 넘기는 것과 같은 형태(id 내림차순 Sort)를 직접 전달한다.
     */
    @Test
    @DisplayName("search: id 내림차순 Sort를 주면 그 순서로, 작성자 이름이 함께 조회된다")
    void search_withIdDescSort_returnsPostsDescWithAuthorName() {
        Post first = postRepository.save(Post.builder().title("첫 글").content("c1").user(user).build());
        Post second = postRepository.save(Post.builder().title("둘째 글").content("c2").user(user).build());
        em.flush();
        em.clear();

        Page<PostRowDto> page = postRepository.search(null, null, false,
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "id")));

        assertThat(page.getContent()).extracting(PostRowDto::id)
                .containsExactly(second.getId(), first.getId());
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent().get(0).author()).isEqualTo("tester");
    }

    @Test
    @DisplayName("search: content 컬럼을 SELECT 결과에 싣지 않는다")
    void search_doesNotSelectContentColumn() {
        postRepository.save(Post.builder().title("제목").content("본문").user(user).build());
        em.flush();
        em.clear();

        Page<PostRowDto> page = postRepository.search(null, null, false, PageRequest.of(0, 10));

        // PostRowDto에는 content 필드 자체가 없다 — 컴파일 시점에 이미 응답에 본문이 없음을 보장하며, 이 테스트는 그 계약이 유지되는지 지킨다.
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).title()).isEqualTo("제목");
    }

    @Test
    @DisplayName("search: size만큼 잘라서 반환하고 totalPages를 정확히 계산한다")
    void search_paginatesAndCalculatesTotalPagesCorrectly() {
        for (int i = 1; i <= 15; i++) {
            postRepository.save(Post.builder().title("글 " + i).content("내용 " + i).user(user).build());
        }
        em.flush();
        em.clear();

        Page<PostRowDto> firstPage = postRepository.search(null, null, false, PageRequest.of(0, 10));
        Page<PostRowDto> secondPage = postRepository.search(null, null, false, PageRequest.of(1, 10));

        assertThat(firstPage.getContent()).hasSize(10);
        assertThat(firstPage.getTotalElements()).isEqualTo(15);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
        assertThat(firstPage.isFirst()).isTrue();
        assertThat(firstPage.isLast()).isFalse();

        assertThat(secondPage.getContent()).hasSize(5);
        assertThat(secondPage.isLast()).isTrue();
    }

    /** COUNT 없는 검색은 같은 조건에서 {@code search}와 같은 행을 같은 순서로 돌려줘야 한다(WHERE 절은 두 쿼리가 상수를 공유하지만, 실제 DB 결과로 한 번 더 못 박는다). */
    @Test
    @DisplayName("searchWithoutCount: 제목·본문 범위, 분류, 이스케이프 조건에서 search와 같은 행을 같은 순서로 돌려준다")
    void searchWithoutCount_returnsSameRowsAsSearch() {
        postRepository.save(Post.builder().title("Kraft 소개").content("내용").user(user).category(Category.FREE).build());
        postRepository.save(Post.builder().title("공지").content("KRAFT 업데이트").user(user).category(Category.NOTICE).build());
        postRepository.save(Post.builder().title("100% 할인").content("내용").user(user).category(Category.FREE).build());
        postRepository.save(Post.builder().title("100원 할인").content("내용").user(user).category(Category.QNA).build());
        postRepository.save(Post.builder().title("관련 없음").content("다른 내용").user(user).category(Category.FREE).build());
        em.flush();
        em.clear();

        PageRequest idDesc = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "id"));
        record Case(String keyword, Category category, boolean searchContent) {
        }
        for (Case c : List.of(
                new Case("kraft", null, false),
                new Case("kraft", null, true),
                new Case("kraft", Category.NOTICE, true),
                new Case("100\\% ", null, false),
                new Case("할인", Category.QNA, false),
                new Case("없는검색어", null, true))) {
            List<Long> expected = postRepository.search(c.keyword(), c.category(), c.searchContent(), idDesc)
                    .getContent().stream().map(PostRowDto::id).toList();

            List<Long> actual = postRepository.searchWithoutCount(c.keyword(), c.category(), c.searchContent(), idDesc)
                    .getContent().stream().map(PostRowDto::id).toList();

            assertThat(actual).as("조건 %s", c).isEqualTo(expected);
        }
    }

    /** Slice는 size + 1개를 읽어 다음 페이지가 있는지만 판정한다 — 정확히 size개면 마지막이다. */
    @Test
    @DisplayName("searchWithoutCount: size를 넘는 일치가 있을 때만 hasNext이고, 정확히 size개면 마지막 페이지다")
    void searchWithoutCount_hasNextOnlyWhenMoreThanPageSizeMatch() {
        for (int i = 1; i <= 11; i++) {
            postRepository.save(Post.builder().title("검색 글 " + i).content("c").user(user).build());
        }
        postRepository.save(Post.builder().title("다른 글").content("c").user(user).build());
        em.flush();
        em.clear();

        Sort idDesc = Sort.by(Sort.Direction.DESC, "id");
        Slice<PostRowDto> first = postRepository.searchWithoutCount("검색 글", null, false, PageRequest.of(0, 10, idDesc));
        Slice<PostRowDto> second = postRepository.searchWithoutCount("검색 글", null, false, PageRequest.of(1, 10, idDesc));
        Slice<PostRowDto> exactFit = postRepository.searchWithoutCount("검색 글", null, false, PageRequest.of(0, 11, idDesc));

        assertThat(first.getContent()).hasSize(10);
        assertThat(first.hasNext()).isTrue();
        assertThat(first.isFirst()).isTrue();
        assertThat(second.getContent()).hasSize(1);
        assertThat(second.hasNext()).isFalse();
        assertThat(second.isLast()).isTrue();
        assertThat(exactFit.getContent()).hasSize(11);
        assertThat(exactFit.hasNext()).as("정확히 size개면 다음 페이지가 없다").isFalse();
    }

    /** viewCount 정렬 + id 동점 처리를 실제 DB로 확인한다(리포지토리의 고정 ORDER BY가 앞서면 이 Sort가 반환 순서에 반영되지 않는다). */
    @Test
    @DisplayName("search: viewCount 내림차순 Sort를 주면 조회수 순서로 반환한다")
    void search_withViewCountSort_ordersByViewCountDesc() {
        Post low = postRepository.save(Post.builder().title("낮음").content("c").user(user).build());
        Post high = postRepository.save(Post.builder().title("높음").content("c").user(user).build());
        Post mid = postRepository.save(Post.builder().title("중간").content("c").user(user).build());
        // view_count는 엔티티 UPDATE에서 빠지므로(updatable=false) 실제 운영 경로와 같은 전용 원자적 UPDATE로 조회수를 올린다.
        postRepository.increaseViewCount(high.getId());
        postRepository.increaseViewCount(high.getId());
        postRepository.increaseViewCount(mid.getId());
        em.flush();
        em.clear();

        Page<PostRowDto> page = postRepository.search(null, null, false,
                PageRequest.of(0, 10, com.kraft.post.service.PostSortPolicy.effectiveSort(
                        Sort.by(Sort.Direction.DESC, "viewCount"))));

        assertThat(page.getContent()).extracting(PostRowDto::id)
                .containsExactly(high.getId(), mid.getId(), low.getId());
    }

    @Test
    @DisplayName("search: 키워드가 제목이나 본문에 포함되면(대소문자 무시) 매치한다")
    void search_matchesWhenKeywordInTitleOrContent() {
        Post titleMatch = postRepository.save(Post.builder().title("Kraft 소개").content("내용").user(user).build());
        Post contentMatch = postRepository.save(Post.builder().title("공지").content("KRAFT 업데이트 안내").user(user).build());
        postRepository.save(Post.builder().title("관련 없음").content("다른 내용").user(user).build());
        em.flush();
        em.clear();

        // searchContent=true(제목+내용)일 때만 본문 매치도 포함한다.
        Page<PostRowDto> page = postRepository.search("kraft", null, true, PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(PostRowDto::id)
                .containsExactlyInAnyOrder(titleMatch.getId(), contentMatch.getId());
    }

    @Test
    @DisplayName("search: searchContent=false(기본값)면 본문 매치는 제외하고 제목만 본다")
    void search_withSearchContentFalse_matchesTitleOnly() {
        Post titleMatch = postRepository.save(Post.builder().title("Kraft 소개").content("내용").user(user).build());
        postRepository.save(Post.builder().title("공지").content("KRAFT 업데이트 안내").user(user).build());
        em.flush();
        em.clear();

        Page<PostRowDto> page = postRepository.search("kraft", null, false, PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(PostRowDto::id).containsExactly(titleMatch.getId());
    }

    /** 이스케이프된 {@code \%}·{@code \_}는 리터럴 문자로만 매치돼야 한다 — PostService.normalize가 이스케이프해 넘기는 값을 이 쿼리의 {@code ESCAPE '\'}가 해석하는지 확인한다(리포지토리 자체는 이스케이프하지 않는다). */
    @Test
    @DisplayName("search: 이스케이프된 %·_는 와일드카드가 아니라 리터럴 문자로만 매치한다")
    void search_withEscapedWildcards_matchesOnlyLiteralCharacters() {
        Post literalMatch = postRepository.save(
                Post.builder().title("100% 할인").content("내용").user(user).build());
        postRepository.save(Post.builder().title("100원 할인").content("내용").user(user).build());
        em.flush();
        em.clear();

        // PostService.escapeLikeWildcards("100% ")와 같은 결과 — 서비스 계층 없이 리포지토리가 받는 값 그대로를 검증한다.
        Page<PostRowDto> page = postRepository.search("100\\% ", null, false, PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(PostRowDto::id)
                .containsExactly(literalMatch.getId());
    }

    @Test
    @DisplayName("search: category로 좁히면 해당 분류의 글만 반환한다")
    void search_filtersByCategory() {
        Post notice = postRepository.save(Post.builder().title("공지").content("c").user(user).category(Category.NOTICE).build());
        postRepository.save(Post.builder().title("자유글").content("c").user(user).category(Category.FREE).build());
        em.flush();
        em.clear();

        Page<PostRowDto> page = postRepository.search(null, Category.NOTICE, false, PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(PostRowDto::id).containsExactly(notice.getId());
    }

    @Test
    @DisplayName("findTopByViewCountDesc: 조회수 내림차순으로 상위 N개를 반환한다")
    void findTopByViewCountDesc_returnsTopPostsOrderedByViewCountDesc() {
        Post low = postRepository.save(Post.builder().title("낮음").content("c").user(user).build());
        Post high = postRepository.save(Post.builder().title("높음").content("c").user(user).build());
        Post mid = postRepository.save(Post.builder().title("중간").content("c").user(user).build());
        // view_count는 엔티티 UPDATE에서 빠지므로(updatable=false) 실제 운영 경로와 같은 전용 원자적 UPDATE로 조회수를 올린다.
        postRepository.increaseViewCount(high.getId());
        postRepository.increaseViewCount(high.getId());
        postRepository.increaseViewCount(mid.getId());
        em.flush();
        em.clear();

        List<PostRowDto> top2 = postRepository.findTopByViewCountDesc(
                LocalDateTime.now().minusDays(7), PageRequest.of(0, 2));

        assertThat(top2).extracting(PostRowDto::id).containsExactly(high.getId(), mid.getId());
        assertThat(low.getViewCount()).isZero();
    }

    @Test
    @DisplayName("findTopByViewCountDesc: since 이전에 작성된 글은 조회수가 높아도 제외한다")
    void findTopByViewCountDesc_excludesPostsCreatedBeforeSince() {
        Post old = postRepository.save(Post.builder().title("옛 인기글").content("c").user(user).build());
        postRepository.increaseViewCount(old.getId());
        postRepository.increaseViewCount(old.getId());
        Post recent = postRepository.save(Post.builder().title("최근 글").content("c").user(user).build());
        postRepository.increaseViewCount(recent.getId());
        em.flush();
        // BaseEntity.createdAt은 @CreatedDate라 직접 세팅할 수 없으니, 네이티브 UPDATE로 "옛 글"의 작성일만 기준 시각보다 앞으로 옮긴다.
        em.getEntityManager().createNativeQuery("UPDATE posts SET created_at = :ts WHERE id = :id")
                .setParameter("ts", LocalDateTime.now().minusDays(30))
                .setParameter("id", old.getId())
                .executeUpdate();
        em.clear();

        List<PostRowDto> top = postRepository.findTopByViewCountDesc(
                LocalDateTime.now().minusDays(7), PageRequest.of(0, 10));

        assertThat(top).extracting(PostRowDto::id).containsExactly(recent.getId());
    }

    @Test
    @DisplayName("findRelated: 같은 분류에서 현재 글을 제외하고 ID 내림차순으로 limit만큼 반환한다")
    void findRelated_returnsSameCategoryPostsExcludingCurrentOrderedByIdDesc() {
        Post current = postRepository.save(Post.builder().title("현재 글").content("c").user(user).category(Category.FREE).build());
        Post older = postRepository.save(Post.builder().title("같은 분류 옛 글").content("c").user(user).category(Category.FREE).build());
        Post newer = postRepository.save(Post.builder().title("같은 분류 새 글").content("c").user(user).category(Category.FREE).build());
        postRepository.save(Post.builder().title("다른 분류").content("c").user(user).category(Category.QNA).build());
        em.flush();
        em.clear();

        List<PostRowDto> related = postRepository.findRelated(Category.FREE, current.getId(), PageRequest.of(0, 5));

        assertThat(related).extracting(PostRowDto::id).containsExactly(newer.getId(), older.getId());
    }

    @Test
    @DisplayName("findRelated: limit만큼만 반환한다")
    void findRelated_limitsResultSize() {
        Post current = postRepository.save(Post.builder().title("현재 글").content("c").user(user).category(Category.FREE).build());
        for (int i = 1; i <= 5; i++) {
            postRepository.save(Post.builder().title("글 " + i).content("c").user(user).category(Category.FREE).build());
        }
        em.flush();
        em.clear();

        List<PostRowDto> related = postRepository.findRelated(Category.FREE, current.getId(), PageRequest.of(0, 2));

        assertThat(related).hasSize(2);
    }

    @Test
    @DisplayName("저장하면 BaseEntity의 createdAt/updatedAt이 자동으로 채워진다 (JpaConfig의 @EnableJpaAuditing)")
    void save_automaticallyPopulatesAuditFields() {
        Post saved = postRepository.save(Post.builder().title("t").content("c").user(user).build());
        em.flush();

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("update() 이후 flush하면 updatedAt이 최초 저장 시점보다 뒤로 갱신된다")
    void update_updatesUpdatedAtAfterFlush() {
        Post saved = postRepository.save(Post.builder().title("t").content("c").user(user).build());
        em.flush();
        // 타임스탬프 해상도에 기대 sleep하는 대신, 저장된 updatedAt을 한 시간 전으로 옮겨 두고 다시 읽는다.
        em.getEntityManager().createNativeQuery("UPDATE posts SET updated_at = :ts WHERE id = :id")
                .setParameter("ts", LocalDateTime.now().minusHours(1))
                .setParameter("id", saved.getId())
                .executeUpdate();
        em.getEntityManager().refresh(saved);
        var createdUpdatedAt = saved.getUpdatedAt();

        saved.update("수정된 제목", "수정된 내용", null, null, null, null);
        em.flush();

        assertThat(saved.getUpdatedAt()).isAfter(createdUpdatedAt);
    }

    @Test
    @DisplayName("소프트 삭제된 글은 목록·검색·인기글·공지·sitemap·최근 글·관련 글·조회수에서 모두 빠지고, 복구하면 돌아온다")
    void softDeletedPosts_areExcludedEverywhereAndRestorable() {
        Post kept = postRepository.save(Post.builder().title("남는 공지").content("c").user(user).category(Category.NOTICE)
                .pinnedUntil(LocalDateTime.now().plusDays(1)).build());
        Post gone = postRepository.save(Post.builder().title("지운 공지").content("c").user(user).category(Category.NOTICE)
                .pinnedUntil(LocalDateTime.now().plusDays(1)).build());
        em.flush();

        assertThat(postRepository.softDelete(gone.getId(), LocalDateTime.now())).isEqualTo(1);
        // 이미 삭제된 글은 다시 삭제되지 않는다.
        assertThat(postRepository.softDelete(gone.getId(), LocalDateTime.now())).isZero();
        em.clear();

        Sort idDesc = Sort.by(Sort.Direction.DESC, "id");
        Page<PostRowDto> page = postRepository.search(null, null, false, PageRequest.of(0, 10, idDesc));
        assertThat(page.getContent()).extracting(PostRowDto::id).containsExactly(kept.getId());
        // COUNT 쿼리도 같은 조건을 쓴다 — 총 건수가 지운 글을 세면 페이지 번호가 어긋난다.
        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(postRepository.searchWithoutCount("지운", null, false, PageRequest.of(0, 10, idDesc))).isEmpty();
        assertThat(postRepository.findTopByViewCountDesc(LocalDateTime.now().minusDays(1), PageRequest.of(0, 10)))
                .extracting(PostRowDto::id).containsExactly(kept.getId());
        assertThat(postRepository.findPinned(LocalDateTime.now(), PageRequest.of(0, 10)))
                .extracting(PostRowDto::id).containsExactly(kept.getId());
        assertThat(postRepository.findSitemapRows(PageRequest.of(0, 10)))
                .extracting(PostRepository.SitemapRow::getId).containsExactly(kept.getId());
        assertThat(postRepository.findRelated(Category.NOTICE, kept.getId(), PageRequest.of(0, 10))).isEmpty();
        assertThat(postRepository.findAllByIdInWithUser(List.of(kept.getId(), gone.getId())))
                .extracting(Post::getId).containsExactly(kept.getId());
        assertThat(postRepository.existsVisibleById(gone.getId())).isFalse();
        assertThat(postRepository.existsVisibleById(kept.getId())).isTrue();
        // 삭제된 글은 조회수도 오르지 않는다.
        assertThat(postRepository.increaseViewCount(gone.getId())).isZero();
        assertThat(postRepository.increaseViewCount(kept.getId())).isEqualTo(1);
        // 관리자 상세가 읽는 쿼리는 삭제된 행도 돌려준다.
        assertThat(postRepository.findByIdWithUser(gone.getId()).orElseThrow().isDeleted()).isTrue();

        assertThat(postRepository.restore(gone.getId())).isEqualTo(1);
        assertThat(postRepository.restore(gone.getId())).isZero();
        em.clear();
        assertThat(postRepository.search(null, null, false, PageRequest.of(0, 10, idDesc)).getContent())
                .extracting(PostRowDto::id).containsExactly(gone.getId(), kept.getId());
    }

    @Test
    @DisplayName("softDelete·restore는 version과 updatedAt을 바꾸지 않는다 — 열려 있던 편집 탭이 가짜 충돌을 받지 않는다")
    void softDelete_doesNotTouchVersionOrUpdatedAt() {
        Post post = postRepository.save(Post.builder().title("제목").content("c").user(user).build());
        em.flush();
        em.clear();
        Post before = postRepository.findById(post.getId()).orElseThrow();
        Long version = before.getVersion();
        LocalDateTime updatedAt = before.getUpdatedAt();
        em.clear();

        postRepository.softDelete(post.getId(), LocalDateTime.now());
        postRepository.restore(post.getId());
        em.clear();

        Post after = postRepository.findById(post.getId()).orElseThrow();
        assertThat(after.getVersion()).isEqualTo(version);
        assertThat(after.getUpdatedAt()).isEqualTo(updatedAt);
    }

    @Test
    @DisplayName("findIdsDeletedBefore: 보관 기간이 지난 삭제 글만 id 커서 순서로 돌려준다")
    void findIdsDeletedBefore_returnsOnlyExpiredAfterCursor() {
        Post old1 = postRepository.save(Post.builder().title("a").content("c").user(user).build());
        Post old2 = postRepository.save(Post.builder().title("b").content("c").user(user).build());
        Post recent = postRepository.save(Post.builder().title("c").content("c").user(user).build());
        postRepository.save(Post.builder().title("d").content("c").user(user).build());
        em.flush();
        LocalDateTime now = LocalDateTime.now();
        postRepository.softDelete(old1.getId(), now.minusDays(31));
        postRepository.softDelete(old2.getId(), now.minusDays(40));
        postRepository.softDelete(recent.getId(), now.minusDays(1));
        em.clear();

        LocalDateTime threshold = now.minusDays(30);
        assertThat(postRepository.findIdsDeletedBefore(threshold, 0L, PageRequest.of(0, 10)))
                .containsExactly(old1.getId(), old2.getId());
        assertThat(postRepository.findIdsDeletedBefore(threshold, old1.getId(), PageRequest.of(0, 10)))
                .containsExactly(old2.getId());
    }

    @Test
    @DisplayName("숨긴 글은 목록·검색·인기글·공지·sitemap·최근 글·관련 글·조회수에서 빠지고, 숨김을 풀면 돌아온다")
    void blindedPosts_areExcludedEverywhereAndUnblindable() {
        Post kept = postRepository.save(Post.builder().title("남는 공지").content("c").user(user).category(Category.NOTICE)
                .pinnedUntil(LocalDateTime.now().plusDays(1)).build());
        Post hidden = postRepository.save(Post.builder().title("숨긴 공지").content("c").user(user).category(Category.NOTICE)
                .pinnedUntil(LocalDateTime.now().plusDays(1)).build());
        em.flush();

        assertThat(postRepository.blind(hidden.getId(), LocalDateTime.now())).isEqualTo(1);
        // 이미 숨겨진 글은 다시 숨겨지지 않는다.
        assertThat(postRepository.blind(hidden.getId(), LocalDateTime.now())).isZero();
        em.clear();

        Sort idDesc = Sort.by(Sort.Direction.DESC, "id");
        Page<PostRowDto> page = postRepository.search(null, null, false, PageRequest.of(0, 10, idDesc));
        assertThat(page.getContent()).extracting(PostRowDto::id).containsExactly(kept.getId());
        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(postRepository.searchWithoutCount("숨긴", null, false, PageRequest.of(0, 10, idDesc))).isEmpty();
        assertThat(postRepository.findTopByViewCountDesc(LocalDateTime.now().minusDays(1), PageRequest.of(0, 10)))
                .extracting(PostRowDto::id).containsExactly(kept.getId());
        assertThat(postRepository.findPinned(LocalDateTime.now(), PageRequest.of(0, 10)))
                .extracting(PostRowDto::id).containsExactly(kept.getId());
        assertThat(postRepository.findSitemapRows(PageRequest.of(0, 10)))
                .extracting(PostRepository.SitemapRow::getId).containsExactly(kept.getId());
        assertThat(postRepository.findRelated(Category.NOTICE, kept.getId(), PageRequest.of(0, 10))).isEmpty();
        assertThat(postRepository.existsVisibleById(hidden.getId())).isFalse();
        assertThat(postRepository.increaseViewCount(hidden.getId())).isZero();
        // 관리자 상세는 숨긴 글도 읽는다.
        assertThat(postRepository.findByIdWithUser(hidden.getId()).orElseThrow().isBlinded()).isTrue();
        assertThat(postRepository.findAllByIdInWithUser(List.of(hidden.getId()))).hasSize(1);

        assertThat(postRepository.unblind(hidden.getId())).isEqualTo(1);
        assertThat(postRepository.unblind(hidden.getId())).isZero();
        em.clear();
        assertThat(postRepository.search(null, null, false, PageRequest.of(0, 10, idDesc)).getContent())
                .extracting(PostRowDto::id).containsExactly(hidden.getId(), kept.getId());
    }

    @Test
    @DisplayName("blind·unblind는 version과 updatedAt을 바꾸지 않고, 삭제된 글은 숨기지 않는다")
    void blind_doesNotTouchVersionOrUpdatedAt_andIgnoresDeletedPosts() {
        Post post = postRepository.save(Post.builder().title("제목").content("c").user(user).build());
        Post deleted = postRepository.save(Post.builder().title("삭제됨").content("c").user(user).build());
        em.flush();
        em.clear();
        Post before = postRepository.findById(post.getId()).orElseThrow();
        Long version = before.getVersion();
        LocalDateTime updatedAt = before.getUpdatedAt();
        em.clear();

        postRepository.blind(post.getId(), LocalDateTime.now());
        postRepository.unblind(post.getId());
        postRepository.softDelete(deleted.getId(), LocalDateTime.now());
        em.clear();

        Post after = postRepository.findById(post.getId()).orElseThrow();
        assertThat(after.getVersion()).isEqualTo(version);
        assertThat(after.getUpdatedAt()).isEqualTo(updatedAt);
        // 삭제된 글은 숨김 대상이 아니다 — 삭제가 이미 더 강한 상태다.
        assertThat(postRepository.blind(deleted.getId(), LocalDateTime.now())).isZero();
    }

    @Test
    @DisplayName("findPinned: 기한이 남은 글만 분류와 무관하게 최신순으로, 최대 limit개 돌려준다")
    void findPinned_returnsOnlyActivePinsNewestFirst() {
        LocalDateTime now = LocalDateTime.now();
        Post expired = postRepository.save(Post.builder().title("기한 지남").content("c").user(user)
                .pinnedUntil(now.minusMinutes(1)).build());
        Post freePinned = postRepository.save(Post.builder().title("자유 고정").content("c").user(user)
                .category(Category.FREE).pinnedUntil(now.plusDays(1)).build());
        // 공지라고 자동으로 고정되지 않는다 — 관리자가 고른 글만 고정된다.
        postRepository.save(Post.builder().title("고정 안 된 공지").content("c").user(user)
                .category(Category.NOTICE).build());
        Post noticePinned = postRepository.save(Post.builder().title("공지 고정").content("c").user(user)
                .category(Category.NOTICE).pinnedUntil(now.plusDays(2)).build());
        em.flush();
        em.clear();

        assertThat(postRepository.findPinned(now, PageRequest.of(0, 10)))
                .extracting(PostRowDto::id).containsExactly(noticePinned.getId(), freePinned.getId());
        assertThat(postRepository.findPinned(now, PageRequest.of(0, 1)))
                .extracting(PostRowDto::id).containsExactly(noticePinned.getId());
        assertThat(expired.getId()).isNotNull();
    }

    @Test
    @DisplayName("pin·unpin: 보이는 글만 고정하고 version·updatedAt은 그대로 두며, 숨기거나 삭제한 글도 고정은 풀 수 있다")
    void pinAndUnpin_touchOnlyPinnedUntil() {
        Post post = postRepository.save(Post.builder().title("제목").content("c").user(user).build());
        Post deleted = postRepository.save(Post.builder().title("삭제됨").content("c").user(user).build());
        Post hidden = postRepository.save(Post.builder().title("숨김").content("c").user(user)
                .pinnedUntil(LocalDateTime.now().plusDays(1)).build());
        em.flush();
        em.clear();
        Post before = postRepository.findById(post.getId()).orElseThrow();
        Long version = before.getVersion();
        LocalDateTime updatedAt = before.getUpdatedAt();
        em.clear();

        // DB가 마이크로초로 저장하므로 비교가 어긋나지 않게 초 단위로 맞춘다.
        LocalDateTime until = LocalDateTime.now().plusDays(3).withNano(0);
        assertThat(postRepository.pin(post.getId(), until)).isEqualTo(1);
        postRepository.softDelete(deleted.getId(), LocalDateTime.now());
        postRepository.blind(hidden.getId(), LocalDateTime.now());
        // 삭제·숨긴 글은 고정할 수 없다.
        assertThat(postRepository.pin(deleted.getId(), until)).isZero();
        assertThat(postRepository.pin(hidden.getId(), until)).isZero();
        em.clear();

        Post pinned = postRepository.findById(post.getId()).orElseThrow();
        assertThat(pinned.getPinnedUntil()).isEqualTo(until);
        assertThat(pinned.getVersion()).isEqualTo(version);
        assertThat(pinned.getUpdatedAt()).isEqualTo(updatedAt);

        assertThat(postRepository.unpin(post.getId())).isEqualTo(1);
        assertThat(postRepository.unpin(post.getId())).isZero();
        // 숨긴 글의 고정도 풀 수 있다.
        assertThat(postRepository.unpin(hidden.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("countPinnedExcluding: 보이는 글 중 기한이 남은 고정만 세고, 기한을 바꾸려는 글 자신은 뺀다")
    void countPinnedExcluding_countsOnlyVisibleActivePins() {
        LocalDateTime now = LocalDateTime.now();
        Post a = postRepository.save(Post.builder().title("a").content("c").user(user).pinnedUntil(now.plusDays(1)).build());
        postRepository.save(Post.builder().title("b").content("c").user(user).pinnedUntil(now.plusDays(1)).build());
        postRepository.save(Post.builder().title("만료").content("c").user(user).pinnedUntil(now.minusDays(1)).build());
        Post deleted = postRepository.save(Post.builder().title("삭제").content("c").user(user)
                .pinnedUntil(now.plusDays(1)).build());
        em.flush();
        postRepository.softDelete(deleted.getId(), now);
        em.clear();

        // 삭제된 글과 만료된 글은 세지 않는다.
        assertThat(postRepository.countPinnedExcluding(-1L, now)).isEqualTo(2);
        assertThat(postRepository.countPinnedExcluding(a.getId(), now)).isEqualTo(1);
    }
}
