package com.kraft.post.domain;

import com.kraft.config.JpaConfig;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link PostRepository} 통합 테스트. {@code @DataJpaTest}는 Entity/Repository 관련
 * 빈만 스캔하므로, {@code @EnableJpaAuditing}이 선언된 {@link JpaConfig}는 기본적으로
 * 포함되지 않는다 — {@code @Import}로 명시해 감사 필드({@code createdAt}/{@code updatedAt})까지
 * 실제로 채워지는지 검증한다(P1-2 회귀 방지).
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

    @Test
    @DisplayName("search: 검색어·분류가 없으면 ID 내림차순으로, JOIN FETCH로 작성자가 함께 조회된다")
    void search_withoutFilters_returnsPostsDescWithAuthorJoinFetched() {
        Post first = postRepository.save(Post.builder().title("첫 글").content("c1").user(user).build());
        Post second = postRepository.save(Post.builder().title("둘째 글").content("c2").user(user).build());
        em.flush();
        em.clear();

        Page<Post> page = postRepository.search(null, null, PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(Post::getId)
                .containsExactly(second.getId(), first.getId());
        assertThat(page.getTotalElements()).isEqualTo(2);
        // em.clear() 이후이므로 LAZY 프록시라면 여기서 LazyInitializationException이 나야 정상이지만,
        // JOIN FETCH로 이미 초기화되어 있어 예외 없이 접근 가능해야 한다.
        assertThat(page.getContent().get(0).getUser().getName()).isEqualTo("tester");
    }

    @Test
    @DisplayName("search: size만큼 잘라서 반환하고 totalPages를 정확히 계산한다")
    void search_paginatesAndCalculatesTotalPagesCorrectly() {
        for (int i = 1; i <= 15; i++) {
            postRepository.save(Post.builder().title("글 " + i).content("내용 " + i).user(user).build());
        }
        em.flush();
        em.clear();

        Page<Post> firstPage = postRepository.search(null, null, PageRequest.of(0, 10));
        Page<Post> secondPage = postRepository.search(null, null, PageRequest.of(1, 10));

        assertThat(firstPage.getContent()).hasSize(10);
        assertThat(firstPage.getTotalElements()).isEqualTo(15);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
        assertThat(firstPage.isFirst()).isTrue();
        assertThat(firstPage.isLast()).isFalse();

        assertThat(secondPage.getContent()).hasSize(5);
        assertThat(secondPage.isLast()).isTrue();
    }

    @Test
    @DisplayName("search: 키워드가 제목이나 본문에 포함되면(대소문자 무시) 매치한다")
    void search_matchesWhenKeywordInTitleOrContent() {
        Post titleMatch = postRepository.save(Post.builder().title("Kraft 소개").content("내용").user(user).build());
        Post contentMatch = postRepository.save(Post.builder().title("공지").content("KRAFT 업데이트 안내").user(user).build());
        postRepository.save(Post.builder().title("관련 없음").content("다른 내용").user(user).build());
        em.flush();
        em.clear();

        Page<Post> page = postRepository.search("kraft", null, PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(Post::getId)
                .containsExactlyInAnyOrder(titleMatch.getId(), contentMatch.getId());
    }

    @Test
    @DisplayName("search: category로 좁히면 해당 분류의 글만 반환한다")
    void search_filtersByCategory() {
        Post notice = postRepository.save(Post.builder().title("공지").content("c").user(user).category(Category.NOTICE).build());
        postRepository.save(Post.builder().title("자유글").content("c").user(user).category(Category.FREE).build());
        em.flush();
        em.clear();

        Page<Post> page = postRepository.search(null, Category.NOTICE, PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(Post::getId).containsExactly(notice.getId());
    }

    @Test
    @DisplayName("findTopByViewCountDesc: 조회수 내림차순으로 상위 N개를 반환한다")
    void findTopByViewCountDesc_returnsTopPostsOrderedByViewCountDesc() {
        Post low = postRepository.save(Post.builder().title("낮음").content("c").user(user).build());
        Post high = postRepository.save(Post.builder().title("높음").content("c").user(user).build());
        Post mid = postRepository.save(Post.builder().title("중간").content("c").user(user).build());
        high.increaseViewCount();
        high.increaseViewCount();
        mid.increaseViewCount();
        em.flush();
        em.clear();

        var top2 = postRepository.findTopByViewCountDesc(PageRequest.of(0, 2));

        assertThat(top2).extracting(Post::getId).containsExactly(high.getId(), mid.getId());
        assertThat(low.getViewCount()).isZero();
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
    void update_updatesUpdatedAtAfterFlush() throws InterruptedException {
        Post saved = postRepository.save(Post.builder().title("t").content("c").user(user).build());
        em.flush();
        var createdUpdatedAt = saved.getUpdatedAt();

        Thread.sleep(5); // 타임스탬프 해상도 차이를 확실히 만들기 위한 최소 대기
        saved.update("수정된 제목", "수정된 내용", null, null);
        em.flush();

        assertThat(saved.getUpdatedAt()).isAfter(createdUpdatedAt);
    }
}
