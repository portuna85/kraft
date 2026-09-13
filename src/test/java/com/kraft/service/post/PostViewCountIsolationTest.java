package com.kraft.service.post;

import com.kraft.domain.comment.CommentRepository;
import com.kraft.domain.post.Category;
import com.kraft.domain.post.Post;
import com.kraft.domain.post.PostImageRepository;
import com.kraft.domain.post.PostLikeRepository;
import com.kraft.domain.post.PostRepository;
import com.kraft.domain.user.Role;
import com.kraft.domain.user.User;
import com.kraft.domain.user.UserRepository;
import com.kraft.web.dto.post.PostUpdateRequestDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 조회수 갱신이 게시글 본문·최종수정일을 오염시키지 않는지 실제 DB로 검증한다
 * (개선 보고서 F02·F11)와, 편집 충돌이 감지되는지 확인한다.
 * <p>
 * 예전에는 상세 조회가 엔티티를 읽어 {@code increaseViewCount()}로 필드를 바꾸고 변경 감지에
 * 맡겼다. Hibernate는 그 UPDATE에 제목·본문·분류를 함께 실었기 때문에, 조회 트랜잭션이 읽어둔
 * 옛 값이 그 사이 커밋된 편집 내용을 덮어썼다. 감사 필드 {@code updatedAt}도 열람만으로 바뀌어
 * 목록의 "최종수정일"이 실제 수정 시각과 달라졌다.
 */
@SpringBootTest
class PostViewCountIsolationTest {

    @Autowired
    private PostService postService;

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
    private TransactionTemplate transactionTemplate;

    /**
     * 겹친 트랜잭션을 만들기 위한 별도 템플릿. 기본 전파(REQUIRED)로는 바깥 트랜잭션에
     * 참여해 같은 영속성 컨텍스트를 쓰므로 "다른 트랜잭션이 먼저 저장했다"를 재현할 수 없다.
     */
    private TransactionTemplate requiresNew;

    private Authentication owner;

    @BeforeEach
    void setUp() {
        requiresNew = new TransactionTemplate(transactionTemplate.getTransactionManager());
        requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        postImageRepository.deleteAll();
        commentRepository.deleteAll();
        postLikeRepository.deleteAll();
        postRepository.deleteAll();
        userRepository.deleteAll();

        userRepository.save(User.builder()
                .name("owner").email("owner@example.com").password("encoded").role(Role.USER).build());
        owner = new UsernamePasswordAuthenticationToken("owner@example.com", null,
                List.of(new SimpleGrantedAuthority(Role.USER.getKey())));
    }

    @Test
    @DisplayName("F02: 겹친 열람·편집에서 편집 내용이 보존되고 조회수도 함께 올라간다")
    void viewAndEditOverlap_keepsEditedContentAndCountsView() {
        Long id = savePost("원래 제목", "원래 내용", Category.FREE);

        // 열람 트랜잭션이 게시글을 먼저 읽어 옛 값을 손에 쥔 상태를 만든다.
        transactionTemplate.executeWithoutResult(status -> {
            Post stale = postRepository.findById(id).orElseThrow();
            assertThat(stale.getTitle()).isEqualTo("원래 제목");

            // 그 사이 별도 트랜잭션이 제목·본문·분류를 저장하고 커밋한다.
            requiresNew.executeWithoutResult(inner ->
                    postService.update(id, new PostUpdateRequestDto("새 제목", "새 내용", null, Category.QNA, null), owner));

            // 이제 열람이 조회수를 올린다. 예전에는 이 시점에 stale의 제목·본문·분류가 함께
            // UPDATE에 실려 방금 저장한 내용을 되돌렸다.
            postService.findByIdForView(id, owner);
        });

        Post result = postRepository.findById(id).orElseThrow();
        assertThat(result.getTitle()).isEqualTo("새 제목");
        assertThat(result.getContent()).isEqualTo("새 내용");
        assertThat(result.getCategory()).isEqualTo(Category.QNA);
        assertThat(result.getViewCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("F11: 상세를 여러 번 열어도 최종수정일은 그대로다")
    void view_doesNotTouchUpdatedAt() {
        Long id = savePost("제목", "내용", Category.FREE);
        LocalDateTime before = postRepository.findById(id).orElseThrow().getUpdatedAt();

        postService.findByIdForView(id, owner);
        postService.findByIdForView(id, owner);
        postService.findByIdForView(id, owner);

        Post result = postRepository.findById(id).orElseThrow();
        assertThat(result.getUpdatedAt()).isEqualTo(before);
        assertThat(result.getViewCount()).isEqualTo(3L);
    }

    @Test
    @DisplayName("조회수는 열람할 때마다 하나씩 정확히 올라간다")
    void view_increasesCountExactlyOncePerView() {
        Long id = savePost("제목", "내용", Category.FREE);

        for (int i = 0; i < 5; i++) {
            postService.findByIdForView(id, owner);
        }

        assertThat(postRepository.findById(id).orElseThrow().getViewCount()).isEqualTo(5L);
    }

    @Test
    @DisplayName("F02: 편집을 시작한 뒤 다른 곳에서 저장되면 충돌로 거절한다")
    void update_withStaleVersion_isRejected() {
        Long id = savePost("제목", "내용", Category.FREE);
        Long versionAtEditStart = postService.findByIdForView(id, owner).version();

        postService.update(id, new PostUpdateRequestDto("먼저 저장", "내용", null, null, versionAtEditStart), owner);

        assertThatThrownBy(() -> postService.update(id,
                new PostUpdateRequestDto("나중 저장", "내용", null, null, versionAtEditStart), owner))
                .isInstanceOf(OptimisticLockingFailureException.class);

        assertThat(postRepository.findById(id).orElseThrow().getTitle()).isEqualTo("먼저 저장");
    }

    @Test
    @DisplayName("열람으로 조회수만 올라간 경우에는 편집 충돌로 보지 않는다")
    void update_afterViewsOnly_stillSucceeds() {
        Long id = savePost("제목", "내용", Category.FREE);
        Long version = postService.findByIdForView(id, owner).version();
        postService.findByIdForView(id, owner);

        postService.update(id, new PostUpdateRequestDto("수정됨", "내용", null, null, version), owner);

        assertThat(postRepository.findById(id).orElseThrow().getTitle()).isEqualTo("수정됨");
    }

    private Long savePost(String title, String content, Category category) {
        User user = userRepository.findAll().get(0);
        return postRepository.save(Post.builder()
                .title(title).content(content).user(user).category(category).build()).getId();
    }
}
