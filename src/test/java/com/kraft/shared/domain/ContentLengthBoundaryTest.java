package com.kraft.shared.domain;

import com.kraft.comment.domain.CommentRepository;
import com.kraft.comment.dto.CommentSaveRequestDto;
import com.kraft.comment.service.CommentService;
import com.kraft.post.domain.PostRepository;
import com.kraft.post.dto.PostSaveRequestDto;
import com.kraft.post.service.PostService;
import com.kraft.user.domain.Role;
import com.kraft.user.domain.User;
import com.kraft.user.domain.UserRepository;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 게시글·댓글 본문 길이 정책이 {@code TEXT} 컬럼의 실제 용량 안에 들어가는지 검증한다
 * (개선 보고서 F07의 본문 길이 항목).
 * <p>
 * 예전에는 본문에 최대 길이 검증이 아예 없었다. {@code TEXT}는 문자 수가 아니라 65,535
 * <b>바이트</b>를 담으므로 "몇 자까지 되는가"가 내용에 따라 달라졌고, 경계를 넘는 본문은 입력
 * 검증이 아니라 DB 저장 실패로 드러났다.
 * <p>
 * 최악의 경우는 이모지가 아니라 <b>한글</b>이다. {@code @Size}가 세는 단위는 자바
 * {@code char}(UTF-16 코드 단위)인데, 한글은 {@code char} 하나에 UTF-8 3바이트를 쓰는 반면
 * 이모지는 {@code char} 두 개(서러게이트 쌍)에 4바이트라 코드 단위당 2바이트뿐이다.
 * 그래서 아래 테스트는 정책 상한을 전부 한글로 채운다.
 */
@SpringBootTest
class ContentLengthBoundaryTest {

    /** UTF-16 코드 단위 하나가 UTF-8에서 가장 많은 바이트(3)를 쓰는 경우. */
    private static final String WORST_CASE_CHAR = "가";

    /** MariaDB TEXT의 용량. */
    private static final int TEXT_MAX_BYTES = 65_535;

    @Autowired
    private PostService postService;

    @Autowired
    private CommentService commentService;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private Validator validator;

    private Authentication author;

    @BeforeEach
    void setUp() {
        commentRepository.deleteAll();
        postRepository.deleteAll();
        userRepository.deleteAll();

        userRepository.save(User.builder()
                .name("author").email("author@example.com").password("encoded").role(Role.USER).build());
        author = new UsernamePasswordAuthenticationToken("author@example.com", null,
                List.of(new SimpleGrantedAuthority(Role.USER.getKey())));
    }

    @Test
    @DisplayName("최악의 경우(전부 한글)로 정책 상한을 채워도 TEXT 용량 안에 들어간다")
    void policyLimits_fitWithinTextCapacityAtWorstCase() {
        assertThat(WORST_CASE_CHAR).as("한글 한 자는 char 하나다").hasSize(1);
        assertThat(utf8BytesOf(WORST_CASE_CHAR)).as("한글 한 자의 UTF-8 바이트 수").isEqualTo(3);

        assertThat(utf8BytesOf(WORST_CASE_CHAR.repeat(ContentPolicy.POST_CONTENT_MAX_LENGTH)))
                .as("게시글 본문 최악의 경우 바이트 수")
                .isLessThanOrEqualTo(TEXT_MAX_BYTES);
        assertThat(utf8BytesOf(WORST_CASE_CHAR.repeat(ContentPolicy.COMMENT_CONTENT_MAX_LENGTH)))
                .as("댓글 본문 최악의 경우 바이트 수")
                .isLessThanOrEqualTo(TEXT_MAX_BYTES);
    }

    @Test
    @DisplayName("정책 상한 길이의 게시글 본문은 실제로 저장되고 그대로 읽힌다")
    void postContent_atPolicyLimit_isStoredAndReadBack() {
        String content = WORST_CASE_CHAR.repeat(ContentPolicy.POST_CONTENT_MAX_LENGTH);

        Long id = postService.save(author, new PostSaveRequestDto("제목", content, null, null));

        assertThat(postRepository.findById(id).orElseThrow().getContent()).isEqualTo(content);
    }

    @Test
    @DisplayName("정책 상한 길이의 댓글 본문은 실제로 저장되고 그대로 읽힌다")
    void commentContent_atPolicyLimit_isStoredAndReadBack() {
        Long postId = postService.save(author, new PostSaveRequestDto("제목", "내용", null, null));
        String content = WORST_CASE_CHAR.repeat(ContentPolicy.COMMENT_CONTENT_MAX_LENGTH);

        Long id = commentService.save(postId, author.getName(), new CommentSaveRequestDto(content));

        assertThat(commentRepository.findById(id).orElseThrow().getContent()).isEqualTo(content);
    }

    @Test
    @DisplayName("정책 상한을 넘는 본문은 입력 검증이 거부한다(DB까지 가지 않는다)")
    void contentOverPolicyLimit_isRejectedByValidation() {
        var tooLongPost = new PostSaveRequestDto(
                "제목", WORST_CASE_CHAR.repeat(ContentPolicy.POST_CONTENT_MAX_LENGTH + 1), null, null);
        var tooLongComment = new CommentSaveRequestDto(
                WORST_CASE_CHAR.repeat(ContentPolicy.COMMENT_CONTENT_MAX_LENGTH + 1));

        assertThat(validator.validate(tooLongPost))
                .extracting(v -> v.getMessage())
                .containsExactly("내용은 " + ContentPolicy.POST_CONTENT_MAX_LENGTH + "자 이하로 입력하세요.");
        assertThat(validator.validate(tooLongComment))
                .extracting(v -> v.getMessage())
                .containsExactly("댓글은 " + ContentPolicy.COMMENT_CONTENT_MAX_LENGTH + "자 이하로 입력하세요.");
    }

    @Test
    @DisplayName("정책 상한 이내의 본문은 입력 검증을 통과한다")
    void contentAtPolicyLimit_passesValidation() {
        var post = new PostSaveRequestDto(
                "제목", WORST_CASE_CHAR.repeat(ContentPolicy.POST_CONTENT_MAX_LENGTH), null, null);
        var comment = new CommentSaveRequestDto(
                WORST_CASE_CHAR.repeat(ContentPolicy.COMMENT_CONTENT_MAX_LENGTH));

        assertThat(validator.validate(post)).isEmpty();
        assertThat(validator.validate(comment)).isEmpty();
    }

    private static int utf8BytesOf(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }
}
