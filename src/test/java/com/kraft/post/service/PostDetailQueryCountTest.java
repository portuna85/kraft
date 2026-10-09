package com.kraft.post.service;

import com.kraft.comment.domain.CommentRepository;
import com.kraft.post.domain.Category;
import com.kraft.post.domain.Post;
import com.kraft.post.domain.PostImageRepository;
import com.kraft.post.domain.PostLike;
import com.kraft.post.domain.PostLikeRepository;
import com.kraft.post.domain.PostRepository;
import com.kraft.support.TestAuthentication;
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
import org.springframework.security.core.Authentication;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 게시글 상세 조회({@link PostQueryService#findByIdForView})가 SQL을 몇 번 내는지 고정한다.
 * <p>
 * 예전에는 조회수 UPDATE, 게시글, 작성자(지연 로딩), 추천 exists, 추천 count가 따로 나갔다.
 * 지금은 조회수 UPDATE, 게시글+작성자(JOIN FETCH), 추천 집계 — 3개다. 누가 이 경로에 쿼리를
 * 다시 끼워 넣으면 여기서 숫자로 드러난다. 홈의 최근 글은 COUNT 없이 1개(+댓글 수 1개)다.
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class PostDetailQueryCountTest {

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
    private User author;
    private User viewer;
    private Long postId;

    @BeforeEach
    void setUp() {
        postImageRepository.deleteAll();
        commentRepository.deleteAll();
        postLikeRepository.deleteAll();
        postRepository.deleteAll();
        userRepository.deleteAll();

        author = userRepository.save(User.builder()
                .name("author").email("author@example.com").password("encoded").role(Role.USER).build());
        viewer = userRepository.save(User.builder()
                .name("viewer").email("viewer@example.com").password("encoded").role(Role.USER).build());
        Post post = postRepository.save(Post.builder()
                .title("제목").content("본문").user(author).category(Category.FREE).build());
        postId = post.getId();
        postLikeRepository.save(PostLike.builder().post(post).user(viewer).build());
        postLikeRepository.save(PostLike.builder().post(post).user(author).build());

        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    @Test
    @DisplayName("로그인 사용자의 상세 조회는 SQL 3개(조회수 UPDATE, 게시글+작성자, 추천 집계)다")
    void detailForLoggedInUser_usesThreeStatements() {
        Authentication auth = TestAuthentication.of(viewer);
        statistics.clear();

        var view = postQueryService.findByIdForView(postId, auth);

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(3L);
        assertThat(view.likeCount()).isEqualTo(2L);
        assertThat(view.likedByMe()).isTrue();
        assertThat(view.author()).isEqualTo("author");
        assertThat(view.viewCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("익명 상세 조회도 SQL 3개이고 likedByMe는 false다")
    void detailForAnonymous_usesThreeStatements() {
        statistics.clear();

        var view = postQueryService.findByIdForView(postId, null);

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(3L);
        assertThat(view.likeCount()).isEqualTo(2L);
        assertThat(view.likedByMe()).isFalse();
    }

    @Test
    @DisplayName("내가 누르지 않은 글이면 likedByMe는 false다")
    void likedByMe_isFalseWhenNotLikedByViewer() {
        User stranger = userRepository.save(User.builder()
                .name("stranger").email("stranger@example.com").password("encoded").role(Role.USER).build());

        var view = postQueryService.findByIdForView(postId, TestAuthentication.of(stranger));

        assertThat(view.likeCount()).isEqualTo(2L);
        assertThat(view.likedByMe()).isFalse();
    }
}
