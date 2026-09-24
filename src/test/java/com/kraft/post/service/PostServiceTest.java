package com.kraft.post.service;

import com.kraft.comment.domain.CommentRepository;
import com.kraft.shared.exception.NotFoundException;
import com.kraft.post.domain.Category;
import com.kraft.post.domain.Post;
import com.kraft.post.domain.PostLikeRepository;
import com.kraft.post.domain.PostRepository;
import com.kraft.post.dto.PostRowDto;
import com.kraft.post.dto.PostSaveRequestDto;
import com.kraft.post.dto.PostsListResponseDto;
import com.kraft.post.dto.PostsPageResponseDto;
import com.kraft.post.dto.PostUpdateRequestDto;
import com.kraft.user.domain.EmailHasher;
import com.kraft.user.domain.Role;
import com.kraft.user.domain.User;
import com.kraft.user.domain.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

    @Mock
    private PostImageRegistry postImageRegistry;

    @Mock
    private PostImageCleaner postImageCleaner;

    @Mock
    private PostLikeWriter postLikeWriter;

    private PostService postService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        postService = new PostService(postRepository, userRepository, commentRepository, postImageService,
                postLikeRepository, postImageRegistry, postImageCleaner, postLikeWriter);
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

    /** search/findTopByViewCountDesc가 반환하는 projection. postOf와 같은 표시값을 쓴다. */
    private static PostRowDto rowOf(User owner, Long id) {
        return new PostRowDto(id, "원래 제목", owner.getName(), null, null, 0L);
    }

    private static Authentication authOf(String email, Role role) {
        return new UsernamePasswordAuthenticationToken(
                email, null, List.of(new SimpleGrantedAuthority(role.getKey())));
    }

    @Test
    @DisplayName("save: 존재하는 회원이면 작성자로 지정해 저장하고 ID를 반환한다")
    void save_whenUserExists_savesPostAndReturnsId() {
        User user = userWithEmail("tester@example.com", 1L);
        given(userRepository.findByEmailHash(EmailHasher.sha512Hex("tester@example.com"))).willReturn(Optional.of(user));
        Post saved = postOf(user, 10L);
        given(postRepository.save(any(Post.class))).willReturn(saved);

        Long id = postService.save(authOf("tester@example.com", Role.USER),
                new PostSaveRequestDto("제목", "내용", null, null));

        assertThat(id).isEqualTo(10L);
    }

    @Test
    @DisplayName("save: 이메일 인증 전(GUEST) 회원이면 AccessDeniedException이고 저장되지 않는다")
    void save_whenUserIsGuest_throwsAccessDeniedExceptionAndDoesNotSave() {
        User guest = User.builder().name("tester").email("guest@example.com").password("encoded").role(Role.GUEST).build();
        ReflectionTestUtils.setField(guest, "id", 1L);
        given(userRepository.findByEmailHash(EmailHasher.sha512Hex("guest@example.com"))).willReturn(Optional.of(guest));

        assertThatThrownBy(() -> postService.save(authOf("guest@example.com", Role.USER), new PostSaveRequestDto("제목", "내용", null, null)))
                .isInstanceOf(AccessDeniedException.class);

        verify(postRepository, never()).save(any());
    }

    @Test
    @DisplayName("save: 존재하지 않는 회원이면 NotFoundException")
    void save_whenUserNotFound_throwsIllegalArgumentException() {
        given(userRepository.findByEmailHash(EmailHasher.sha512Hex("nobody@example.com"))).willReturn(Optional.empty());

        assertThatThrownBy(() -> postService.save(authOf("nobody@example.com", Role.USER),
                new PostSaveRequestDto("제목", "내용", null, null)))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("존재하지 않는 회원");

        verify(postRepository, never()).save(any());
    }

    @Test
    @DisplayName("update: 작성자 본인이면 제목/내용이 변경 감지로 반영된다")
    void update_whenAuthor_updatesTitleAndContent() {
        User owner = userWithEmail("owner@example.com", 1L);
        Post post = postOf(owner, 100L);
        given(postRepository.findById(100L)).willReturn(Optional.of(post));
        given(userRepository.findByEmailHash(EmailHasher.sha512Hex("owner@example.com"))).willReturn(Optional.of(owner));

        Long id = postService.update(100L, new PostUpdateRequestDto("새 제목", "새 내용", null, null, null),
                authOf("owner@example.com", Role.USER));

        assertThat(id).isEqualTo(100L);
        assertThat(post.getTitle()).isEqualTo("새 제목");
        assertThat(post.getContent()).isEqualTo("새 내용");
    }

    @Test
    @DisplayName("update: picture가 기존과 다르면 새 이미지의 소유권을 확인하고 이전 이미지는 삭제 예약만 한다")
    void update_whenPictureChanges_attachesNewImageAndSchedulesOldForDeletion() {
        User owner = userWithEmail("owner@example.com", 1L);
        Post post = postOf(owner, 100L);
        ReflectionTestUtils.setField(post, "picture", "/images/old.png");
        given(postRepository.findById(100L)).willReturn(Optional.of(post));
        given(userRepository.findByEmailHash(EmailHasher.sha512Hex("owner@example.com"))).willReturn(Optional.of(owner));

        postService.update(100L, new PostUpdateRequestDto("새 제목", "새 내용", "/images/new.png", null, null),
                authOf("owner@example.com", Role.USER));

        assertThat(post.getPicture()).isEqualTo("/images/new.png");
        verify(postImageRegistry).attach("/images/new.png", owner, post);
        // 파일을 직접 지우지 않는다. 트랜잭션이 롤백되면 예약도 사라져 기존 이미지가 보존된다(F05).
        verify(postImageRegistry).markForDeletion("/images/old.png");
        verify(postImageService, never()).deleteIfExists(any());
    }

    @Test
    @DisplayName("update: picture가 기존과 같으면 삭제를 예약하지 않는다")
    void update_whenPictureUnchanged_doesNotScheduleDeletion() {
        User owner = userWithEmail("owner@example.com", 1L);
        Post post = postOf(owner, 100L);
        ReflectionTestUtils.setField(post, "picture", "/images/same.png");
        given(postRepository.findById(100L)).willReturn(Optional.of(post));
        given(userRepository.findByEmailHash(EmailHasher.sha512Hex("owner@example.com"))).willReturn(Optional.of(owner));

        postService.update(100L, new PostUpdateRequestDto("새 제목", "새 내용", "/images/same.png", null, null),
                authOf("owner@example.com", Role.USER));

        verify(postImageRegistry, never()).markForDeletion(any());
        verify(postImageService, never()).deleteIfExists(any());
    }

    @Test
    @DisplayName("update: 작성자가 아니면 AccessDeniedException, 내용은 변경되지 않는다")
    void update_whenNotAuthor_throwsAccessDeniedExceptionAndDoesNotModify() {
        User owner = userWithEmail("owner@example.com", 1L);
        Post post = postOf(owner, 100L);
        User intruder = userWithEmail("intruder@example.com", 2L);
        given(postRepository.findById(100L)).willReturn(Optional.of(post));
        given(userRepository.findByEmailHash(EmailHasher.sha512Hex("intruder@example.com"))).willReturn(Optional.of(intruder));

        assertThatThrownBy(() -> postService.update(100L, new PostUpdateRequestDto("해킹", "해킹", null, null, null),
                authOf("intruder@example.com", Role.USER)))
                .isInstanceOf(AccessDeniedException.class);

        assertThat(post.getTitle()).isEqualTo("원래 제목");
    }

    @Test
    @DisplayName("update: 정지된 작성자는 자신의 글도 수정할 수 없다")
    void update_whenAuthorIsSuspended_throwsAccessDeniedExceptionAndDoesNotModify() {
        User owner = userWithEmail("owner@example.com", 1L);
        owner.suspendUntil(java.time.LocalDateTime.now().plusDays(1), "규정 위반");
        Post post = postOf(owner, 100L);
        given(postRepository.findById(100L)).willReturn(Optional.of(post));
        given(userRepository.findByEmailHash(EmailHasher.sha512Hex("owner@example.com"))).willReturn(Optional.of(owner));

        assertThatThrownBy(() -> postService.update(100L, new PostUpdateRequestDto("수정 시도", "내용", null, null, null),
                authOf("owner@example.com", Role.USER)))
                .isInstanceOf(AccessDeniedException.class);

        assertThat(post.getTitle()).isEqualTo("원래 제목");
    }

    @Test
    @DisplayName("update: ROLE_ADMIN이면 작성자가 아니어도 수정할 수 있다")
    void update_whenAdmin_updatesEvenIfNotAuthor() {
        User owner = userWithEmail("owner@example.com", 1L);
        Post post = postOf(owner, 100L);
        User admin = userWithEmail("admin@example.com", 2L);
        given(postRepository.findById(100L)).willReturn(Optional.of(post));
        given(userRepository.findByEmailHash(EmailHasher.sha512Hex("admin@example.com"))).willReturn(Optional.of(admin));

        postService.update(100L, new PostUpdateRequestDto("관리자 수정", "관리자 수정", null, null, null),
                authOf("admin@example.com", Role.ADMIN));

        assertThat(post.getTitle()).isEqualTo("관리자 수정");
    }

    @Test
    @DisplayName("update: 작성자가 없는(user=null) 게시글은 관리자만 수정할 수 있다")
    void update_whenPostHasNoAuthor_throwsAccessDeniedExceptionForNonAdmin() {
        Post post = Post.builder().title("고아 게시글").content("c").user(null).build();
        ReflectionTestUtils.setField(post, "id", 200L);
        User someone = userWithEmail("someone@example.com", 3L);
        given(postRepository.findById(200L)).willReturn(Optional.of(post));
        given(userRepository.findByEmailHash(EmailHasher.sha512Hex("someone@example.com"))).willReturn(Optional.of(someone));

        assertThatThrownBy(() -> postService.update(200L, new PostUpdateRequestDto("x", "y", null, null, null),
                authOf("someone@example.com", Role.USER)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("delete: 작성자가 아니면 AccessDeniedException이고 delete가 호출되지 않는다")
    void delete_whenNotAuthor_throwsAccessDeniedExceptionAndDoesNotDelete() {
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
    void delete_whenAuthor_deletesCommentsFirstThenDeletesPost() {
        User owner = userWithEmail("owner@example.com", 1L);
        Post post = postOf(owner, 100L);
        given(postRepository.findById(100L)).willReturn(Optional.of(post));

        postService.delete(100L, authOf("owner@example.com", Role.USER));

        var inOrder = org.mockito.Mockito.inOrder(commentRepository, postRepository);
        inOrder.verify(commentRepository).deleteAllByPostId(100L);
        inOrder.verify(postRepository).delete(post);
    }

    @Test
    @DisplayName("delete: 게시글에 붙은 이미지의 삭제를 예약하고, 커밋 후 그 이미지만 정리한다")
    void delete_whenPostHasImage_schedulesImageDeletionAndTriggersCleanup() {
        User owner = userWithEmail("owner@example.com", 1L);
        Post post = postOf(owner, 100L);
        ReflectionTestUtils.setField(post, "picture", "/images/old.png");
        given(postRepository.findById(100L)).willReturn(Optional.of(post));
        given(postImageRegistry.markPostImagesForDeletion(100L)).willReturn(List.of(55L));

        postService.delete(100L, authOf("owner@example.com", Role.USER));

        // 예약이 post_id를 비워야 게시글 DELETE가 FK에 걸리지 않는다.
        var inOrder = org.mockito.Mockito.inOrder(postImageRegistry, postRepository);
        inOrder.verify(postImageRegistry).markPostImagesForDeletion(100L);
        inOrder.verify(postRepository).delete(post);
        // 트랜잭션 밖에서 호출했으므로 AfterCommit이 즉시 실행된다. 이번 요청이 표시한
        // id만 넘긴다 — 시스템 전체의 삭제 대기열을 매번 훑지 않는다.
        verify(postImageCleaner).cleanPendingDeletionsFor(List.of(55L));
        verify(postImageService, never()).deleteIfExists(any());
    }

    @Test
    @DisplayName("findById: 존재하지 않는 ID면 IllegalArgumentException")
    void findById_whenNotFound_throwsIllegalArgumentException() {
        given(postRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> postService.findById(999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("id=999");
    }

    /** 정렬 없는 요청은 서비스가 id 내림차순 Sort를 채운 뒤 리포지토리로 넘긴다(B10). */
    private static Pageable idDescOf(Pageable requested) {
        return PageRequest.of(requested.getPageNumber(), requested.getPageSize(), Sort.by(Sort.Direction.DESC, "id"));
    }

    @Test
    @DisplayName("findAllDesc: Repository의 Page를 PostsPageResponseDto로 그대로 변환한다")
    void findAllDesc_convertsPageToDto() {
        User owner = userWithEmail("owner@example.com", 1L);
        PostRowDto row = rowOf(owner, 1L);
        Pageable pageable = PageRequest.of(0, 10);
        Page<PostRowDto> page = new PageImpl<>(List.of(row), pageable, 1);
        given(postRepository.search(null, null, idDescOf(pageable))).willReturn(page);

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
    void findAllDesc_passesKeywordAndCategoryAndIncludesCommentCounts() {
        User owner = userWithEmail("owner@example.com", 1L);
        PostRowDto row = rowOf(owner, 1L);
        Pageable pageable = PageRequest.of(0, 10);
        Page<PostRowDto> page = new PageImpl<>(List.of(row), pageable, 1);
        given(postRepository.search("공지", Category.NOTICE, idDescOf(pageable))).willReturn(page);
        given(commentRepository.countByPostIdIn(List.of(1L))).willReturn(Map.of(1L, 3L));

        PostsPageResponseDto result = postService.findAllDesc(pageable, "공지", Category.NOTICE);

        assertThat(result.content().get(0).commentCount()).isEqualTo(3L);
    }

    @Test
    @DisplayName("findAllDesc: 검색어가 공백뿐이면 null로 정규화해 리포지토리에 전달한다")
    void findAllDesc_normalizesBlankKeywordToNull() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<PostRowDto> page = new PageImpl<>(List.of(), pageable, 0);
        given(postRepository.search(null, null, idDescOf(pageable))).willReturn(page);

        postService.findAllDesc(pageable, "   ", null);

        verify(postRepository).search(null, null, idDescOf(pageable));
    }

    @Test
    @DisplayName("findAllDesc: 100자를 넘는 검색어는 100자로 잘라 리포지토리에 전달한다")
    void findAllDesc_truncatesKeywordLongerThan100Characters() {
        Pageable pageable = PageRequest.of(0, 10);
        String tooLong = "가".repeat(150);
        String truncated = "가".repeat(100);
        Page<PostRowDto> page = new PageImpl<>(List.of(), pageable, 0);
        given(postRepository.search(truncated, null, idDescOf(pageable))).willReturn(page);

        postService.findAllDesc(pageable, tooLong, null);

        verify(postRepository).search(truncated, null, idDescOf(pageable));
    }

    /**
     * B10: viewCount·updatedAt 정렬을 요청하면 그 컬럼이 주 정렬로 리포지토리에 전달되고,
     * id 내림차순이 동점 처리로 끝에 붙어야 한다 — 예전에는 리포지토리 JPQL의 고정
     * ORDER BY p.id DESC가 항상 먼저라 이 정렬이 반환 순서에 전혀 반영되지 않았다.
     */
    @Test
    @DisplayName("findAllDesc: viewCount 정렬 요청은 id 내림차순 동점 처리를 덧붙여 리포지토리에 전달된다")
    void findAllDesc_withViewCountSort_appendsIdTieBreaker() {
        Pageable requested = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "viewCount"));
        Pageable expectedEffective = PageRequest.of(0, 10,
                Sort.by(Sort.Direction.DESC, "viewCount").and(Sort.by(Sort.Direction.DESC, "id")));
        Page<PostRowDto> page = new PageImpl<>(List.of(), expectedEffective, 0);
        given(postRepository.search(null, null, expectedEffective)).willReturn(page);

        postService.findAllDesc(requested);

        verify(postRepository).search(null, null, expectedEffective);
    }

    @Test
    @DisplayName("findPopular: 조회수 상위 N개를 반환한다 (댓글 수는 화면에 쓰이지 않아 집계하지 않는다)")
    void findPopular_returnsTopPostsWithoutCommentCountAggregation() {
        User owner = userWithEmail("owner@example.com", 1L);
        PostRowDto row = rowOf(owner, 1L);
        given(postRepository.findTopByViewCountDesc(PageRequest.of(0, 5))).willReturn(List.of(row));

        List<PostsListResponseDto> result = postService.findPopular(5);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).commentCount()).isZero();
        verify(commentRepository, never()).countByPostIdIn(any());
    }

    @Test
    @DisplayName("findRelated: 분류·제외할 id·limit을 그대로 리포지토리에 넘긴다")
    void findRelated_delegatesToRepositoryWithCategoryExcludeIdAndLimit() {
        User owner = userWithEmail("owner@example.com", 1L);
        PostRowDto row = rowOf(owner, 2L);
        given(postRepository.findRelated(Category.FREE, 1L, PageRequest.of(0, 5))).willReturn(List.of(row));

        List<PostRowDto> result = postService.findRelated(Category.FREE, 1L, 5);

        assertThat(result).containsExactly(row);
    }

    @Test
    @DisplayName("findByIdForView: 조회수는 엔티티를 건드리지 않고 원자적 UPDATE로 올리고, 추천 정보를 함께 담는다")
    void findByIdForView_increasesViewCountAtomicallyAndIncludesLikeInformation() {
        User owner = userWithEmail("owner@example.com", 1L);
        Post post = postOf(owner, 100L);
        given(postRepository.findById(100L)).willReturn(Optional.of(post));
        given(postLikeRepository.existsByPostIdAndUserId(100L, 1L)).willReturn(true);
        given(postLikeRepository.countByPostId(100L)).willReturn(3L);
        given(userRepository.findByEmailHash(EmailHasher.sha512Hex("owner@example.com"))).willReturn(Optional.of(owner));

        var result = postService.findByIdForView(100L, authOf("owner@example.com", Role.USER));

        // 조회수 증가는 전용 UPDATE 한 문장이다. 엔티티를 바꿔 변경 감지에 맡기면 제목·본문까지
        // 함께 UPDATE에 실려 겹친 편집을 되돌린다(F02). 실제 증가분은 PostViewCountIsolationTest가
        // 진짜 DB로 검증한다 — mock 리포지토리로는 관찰할 수 없는 지점이다.
        var inOrder = org.mockito.Mockito.inOrder(postRepository);
        inOrder.verify(postRepository).increaseViewCount(100L);
        inOrder.verify(postRepository).findById(100L);
        assertThat(post.getViewCount()).isZero();

        assertThat(result.likeCount()).isEqualTo(3L);
        assertThat(result.likedByMe()).isTrue();
    }

    @Test
    @DisplayName("findByIdForView: 익명이면 likedByMe는 항상 false다")
    void findByIdForView_whenAnonymous_likedByMeIsFalse() {
        User owner = userWithEmail("owner@example.com", 1L);
        Post post = postOf(owner, 100L);
        given(postRepository.findById(100L)).willReturn(Optional.of(post));
        given(postLikeRepository.countByPostId(100L)).willReturn(0L);

        var result = postService.findByIdForView(100L, null);

        assertThat(result.likedByMe()).isFalse();
        verify(postLikeRepository, never()).existsByPostIdAndUserId(any(), any());
    }

    @Test
    @DisplayName("setLike(true): 아직 안 눌렀으면 추천을 추가하고 liked=true를 반환한다")
    void setLike_toTrueWhenNotLikedYet_addsLikeAndReturnsLikedTrue() {
        User user = userWithEmail("liker@example.com", 2L);
        Post post = postOf(user, 100L);
        given(postRepository.findById(100L)).willReturn(Optional.of(post));
        given(userRepository.findByEmailHash(EmailHasher.sha512Hex("liker@example.com"))).willReturn(Optional.of(user));
        given(postLikeRepository.existsByPostIdAndUserId(100L, 2L)).willReturn(false);
        given(postLikeWriter.countByPostId(100L)).willReturn(1L);

        var result = postService.setLike(100L, true, authOf("liker@example.com", Role.USER));

        assertThat(result.liked()).isTrue();
        assertThat(result.likeCount()).isEqualTo(1L);
        verify(postLikeWriter).insert(post, user);
        verify(postLikeWriter, never()).delete(any(), any());
    }

    @Test
    @DisplayName("setLike(false): 추천을 취소하고 liked=false를 반환한다")
    void setLike_toFalse_removesLikeAndReturnsLikedFalse() {
        User user = userWithEmail("liker@example.com", 2L);
        Post post = postOf(user, 100L);
        given(postRepository.findById(100L)).willReturn(Optional.of(post));
        given(userRepository.findByEmailHash(EmailHasher.sha512Hex("liker@example.com"))).willReturn(Optional.of(user));
        given(postLikeWriter.countByPostId(100L)).willReturn(0L);

        var result = postService.setLike(100L, false, authOf("liker@example.com", Role.USER));

        assertThat(result.liked()).isFalse();
        verify(postLikeWriter).delete(100L, 2L);
        verify(postLikeWriter, never()).insert(any(), any());
    }

    @Test
    @DisplayName("setLike(true): 이미 추천한 상태면 아무것도 쓰지 않고 같은 결과를 돌려준다(멱등)")
    void setLike_toTrueWhenAlreadyLiked_isIdempotent() {
        User user = userWithEmail("liker@example.com", 2L);
        Post post = postOf(user, 100L);
        given(postRepository.findById(100L)).willReturn(Optional.of(post));
        given(userRepository.findByEmailHash(EmailHasher.sha512Hex("liker@example.com"))).willReturn(Optional.of(user));
        given(postLikeRepository.existsByPostIdAndUserId(100L, 2L)).willReturn(true);
        given(postLikeWriter.countByPostId(100L)).willReturn(1L);

        var result = postService.setLike(100L, true, authOf("liker@example.com", Role.USER));

        assertThat(result.liked()).isTrue();
        verify(postLikeWriter, never()).insert(any(), any());
    }

    /**
     * B09: 무엇이 "중복이라 흡수해도 되는 예외"인지는 {@code PostLikeWriter.isDuplicateLikeConstraint}가
     * 실제 제약 이름을 보고 판단한다(PostLikeWriterTest가 실제 DB로 검증). PostService는 그
     * 판정 결과를 그대로 따를 뿐이므로, 여기서는 판정이 false일 때 예외가 삼켜지지 않고
     * 전파되는지만 확인한다 — FK 위반을 "이미 추천됨"으로 위장하지 않는다는 뜻이다.
     */
    @Test
    @DisplayName("B09: 중복 제약이 아닌 실패는 PostService가 그대로 전파한다")
    void setLike_whenInsertFailsForNonDuplicateReason_propagatesException() {
        User user = userWithEmail("liker@example.com", 2L);
        Post post = postOf(user, 100L);
        given(postRepository.findById(100L)).willReturn(Optional.of(post));
        given(userRepository.findByEmailHash(EmailHasher.sha512Hex("liker@example.com"))).willReturn(Optional.of(user));
        given(postLikeRepository.existsByPostIdAndUserId(100L, 2L)).willReturn(false);
        DataIntegrityViolationException fkViolation = new DataIntegrityViolationException("FK_POST_LIKES_POST");
        org.mockito.BDDMockito.willThrow(fkViolation).given(postLikeWriter).insert(post, user);
        given(postLikeWriter.isDuplicateLikeConstraint(fkViolation)).willReturn(false);

        assertThatThrownBy(() -> postService.setLike(100L, true, authOf("liker@example.com", Role.USER)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("B09: 검사와 INSERT 사이에 같은 추천이 들어와도(유니크 제약 위반) 성공으로 처리한다")
    void setLike_whenConcurrentInsertWins_treatsAsSuccess() {
        User user = userWithEmail("liker@example.com", 2L);
        Post post = postOf(user, 100L);
        given(postRepository.findById(100L)).willReturn(Optional.of(post));
        given(userRepository.findByEmailHash(EmailHasher.sha512Hex("liker@example.com"))).willReturn(Optional.of(user));
        given(postLikeRepository.existsByPostIdAndUserId(100L, 2L)).willReturn(false);
        given(postLikeWriter.countByPostId(100L)).willReturn(1L);
        DataIntegrityViolationException duplicate = new DataIntegrityViolationException("UK_POST_LIKE_POST_USER");
        org.mockito.BDDMockito.willThrow(duplicate).given(postLikeWriter).insert(post, user);
        given(postLikeWriter.isDuplicateLikeConstraint(duplicate)).willReturn(true);

        var result = postService.setLike(100L, true, authOf("liker@example.com", Role.USER));

        assertThat(result.liked()).isTrue();
        assertThat(result.likeCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("save: 일반 사용자는 NOTICE 분류로 글을 쓸 수 없다")
    void save_whenUserUsesNoticeCategory_throwsAccessDeniedException() {
        User user = userWithEmail("tester@example.com", 1L);
        given(userRepository.findByEmailHash(EmailHasher.sha512Hex("tester@example.com"))).willReturn(Optional.of(user));

        assertThatThrownBy(() -> postService.save(authOf("tester@example.com", Role.USER),
                new PostSaveRequestDto("공지", "내용", null, Category.NOTICE)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("공지 분류는 관리자만");

        verify(postRepository, never()).save(any());
    }

    @Test
    @DisplayName("save: 관리자는 NOTICE 분류로 글을 쓸 수 있다")
    void save_whenAdminUsesNoticeCategory_saves() {
        User admin = userWithEmail("admin@example.com", 1L);
        given(userRepository.findByEmailHash(EmailHasher.sha512Hex("admin@example.com"))).willReturn(Optional.of(admin));
        given(postRepository.save(any(Post.class))).willReturn(postOf(admin, 10L));

        Long id = postService.save(authOf("admin@example.com", Role.ADMIN),
                new PostSaveRequestDto("공지", "내용", null, Category.NOTICE));

        assertThat(id).isEqualTo(10L);
    }

    @Test
    @DisplayName("update: 일반 사용자는 자기 글이어도 NOTICE로 바꿀 수 없다")
    void update_whenUserSwitchesToNoticeCategory_throwsAccessDeniedException() {
        User owner = userWithEmail("owner@example.com", 1L);
        Post post = postOf(owner, 100L);
        given(postRepository.findById(100L)).willReturn(Optional.of(post));
        given(userRepository.findByEmailHash(EmailHasher.sha512Hex("owner@example.com"))).willReturn(Optional.of(owner));

        assertThatThrownBy(() -> postService.update(100L,
                new PostUpdateRequestDto("제목", "내용", null, Category.NOTICE, null),
                authOf("owner@example.com", Role.USER)))
                .isInstanceOf(AccessDeniedException.class);

        assertThat(post.getTitle()).isEqualTo("원래 제목");
    }

    @Test
    @DisplayName("update: 화면이 보낸 버전이 지금 버전과 다르면 충돌로 거절하고 내용은 그대로다")
    void update_withStaleVersion_throwsOptimisticLockingFailureException() {
        User owner = userWithEmail("owner@example.com", 1L);
        Post post = postOf(owner, 100L);
        ReflectionTestUtils.setField(post, "version", 3L);
        given(postRepository.findById(100L)).willReturn(Optional.of(post));
        given(userRepository.findByEmailHash(EmailHasher.sha512Hex("owner@example.com"))).willReturn(Optional.of(owner));

        assertThatThrownBy(() -> postService.update(100L,
                new PostUpdateRequestDto("나중 저장", "내용", null, null, 1L),
                authOf("owner@example.com", Role.USER)))
                .isInstanceOf(OptimisticLockingFailureException.class);

        assertThat(post.getTitle()).isEqualTo("원래 제목");
    }

    @Test
    @DisplayName("update: 버전을 보내지 않으면(null) 충돌 검사를 하지 않는다")
    void update_withoutVersion_skipsConflictCheck() {
        User owner = userWithEmail("owner@example.com", 1L);
        Post post = postOf(owner, 100L);
        ReflectionTestUtils.setField(post, "version", 3L);
        given(postRepository.findById(100L)).willReturn(Optional.of(post));
        given(userRepository.findByEmailHash(EmailHasher.sha512Hex("owner@example.com"))).willReturn(Optional.of(owner));

        postService.update(100L, new PostUpdateRequestDto("수정됨", "내용", null, null, null),
                authOf("owner@example.com", Role.USER));

        assertThat(post.getTitle()).isEqualTo("수정됨");
    }
}
