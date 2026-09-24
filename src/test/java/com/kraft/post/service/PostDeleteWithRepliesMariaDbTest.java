package com.kraft.post.service;

import com.kraft.comment.domain.Comment;
import com.kraft.comment.domain.CommentRepository;
import com.kraft.comment.service.CommentService;
import com.kraft.post.domain.Post;
import com.kraft.post.domain.PostLikeRepository;
import com.kraft.post.domain.PostRepository;
import com.kraft.report.domain.Report;
import com.kraft.report.domain.ReportRepository;
import com.kraft.report.domain.ReportReason;
import com.kraft.report.domain.ReportTargetType;
import com.kraft.report.service.ReportService;
import com.kraft.user.domain.Role;
import com.kraft.user.domain.User;
import com.kraft.user.domain.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.testcontainers.mariadb.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 개선 보고서 COR-01(답글이 있는 게시글 삭제와 자기참조 FK)의 회귀 테스트. H2는
 * {@code FK_COMMENTS_PARENT} 위반을 문장 끝에서만 검사해 삭제 순서 문제를 재현하지 못하므로
 * (CommentRepositoryTest), 실제 InnoDB로 답글이 달린 게시글을 두 경로(작성자 삭제, 관리자의
 * 신고 처리)로 지워 FK 위반이 나지 않는지 확인한다. Docker가 없으면 건너뛴다.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        // 운영(prod)과 같은 스키마 경로로 실행한다(OPS-C1) — 이전에는 속성을 지정하지 않아
        // test 프로파일 기본값(Flyway off, ddl-auto=create-drop)을 그대로 썼다. 이 테스트가
        // 잡으려는 FK 문제는 실제 마이그레이션이 만든 스키마(V19 등)에서만 의미가 있다.
        "spring.flyway.enabled=true",
        "spring.flyway.baseline-on-migrate=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.session.jdbc.initialize-schema=never"
})
class PostDeleteWithRepliesMariaDbTest {

    @Container
    @ServiceConnection
    static MariaDBContainer mariadb = new MariaDBContainer("mariadb:11.7.2");

    @Autowired
    private PostService postService;

    @Autowired
    private ReportService reportService;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private PostLikeRepository postLikeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ReportRepository reportRepository;

    private User author;
    private Authentication authorAuth;
    private Authentication adminAuth;
    private Long commenterId;

    @BeforeEach
    void setUp() {
        reportRepository.deleteAll();
        postLikeRepository.deleteAll();
        commentRepository.deleteAll();
        postRepository.deleteAll();
        userRepository.deleteAll();

        author = userRepository.save(User.builder()
                .name("reply-author").email("reply-author@example.com").password("encoded").role(Role.USER).build());
        User admin = userRepository.save(User.builder()
                .name("reply-admin").email("reply-admin@example.com").password("encoded").role(Role.ADMIN).build());
        User commenter = userRepository.save(User.builder()
                .name("reply-commenter").email("reply-commenter@example.com").password("encoded").role(Role.USER)
                .build());
        authorAuth = new UsernamePasswordAuthenticationToken(author.getEmail(), null,
                List.of(new SimpleGrantedAuthority(Role.USER.getKey())));
        adminAuth = new UsernamePasswordAuthenticationToken(admin.getEmail(), null,
                List.of(new SimpleGrantedAuthority(Role.ADMIN.getKey())));
        this.commenterId = commenter.getId();
    }

    private Post seedPostWithParentsAndReplies() {
        Post post = postRepository.save(Post.builder().title("제목").content("내용").user(author).build());
        User commenter = userRepository.findById(commenterId).orElseThrow();
        for (int i = 0; i < 3; i++) {
            Comment parent = commentRepository.save(
                    Comment.builder().content("부모" + i).post(post).user(commenter).build());
            for (int j = 0; j < 2; j++) {
                commentRepository.save(
                        Comment.builder().content("답글" + i + "-" + j).post(post).user(commenter).parent(parent)
                                .build());
            }
        }
        return post;
    }

    @Test
    @DisplayName("COR-01 회귀: 작성자가 답글이 여러 개 달린 게시글을 삭제해도 FK 위반이 나지 않는다")
    void authorDelete_withParentsAndReplies_succeedsWithoutForeignKeyViolation() {
        Post post = seedPostWithParentsAndReplies();
        Long postId = post.getId();

        assertThatCode(() -> postService.delete(postId, authorAuth)).doesNotThrowAnyException();

        assertThat(postRepository.findById(postId)).isEmpty();
        assertThat(commentRepository.countByPostId(postId)).isZero();
    }

    @Test
    @DisplayName("COR-01 회귀: 신고 처리로 답글이 달린 게시글을 지워도 FK 위반이 나지 않는다")
    void reportResolve_withParentsAndReplies_succeedsWithoutForeignKeyViolation() {
        Post post = seedPostWithParentsAndReplies();
        Long postId = post.getId();
        User commenter = userRepository.findById(commenterId).orElseThrow();
        Report report = reportRepository.save(Report.builder()
                .reporter(commenter)
                .targetType(ReportTargetType.POST)
                .targetId(postId)
                .reason(ReportReason.SPAM)
                .detail("스팸")
                .build());

        assertThatCode(() -> reportService.resolve(report.getId(), adminAuth)).doesNotThrowAnyException();

        assertThat(postRepository.findById(postId)).isEmpty();
        assertThat(commentRepository.countByPostId(postId)).isZero();
    }
}
