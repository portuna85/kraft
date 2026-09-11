package com.kraft.service.post;

import com.kraft.domain.comment.CommentRepository;
import com.kraft.domain.post.Category;
import com.kraft.domain.post.Post;
import com.kraft.domain.post.PostLikeRepository;
import com.kraft.domain.post.PostRepository;
import com.kraft.domain.user.EmailHasher;
import com.kraft.domain.user.Role;
import com.kraft.domain.user.User;
import com.kraft.domain.user.UserRepository;
import com.kraft.web.dto.post.PostSaveRequestDto;
import com.kraft.web.dto.post.PostUpdateRequestDto;
import com.kraft.web.dto.post.PostsListResponseDto;
import com.kraft.web.dto.post.PostsPageResponseDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * {@link PostService} 단위 테스트. Spring 컨텍스트 없이 Mockito로 {@link PostRepository},
 * {@link UserRepository}를 모킹해 빠르게 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private PostImageService postImageService;

    @Mock
    private PostLikeRepository postLikeRepository;

    private PostService postService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        postService = new PostService(postRepository, userRepository, commentRepository, postImageService, postLikeRepository);
    }

    private static User userWithEmail(String email, Long id) {
        User user = User.builder().name("tester").email(email).password("encoded").role(Role.USER).build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private static Post postOf(User owner, Long id) {
        Post post = Post.builder().title("원래 제목").content("원래 내용").user(owner).build();
        ReflectionTestUtils.setField(post, "id", id);
        return post;
    }

    private static Authentication authOf(String email, Role role) {
        return new UsernamePasswordAuthenticationToken(
                email, null, List.of(new SimpleGrantedAuthority(role.getKey())));
    }

    @Test
    @DisplayName("save: 존재하는 회원이면 작성자로 지정해 저장하고 ID를 반환한다")
    void save_존재하는_회원이면_저장한다() {
        User user = userWithEmail("tester@example.com", 1L);
        given(userRepository.findByEmailHash(EmailHasher.sha512Hex("tester@example.com"))).willReturn(Optional.of(user));
        Post saved = postOf(user, 10L);
        given(postRepository.save(any(Post.class))).willReturn(saved);

        Long id = postService.save("tester@example.com",
                new PostSaveRequestDto("제목", "내용", null, null));

        assertThat(id).isEqualTo(10L);
    }

    @Test
    @DisplayName("save: 이메일 인증 전(GUEST) 회원이면 AccessDeniedException이고 저장되지 않는다")
    void save_GUEST_회원이면_거부된다() {
        User guest = User.builder().name("tester").email("guest@example.com").password("encoded").role(Role.GUEST).build();
        ReflectionTestUtils.setField(guest, "id", 1L);
        given(userRepository.findByEmailHash(EmailHasher.sha512Hex("guest@example.com"))).willReturn(Optional.of(guest));

        assertThatThrownBy(() -> postService.save("guest@example.com", new PostSaveRequestDto("제목", "내용", null, null)))
                .isInstanceOf(AccessDeniedException.class);

        verify(postRepository, never()).save(any());
    }

    @Test
    @DisplayName("save: 존재하지 않는 회원이면 IllegalArgumentException")
    void save_존재하지_않는_회원이면_예외() {
        given(userRepository.findByEmailHash(EmailHasher.sha512Hex("nobody@example.com"))).willReturn(Optional.empty());

        assertThatThrownBy(() -> postService.save("nobody@example.com",
                new PostSaveRequestDto("제목", "내용", null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("존재하지 않는 회원");

        verify(postRepository, never()).save(any());
    }

    @Test
    @DisplayName("update: 작성자 본인이면 제목/내용이 변경 감지로 반영된다")
    void update_작성자_본인이면_수정된다() {
        User owner = userWithEmail("owner@example.com", 1L);
        Post post = postOf(owner, 100L);
        given(postRepository.findById(100L)).willReturn(Optional.of(post));

        Long id = postService.update(100L, new PostUpdateRequestDto("새 제목", "새 내용", null, null),
                authOf("owner@example.com", Role.USER));

        assertThat(id).isEqualTo(100L);
        assertThat(post.getTitle()).isEqualTo("새 제목");
        assertThat(post.getContent()).isEqualTo("새 내용");
    }

    @Test
    @DisplayName("update: picture가 기존과 다르면 반영하고 이전 이미지 파일을 지운다")
    void update_이미지가_바뀌면_이전_파일을_지운다() {
        User owner = userWithEmail("owner@example.com", 1L);
        Post post = postOf(owner, 100L);
        ReflectionTestUtils.setField(post, "picture", "/images/old.png");
        given(postRepository.findById(100L)).willReturn(Optional.of(post));

        postService.update(100L, new PostUpdateRequestDto("새 제목", "새 내용", "/images/new.png", null),
                authOf("owner@example.com", Role.USER));

        assertThat(post.getPicture()).isEqualTo("/images/new.png");
        verify(postImageService).deleteIfExists("/images/old.png");
    }

    @Test
    @DisplayName("update: picture가 기존과 같으면 이미지 파일을 지우지 않는다")
    void update_이미지가_그대로면_파일을_지우지_않는다() {
        User owner = userWithEmail("owner@example.com", 1L);
        Post post = postOf(owner, 100L);
        ReflectionTestUtils.setField(post, "picture", "/images/same.png");
        given(postRepository.findById(100L)).willReturn(Optional.of(post));

        postService.update(100L, new PostUpdateRequestDto("새 제목", "새 내용", "/images/same.png", null),
                authOf("owner@example.com", Role.USER));

        verify(postImageService, never()).deleteIfExists(any());
    }

    @Test
    @DisplayName("update: 작성자가 아니면 AccessDeniedException, 내용은 변경되지 않는다")
    void update_타인이면_거부된다() {
        User owner = userWithEmail("owner@example.com", 1L);
        Post post = postOf(owner, 100L);
        given(postRepository.findById(100L)).willReturn(Optional.of(post));

        assertThatThrownBy(() -> postService.update(100L, new PostUpdateRequestDto("해킹", "해킹", null, null),
                authOf("intruder@example.com", Role.USER)))
                .isInstanceOf(AccessDeniedException.class);

        assertThat(post.getTitle()).isEqualTo("원래 제목");
    }

    @Test
    @DisplayName("update: ROLE_ADMIN이면 작성자가 아니어도 수정할 수 있다")
    void update_관리자는_타인_글도_수정할_수_있다() {
        User owner = userWithEmail("owner@example.com", 1L);
        Post post = postOf(owner, 100L);
        given(postRepository.findById(100L)).willReturn(Optional.of(post));

        postService.update(100L, new PostUpdateRequestDto("관리자 수정", "관리자 수정", null, null),
                authOf("admin@example.com", Role.ADMIN));

        assertThat(post.getTitle()).isEqualTo("관리자 수정");
    }

    @Test
    @DisplayName("update: 작성자가 없는(user=null) 게시글은 관리자만 수정할 수 있다")
    void update_작성자없는_글은_관리자만_가능() {
        Post post = Post.builder().title("고아 게시글").content("c").user(null).build();
        ReflectionTestUtils.setField(post, "id", 200L);
        given(postRepository.findById(200L)).willReturn(Optional.of(post));

        assertThatThrownBy(() -> postService.update(200L, new PostUpdateRequestDto("x", "y", null, null),
                authOf("someone@example.com", Role.USER)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("delete: 작성자가 아니면 AccessDeniedException이고 delete가 호출되지 않는다")
    void delete_타인이면_거부된다() {
        User owner = userWithEmail("owner@example.com", 1L);
        Post post = postOf(owner, 100L);
        given(postRepository.findById(100L)).willReturn(Optional.of(post));

        assertThatThrownBy(() -> postService.delete(100L, authOf("intruder@example.com", Role.USER)))
                .isInstanceOf(AccessDeniedException.class);

        verify(postRepository, never()).delete(any());
        verify(commentRepository, never()).deleteAllByPostId(any());
        verify(postImageService, never()).deleteIfExists(any());
    }

    @Test
    @DisplayName("delete: 작성자 본인이면 댓글을 먼저 지우고 게시글을 삭제한다 (comments.post_id FK 위반 방지)")
    void delete_작성자_본인이면_댓글을_먼저_지우고_삭제된다() {
        User owner = userWithEmail("owner@example.com", 1L);
        Post post = postOf(owner, 100L);
        given(postRepository.findById(100L)).willReturn(Optional.of(post));

        postService.delete(100L, authOf("owner@example.com", Role.USER));

        var inOrder = org.mockito.Mockito.inOrder(commentRepository, postRepository);
        inOrder.verify(commentRepository).deleteAllByPostId(100L);
        inOrder.verify(postRepository).delete(post);
    }

    @Test
    @DisplayName("delete: 게시글에 이미지가 있으면 삭제 후 이미지 파일도 정리한다")
    void delete_이미지가_있으면_파일도_정리한다() {
        User owner = userWithEmail("owner@example.com", 1L);
        Post post = postOf(owner, 100L);
        ReflectionTestUtils.setField(post, "picture", "/images/old.png");
        given(postRepository.findById(100L)).willReturn(Optional.of(post));

        postService.delete(100L, authOf("owner@example.com", Role.USER));

        verify(postImageService).deleteIfExists("/images/old.png");
    }

    @Test
    @DisplayName("findById: 존재하지 않는 ID면 IllegalArgumentException")
    void findById_존재하지_않으면_예외() {
        given(postRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> postService.findById(999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("id=999");
    }

    @Test
    @DisplayName("findAllDesc: Repository의 Page를 PostsPageResponseDto로 그대로 변환한다")
    void findAllDesc_페이지를_변환한다() {
        User owner = userWithEmail("owner@example.com", 1L);
        Post post = postOf(owner, 1L);
        Pageable pageable = PageRequest.of(0, 10);
        Page<Post> page = new PageImpl<>(List.of(post), pageable, 1);
        given(postRepository.search(null, null, pageable)).willReturn(page);

        PostsPageResponseDto result = postService.findAllDesc(pageable);

        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.totalPages()).isEqualTo(1);
        assertThat(result.first()).isTrue();
        assertThat(result.last()).isTrue();
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).title()).isEqualTo("원래 제목");
        assertThat(result.content().get(0).author()).isEqualTo("tester");
    }

    @Test
    @DisplayName("findAllDesc(keyword, category): 검색어·분류를 리포지토리에 그대로 전달하고 댓글 수를 함께 담는다")
    void findAllDesc_검색어와_분류를_전달하고_댓글수를_담는다() {
        User owner = userWithEmail("owner@example.com", 1L);
        Post post = postOf(owner, 1L);
        Pageable pageable = PageRequest.of(0, 10);
        Page<Post> page = new PageImpl<>(List.of(post), pageable, 1);
        given(postRepository.search("공지", Category.NOTICE, pageable)).willReturn(page);
        given(commentRepository.countByPostIdIn(List.of(1L))).willReturn(Map.of(1L, 3L));

        PostsPageResponseDto result = postService.findAllDesc(pageable, "공지", Category.NOTICE);

        assertThat(result.content().get(0).commentCount()).isEqualTo(3L);
    }

    @Test
    @DisplayName("findAllDesc: 검색어가 공백뿐이면 null로 정규화해 리포지토리에 전달한다")
    void findAllDesc_공백_검색어는_null로_정규화된다() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Post> page = new PageImpl<>(List.of(), pageable, 0);
        given(postRepository.search(null, null, pageable)).willReturn(page);

        postService.findAllDesc(pageable, "   ", null);

        verify(postRepository).search(null, null, pageable);
    }

    @Test
    @DisplayName("findPopular: 조회수 상위 N개를 댓글 수와 함께 반환한다")
    void findPopular_조회수_상위N개를_반환한다() {
        User owner = userWithEmail("owner@example.com", 1L);
        Post post = postOf(owner, 1L);
        given(postRepository.findTopByViewCountDesc(PageRequest.of(0, 5))).willReturn(List.of(post));
        given(commentRepository.countByPostIdIn(List.of(1L))).willReturn(Map.of(1L, 2L));

        List<PostsListResponseDto> result = postService.findPopular(5);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).commentCount()).isEqualTo(2L);
    }

    @Test
    @DisplayName("findByIdForView: 조회할 때마다 조회수를 1 늘리고, 추천 수·내가 눌렀는지를 함께 담는다")
    void findByIdForView_조회수를_늘리고_추천정보를_담는다() {
        User owner = userWithEmail("owner@example.com", 1L);
        Post post = postOf(owner, 100L);
        given(postRepository.findById(100L)).willReturn(Optional.of(post));
        given(postLikeRepository.existsByPostIdAndUserId(100L, 1L)).willReturn(true);
        given(postLikeRepository.countByPostId(100L)).willReturn(3L);
        given(userRepository.findByEmailHash(EmailHasher.sha512Hex("owner@example.com"))).willReturn(Optional.of(owner));

        var result = postService.findByIdForView(100L, authOf("owner@example.com", Role.USER));

        assertThat(post.getViewCount()).isEqualTo(1L);
        assertThat(result.viewCount()).isEqualTo(1L);
        assertThat(result.likeCount()).isEqualTo(3L);
        assertThat(result.likedByMe()).isTrue();
    }

    @Test
    @DisplayName("findByIdForView: 익명이면 likedByMe는 항상 false다")
    void findByIdForView_익명이면_likedByMe는_false() {
        User owner = userWithEmail("owner@example.com", 1L);
        Post post = postOf(owner, 100L);
        given(postRepository.findById(100L)).willReturn(Optional.of(post));
        given(postLikeRepository.countByPostId(100L)).willReturn(0L);

        var result = postService.findByIdForView(100L, null);

        assertThat(result.likedByMe()).isFalse();
        verify(postLikeRepository, never()).existsByPostIdAndUserId(any(), any());
    }

    @Test
    @DisplayName("toggleLike: 아직 안 눌렀으면 추천을 추가하고 liked=true를 반환한다")
    void toggleLike_안눌렀으면_추가한다() {
        User user = userWithEmail("liker@example.com", 2L);
        Post post = postOf(user, 100L);
        given(postRepository.findById(100L)).willReturn(Optional.of(post));
        given(userRepository.findByEmailHash(EmailHasher.sha512Hex("liker@example.com"))).willReturn(Optional.of(user));
        given(postLikeRepository.existsByPostIdAndUserId(100L, 2L)).willReturn(false);
        given(postLikeRepository.countByPostId(100L)).willReturn(1L);

        var result = postService.toggleLike(100L, authOf("liker@example.com", Role.USER));

        assertThat(result.liked()).isTrue();
        assertThat(result.likeCount()).isEqualTo(1L);
        verify(postLikeRepository).save(any());
        verify(postLikeRepository, never()).deleteByPostIdAndUserId(any(), any());
    }

    @Test
    @DisplayName("toggleLike: 이미 눌렀으면 추천을 취소하고 liked=false를 반환한다")
    void toggleLike_이미눌렀으면_취소한다() {
        User user = userWithEmail("liker@example.com", 2L);
        Post post = postOf(user, 100L);
        given(postRepository.findById(100L)).willReturn(Optional.of(post));
        given(userRepository.findByEmailHash(EmailHasher.sha512Hex("liker@example.com"))).willReturn(Optional.of(user));
        given(postLikeRepository.existsByPostIdAndUserId(100L, 2L)).willReturn(true);
        given(postLikeRepository.countByPostId(100L)).willReturn(0L);

        var result = postService.toggleLike(100L, authOf("liker@example.com", Role.USER));

        assertThat(result.liked()).isFalse();
        verify(postLikeRepository).deleteByPostIdAndUserId(100L, 2L);
        verify(postLikeRepository, never()).save(any());
    }
}
