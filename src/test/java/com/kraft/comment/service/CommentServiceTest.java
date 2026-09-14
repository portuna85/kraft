package com.kraft.comment.service;

import com.kraft.comment.domain.Comment;
import com.kraft.comment.domain.CommentRepository;
import com.kraft.comment.dto.CommentResponseDto;
import com.kraft.comment.dto.CommentSaveRequestDto;
import com.kraft.comment.dto.CommentUpdateRequestDto;
import com.kraft.post.domain.Post;
import com.kraft.post.domain.PostRepository;
import com.kraft.user.domain.EmailHasher;
import com.kraft.user.domain.Role;
import com.kraft.user.domain.User;
import com.kraft.user.domain.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * {@link CommentService} 단위 테스트. {@link PostService}와 동일한 방식으로 Mockito만
 * 사용해 {@link PostRepository}, {@link UserRepository}, {@link CommentRepository}를 모킹한다.
 */
@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private PostRepository postRepository;

    @Mock
    private UserRepository userRepository;

    private CommentService commentService;

    @BeforeEach
    void setUp() {
        commentService = new CommentService(commentRepository, postRepository, userRepository);
    }

    private static User userWithEmail(String email, Long id) {
        User user = User.builder().name("tester").email(email).password("encoded").role(Role.USER).build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private static Post postOf(Long id) {
        Post post = Post.builder().title("게시글").content("내용").user(null).build();
        ReflectionTestUtils.setField(post, "id", id);
        return post;
    }

    private static Comment commentOf(User owner, Long id) {
        Comment comment = Comment.builder().content("원래 댓글").post(postOf(1L)).user(owner).build();
        ReflectionTestUtils.setField(comment, "id", id);
        return comment;
    }

    private static Authentication authOf(String email, Role role) {
        return new UsernamePasswordAuthenticationToken(
                email, null, List.of(new SimpleGrantedAuthority(role.getKey())));
    }

    @Test
    @DisplayName("save: 게시글과 회원이 모두 존재하면 댓글을 저장하고 ID를 반환한다")
    void save_whenPostAndUserExist_savesCommentAndReturnsId() {
        Post post = postOf(1L);
        User user = userWithEmail("tester@example.com", 1L);
        given(postRepository.findById(1L)).willReturn(Optional.of(post));
        given(userRepository.findByEmailHash(EmailHasher.sha512Hex("tester@example.com"))).willReturn(Optional.of(user));
        Comment saved = commentOf(user, 100L);
        given(commentRepository.save(any(Comment.class))).willReturn(saved);

        Long id = commentService.save(1L, "tester@example.com", new CommentSaveRequestDto("댓글 내용"));

        assertThat(id).isEqualTo(100L);
    }

    @Test
    @DisplayName("save: 게시글이 없으면 IllegalArgumentException")
    void save_whenPostNotFound_throwsIllegalArgumentException() {
        given(postRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.save(999L, "tester@example.com", new CommentSaveRequestDto("내용")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("해당 게시글이 없습니다");

        verify(commentRepository, never()).save(any());
    }

    @Test
    @DisplayName("save: 이메일 인증 전(GUEST) 회원이면 AccessDeniedException이고 저장되지 않는다")
    void save_whenUserIsGuest_throwsAccessDeniedExceptionAndDoesNotSave() {
        User guest = User.builder().name("tester").email("guest@example.com").password("encoded").role(Role.GUEST).build();
        ReflectionTestUtils.setField(guest, "id", 1L);
        given(postRepository.findById(1L)).willReturn(Optional.of(postOf(1L)));
        given(userRepository.findByEmailHash(EmailHasher.sha512Hex("guest@example.com"))).willReturn(Optional.of(guest));

        assertThatThrownBy(() -> commentService.save(1L, "guest@example.com", new CommentSaveRequestDto("내용")))
                .isInstanceOf(AccessDeniedException.class);

        verify(commentRepository, never()).save(any());
    }

    @Test
    @DisplayName("save: 회원이 없으면 IllegalArgumentException")
    void save_whenUserNotFound_throwsIllegalArgumentException() {
        given(postRepository.findById(1L)).willReturn(Optional.of(postOf(1L)));
        given(userRepository.findByEmailHash(EmailHasher.sha512Hex("nobody@example.com"))).willReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.save(1L, "nobody@example.com", new CommentSaveRequestDto("내용")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("존재하지 않는 회원");

        verify(commentRepository, never()).save(any());
    }

    @Test
    @DisplayName("update: 작성자 본인이면 내용이 변경된다")
    void update_whenAuthor_updatesContent() {
        User owner = userWithEmail("owner@example.com", 1L);
        Comment comment = commentOf(owner, 100L);
        given(commentRepository.findById(100L)).willReturn(Optional.of(comment));

        Long id = commentService.update(100L, new CommentUpdateRequestDto("수정된 댓글"),
                authOf("owner@example.com", Role.USER));

        assertThat(id).isEqualTo(100L);
        assertThat(comment.getContent()).isEqualTo("수정된 댓글");
    }

    @Test
    @DisplayName("update: 작성자가 아니면 AccessDeniedException이고 내용은 변경되지 않는다")
    void update_whenNotAuthor_throwsAccessDeniedExceptionAndDoesNotModify() {
        User owner = userWithEmail("owner@example.com", 1L);
        Comment comment = commentOf(owner, 100L);
        given(commentRepository.findById(100L)).willReturn(Optional.of(comment));

        assertThatThrownBy(() -> commentService.update(100L, new CommentUpdateRequestDto("해킹"),
                authOf("intruder@example.com", Role.USER)))
                .isInstanceOf(AccessDeniedException.class);

        assertThat(comment.getContent()).isEqualTo("원래 댓글");
    }

    @Test
    @DisplayName("update: ROLE_ADMIN이면 작성자가 아니어도 수정할 수 있다")
    void update_whenAdmin_updatesContentEvenIfNotAuthor() {
        User owner = userWithEmail("owner@example.com", 1L);
        Comment comment = commentOf(owner, 100L);
        given(commentRepository.findById(100L)).willReturn(Optional.of(comment));

        commentService.update(100L, new CommentUpdateRequestDto("관리자 수정"),
                authOf("admin@example.com", Role.ADMIN));

        assertThat(comment.getContent()).isEqualTo("관리자 수정");
    }

    @Test
    @DisplayName("delete: 작성자가 아니면 AccessDeniedException이고 delete가 호출되지 않는다")
    void delete_whenNotAuthor_throwsAccessDeniedExceptionAndDoesNotDelete() {
        User owner = userWithEmail("owner@example.com", 1L);
        Comment comment = commentOf(owner, 100L);
        given(commentRepository.findById(100L)).willReturn(Optional.of(comment));

        assertThatThrownBy(() -> commentService.delete(100L, authOf("intruder@example.com", Role.USER)))
                .isInstanceOf(AccessDeniedException.class);

        verify(commentRepository, never()).delete(any());
    }

    @Test
    @DisplayName("delete: 작성자 본인이면 정상적으로 삭제된다")
    void delete_whenAuthor_deletesSuccessfully() {
        User owner = userWithEmail("owner@example.com", 1L);
        Comment comment = commentOf(owner, 100L);
        given(commentRepository.findById(100L)).willReturn(Optional.of(comment));

        commentService.delete(100L, authOf("owner@example.com", Role.USER));

        verify(commentRepository).delete(comment);
    }

    @Test
    @DisplayName("findByPostId: Repository 결과를 CommentResponseDto 리스트로 변환한다")
    void findByPostId_convertsRepositoryResultToResponseDtoList() {
        User owner = userWithEmail("owner@example.com", 1L);
        Comment comment = commentOf(owner, 100L);
        given(commentRepository.findAllByPostIdAsc(1L)).willReturn(List.of(comment));

        List<CommentResponseDto> result = commentService.findByPostId(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).content()).isEqualTo("원래 댓글");
        assertThat(result.get(0).author()).isEqualTo("tester");
    }
}
