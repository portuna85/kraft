package com.kraft.post.service;

import com.kraft.comment.domain.CommentRepository;
import com.kraft.post.domain.Category;
import com.kraft.post.domain.Post;
import com.kraft.post.domain.PostImageRepository;
import com.kraft.post.domain.PostLikeRepository;
import com.kraft.post.domain.PostRepository;
import com.kraft.user.domain.Role;
import com.kraft.user.domain.User;
import com.kraft.user.domain.UserRepository;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 검색 목록이 SQL을 몇 번 내는지 고정한다.
 * <p>
 * 검색어가 있으면 {@code LIKE '%kw%'}가 인덱스를 못 타서 COUNT가 매칭 여부와 상관없이 항상
 * 테이블 전체를 읽는다. 그래서 검색은 COUNT를 세지 않고 결과 쿼리와 댓글 수 쿼리, 2문장이다
 * (예전에는 COUNT를 포함해 3문장). 검색어 없는 목록은 총 건수·번호 이동이 필요해 COUNT를
 * 그대로 센다 — 이 숫자가 바뀌면 누가 어느 쪽 경로를 건드렸는지 여기서 드러난다.
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class PostSearchQueryCountTest {

    @Autowired
    private PostQueryService postQueryService;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private PostLikeRepository postLikeRepository;

    @Autowired
    private PostImageRepository postImageRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private Statistics statistics;

    @BeforeEach
    void setUp() {
        postImageRepository.deleteAll();
        commentRepository.deleteAll();
        postLikeRepository.deleteAll();
        postRepository.deleteAll();
        userRepository.deleteAll();

        User author = userRepository.save(User.builder()
                .name("author").email("author@example.com").password("encoded").role(Role.USER).build());
        for (int i = 1; i <= 12; i++) {
            postRepository.save(Post.builder()
                    .title("검색 대상 " + i).content("본문").user(author).category(Category.FREE).build());
        }
        postRepository.save(Post.builder().title("다른 글").content("본문").user(author).category(Category.FREE).build());

        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    @Test
    @DisplayName("검색어가 있으면 COUNT 없이 SQL 2개(결과, 댓글 수)이고 다음 페이지 유무만 안다")
    void keywordSearch_runsWithoutCountQuery() {
        statistics.clear();

        var result = postQueryService.findAllDesc(PageRequest.of(0, 10), "검색 대상", null);

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2L);
        assertThat(result.content()).hasSize(10);
        assertThat(result.last()).as("12개 중 10개를 봤으므로 다음 페이지가 있다").isFalse();
        assertThat(result.totalElements()).isNull();
        assertThat(result.totalPages()).isNull();
    }

    @Test
    @DisplayName("검색 결과의 마지막 페이지는 last=true다")
    void keywordSearch_lastPageIsMarkedLast() {
        var result = postQueryService.findAllDesc(PageRequest.of(1, 10), "검색 대상", null);

        assertThat(result.content()).hasSize(2);
        assertThat(result.last()).isTrue();
        assertThat(result.first()).isFalse();
    }

    @Test
    @DisplayName("검색어 없는 전체 목록은 총 건수·번호 이동에 필요해 COUNT를 포함한 SQL 3개이고 전체 건수가 채워진다")
    void listWithoutKeyword_stillCountsTotal() {
        statistics.clear();

        var result = postQueryService.findAllDesc(PageRequest.of(0, 10));

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(3L);
        assertThat(result.totalElements()).isEqualTo(13L);
        assertThat(result.totalPages()).isEqualTo(2);
    }
}
