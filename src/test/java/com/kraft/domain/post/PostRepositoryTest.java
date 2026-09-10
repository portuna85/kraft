package com.kraft.domain.post;

import com.kraft.config.JpaConfig;
import com.kraft.domain.user.Role;
import com.kraft.domain.user.User;
import com.kraft.domain.user.UserRepository;
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
@Import(JpaConfig.class)
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
    @DisplayName("findAllDesc: ID 내림차순으로 정렬되고, JOIN FETCH로 작성자가 함께 조회된다")
    void findAllDesc_는_ID_내림차순으로_JOIN_FETCH하여_조회한다() {
        Post first = postRepository.save(Post.builder().title("첫 글").content("c1").user(user).build());
        Post second = postRepository.save(Post.builder().title("둘째 글").content("c2").user(user).build());
        em.flush();
        em.clear();

        Page<Post> page = postRepository.findAllDesc(PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(Post::getId)
                .containsExactly(second.getId(), first.getId());
        assertThat(page.getTotalElements()).isEqualTo(2);
        // em.clear() 이후이므로 LAZY 프록시라면 여기서 LazyInitializationException이 나야 정상이지만,
        // JOIN FETCH로 이미 초기화되어 있어 예외 없이 접근 가능해야 한다.
        assertThat(page.getContent().get(0).getUser().getName()).isEqualTo("tester");
    }

    @Test
    @DisplayName("findAllDesc: size만큼 잘라서 반환하고 totalPages를 정확히 계산한다")
    void findAllDesc_는_페이지_단위로_잘라서_반환한다() {
        for (int i = 1; i <= 15; i++) {
            postRepository.save(Post.builder().title("글 " + i).content("내용 " + i).user(user).build());
        }
        em.flush();
        em.clear();

        Page<Post> firstPage = postRepository.findAllDesc(PageRequest.of(0, 10));
        Page<Post> secondPage = postRepository.findAllDesc(PageRequest.of(1, 10));

        assertThat(firstPage.getContent()).hasSize(10);
        assertThat(firstPage.getTotalElements()).isEqualTo(15);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
        assertThat(firstPage.isFirst()).isTrue();
        assertThat(firstPage.isLast()).isFalse();

        assertThat(secondPage.getContent()).hasSize(5);
        assertThat(secondPage.isLast()).isTrue();
    }

    @Test
    @DisplayName("저장하면 BaseEntity의 createdAt/updatedAt이 자동으로 채워진다 (JpaConfig의 @EnableJpaAuditing)")
    void 감사필드가_자동으로_채워진다() {
        Post saved = postRepository.save(Post.builder().title("t").content("c").user(user).build());
        em.flush();

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("update() 이후 flush하면 updatedAt이 최초 저장 시점보다 뒤로 갱신된다")
    void 수정하면_updatedAt이_갱신된다() throws InterruptedException {
        Post saved = postRepository.save(Post.builder().title("t").content("c").user(user).build());
        em.flush();
        var createdUpdatedAt = saved.getUpdatedAt();

        Thread.sleep(5); // 타임스탬프 해상도 차이를 확실히 만들기 위한 최소 대기
        saved.update("수정된 제목", "수정된 내용");
        em.flush();

        assertThat(saved.getUpdatedAt()).isAfter(createdUpdatedAt);
    }
}
