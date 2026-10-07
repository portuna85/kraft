package com.kraft.post.service;

import com.kraft.comment.domain.CommentRepository;
import com.kraft.shared.exception.NotFoundException;
import com.kraft.post.domain.Category;
import com.kraft.post.domain.Post;
import com.kraft.post.domain.PostLikeRepository;
import com.kraft.post.domain.PostHiddenException;
import com.kraft.post.domain.PostNotFoundException;
import com.kraft.post.domain.PostRepository;
import com.kraft.post.dto.PostRowDto;
import com.kraft.post.dto.PostSaveRequestDto;
import com.kraft.post.dto.PostsListResponseDto;
import com.kraft.post.dto.PostsPageResponseDto;
import com.kraft.post.dto.PostUpdateRequestDto;
import com.kraft.report.domain.ReportTargetType;
import com.kraft.report.event.TargetDeletedEvent;
import com.kraft.support.TestAuthentication;
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
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
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

    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    private PostService postService;
    private PostQueryService postQueryService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        postService = new PostService(postRepository, userRepository, commentRepository, postImageService,
                postLikeRepository, postImageRegistry, postImageCleaner, postLikeWriter, eventPublisher);
        postQueryService = new PostQueryService(postRepository, userRepository, commentRepository, postLikeRepository);
    }

    private static User userWithEmail(String email, Long id) {
        return userWithEmail(email, id, Role.USER);
    }

    private static User userWithEmail(String email, Long id, Role role) {
        User user = User.builder().name("tester").email(email).password("encoded").role(role).build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private static PostLikeRepository.LikeSummary likeSummary(long total, long mine) {
        return new PostLikeRepository.LikeSummary() {
            @Override
            public Long getTotal() {
                return total;
            }

            @Override
            public Long getMine() {
                return mine;
            }
        };
    }

    private static Post postOf(User owner, Long id) {
        Post post = Post.builder().title("원래 제목").content("원래 내용").user(owner).build();
        ReflectionTestUtils.setField(post, "id", id);
        return post;
    }

    /** search/findTopByViewCountDesc가 반환하는 projection. postOf와 같은 표시값을 쓴다. */
    private static PostRowDto rowOf(User owner, Long id) {
        return new PostRowDto(id, "원래 제목", owner.getName(), null, null, null, 0L);
    }

    private static Authentication authOf(User user) {
        return TestAuthentication.of(user);
    }

    private static Authentication authOf(Long id, String email, Role role) {
        return TestAuthentication.of(id, email, role);
    }

    @Test
    @DisplayName("save: 존재하는 회원이면 작성자로 지정해 저장하고 ID를 반환한다")
    void save_whenUserExists_savesPostAndReturnsId() {
        User user = userWithEmail("tester@example.com", 1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        Post saved = postOf(user, 10L);
        given(postRepository.save(any(Post.class))).willReturn(saved);

        Long id = postService.save(authOf(user),
                new PostSaveRequestDto("제목", "내용", null, null, null, null));

        assertThat(id).isEqualTo(10L);
    }

    @Test
    @DisplayName("save: 이메일 인증 전(GUEST) 회원이면 AccessDeniedException이고 저장되지 않는다")
    void save_whenUserIsGuest_throwsAccessDeniedExceptionAndDoesNotSave() {
        User guest = User.builder().name("tester").email("guest@example.com").password("encoded").role(Role.GUEST).build();
        ReflectionTestUtils.setField(guest, "id", 1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(guest));

        assertThatThrownBy(() -> postService.save(authOf(guest), new PostSaveRequestDto("제목", "내용", null, null, null, null)))
                .isInstanceOf(AccessDeniedException.class);

        verify(postRepository, never()).save(any());
    }

    @Test
    @DisplayName("save: 존재하지 않는 회원이면 NotFoundException")
    void save_whenUserNotFound_throwsIllegalArgumentException() {
        given(userRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> postService.save(authOf(999L, "nobody@example.com", Role.USER),
                new PostSaveRequestDto("제목", "내용", null, null, null, null)))
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
        given(userRepository.findById(1L)).willReturn(Optional.of(owner));

        PostService.PostUpdateResult result = postService.update(
                100L, new PostUpdateRequestDto("새 제목", "새 내용", null, null, null, null), null, authOf(owner));

        assertThat(result.id()).isEqualTo(100L);
        // 저장 직후 flush해 확정된 버전을 돌려준다(응답 ETag).
        verify(postRepository).flush();
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
        given(userRepository.findById(1L)).willReturn(Optional.of(owner));

        postService.update(100L, new PostUpdateRequestDto("새 제목", "새 내용", "/images/new.png", null, null, null), null,
                authOf(owner));

        assertThat(post.getPicture()).isEqualTo("/images/new.png");
        verify(postImageRegistry).attach("/images/new.png", owner, post);
        // 파일을 직접 지우지 않는다. 트랜잭션이 롤백되면 예약도 사라져 기존 이미지가 보존된다(F05).
        verify(postImageRegistry).markForDeletion("/images/old.png");
        verify(postImageService, never()).deleteIfExists(any());
    }

    @Test
    @DisplayName("update: 새 이미지의 크기는 클라이언트가 보낸 값이 아니라 서버가 측정한 값을 쓴다 (BE-24)")
    void update_whenPictureChanges_usesServerMeasuredSize() {
        User owner = userWithEmail("owner@example.com", 1L);
        Post post = postOf(owner, 100L);
        given(postRepository.findById(100L)).willReturn(Optional.of(post));
        given(userRepository.findById(1L)).willReturn(Optional.of(owner));
        given(postImageRegistry.attach("/images/new.png", owner, post))
                .willReturn(Optional.of(new PostImageRegistry.MeasuredSize(640, 480)));

        postService.update(100L, new PostUpdateRequestDto("제목", "내용", "/images/new.png", 99999, 1, null), null,
                authOf(owner));

        assertThat(post.getPictureWidth()).isEqualTo(640);
        assertThat(post.getPictureHeight()).isEqualTo(480);
    }

    @Test
    @DisplayName("update: picture가 기존과 같으면 삭제를 예약하지 않는다")
    void update_whenPictureUnchanged_doesNotScheduleDeletion() {
        User owner = userWithEmail("owner@example.com", 1L);
        Post post = postOf(owner, 100L);
        ReflectionTestUtils.setField(post, "picture", "/images/same.png");
        given(postRepository.findById(100L)).willReturn(Optional.of(post));
        given(userRepository.findById(1L)).willReturn(Optional.of(owner));

        postService.update(100L, new PostUpdateRequestDto("새 제목", "새 내용", "/images/same.png", null, null, null), null,
                authOf(owner));

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
        given(userRepository.findById(2L)).willReturn(Optional.of(intruder));

        assertThatThrownBy(() -> postService.update(100L, new PostUpdateRequestDto("해킹", "해킹", null, null, null, null), null,
                authOf(intruder)))
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
        given(userRepository.findById(1L)).willReturn(Optional.of(owner));

        assertThatThrownBy(() -> postService.update(100L, new PostUpdateRequestDto("수정 시도", "내용", null, null, null, null), null,
                authOf(owner)))
                .isInstanceOf(AccessDeniedException.class);

        assertThat(post.getTitle()).isEqualTo("원래 제목");
    }

    @Test
    @DisplayName("update: ROLE_ADMIN이면 작성자가 아니어도 수정할 수 있다")
    void update_whenAdmin_updatesEvenIfNotAuthor() {
        User owner = userWithEmail("owner@example.com", 1L);
        Post post = postOf(owner, 100L);
        User admin = userWithEmail("admin@example.com", 2L, Role.ADMIN);
        given(postRepository.findById(100L)).willReturn(Optional.of(post));
        given(userRepository.findById(2L)).willReturn(Optional.of(admin));

        postService.update(100L, new PostUpdateRequestDto("관리자 수정", "관리자 수정", null, null, null, null), null,
                authOf(admin));

        assertThat(post.getTitle()).isEqualTo("관리자 수정");
    }

    @Test
    @DisplayName("update: 작성자가 없는(user=null) 게시글은 관리자만 수정할 수 있다")
    void update_whenPostHasNoAuthor_throwsAccessDeniedExceptionForNonAdmin() {
        Post post = Post.builder().title("고아 게시글").content("c").user(null).build();
        ReflectionTestUtils.setField(post, "id", 200L);
        User someone = userWithEmail("someone@example.com", 3L);
        given(postRepository.findById(200L)).willReturn(Optional.of(post));
        given(userRepository.findById(3L)).willReturn(Optional.of(someone));

        assertThatThrownBy(() -> postService.update(200L, new PostUpdateRequestDto("x", "y", null, null, null, null), null,
                authOf(someone)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("delete: 작성자가 아니면 AccessDeniedException이고 delete가 호출되지 않는다")
    void delete_whenNotAuthor_throwsAccessDeniedExceptionAndDoesNotDelete() {
        User owner = userWithEmail("owner@example.com", 1L);
        Post post = postOf(owner, 100L);
        given(postRepository.findById(100L)).willReturn(Optional.of(post));

        assertThatThrownBy(() -> postService.delete(100L, authOf(2L, "intruder@example.com", Role.USER)))
                .isInstanceOf(AccessDeniedException.class);

        verify(postRepository, never()).delete(any());
        verify(commentRepository, never()).deleteAllByPostId(any());
        verify(postImageService, never()).deleteIfExists(any());
    }

    @Test
    @DisplayName("delete: 작성자 본인이면 소프트 삭제만 하고 댓글·추천·이미지는 그대로 둔다")
    void delete_whenAuthor_softDeletesAndKeepsChildren() {
        User owner = userWithEmail("owner@example.com", 1L);
        Post post = postOf(owner, 100L);
        given(postRepository.findById(100L)).willReturn(Optional.of(post));
        given(commentRepository.findIdsByPostId(100L)).willReturn(List.of(7L, 8L));
        given(postRepository.softDelete(eq(100L), any())).willReturn(1);

        postService.delete(100L, authOf(owner));

        verify(postRepository).softDelete(eq(100L), any());
        verify(postRepository, never()).delete(any(Post.class));
        verify(commentRepository, never()).deleteAllByPostId(any());
        verify(postLikeRepository, never()).deleteAllByPostId(any());
        verify(postImageRegistry, never()).markPostImagesForDeletion(any());
        // 글이 사라지는 순간 글과 아래 댓글의 대기 신고를 닫는다(A-BE-01).
        verify(eventPublisher).publishEvent(new TargetDeletedEvent(ReportTargetType.POST, List.of(100L)));
        verify(eventPublisher).publishEvent(new TargetDeletedEvent(ReportTargetType.COMMENT, List.of(7L, 8L)));
    }

    @Test
    @DisplayName("delete: 이미 소프트 삭제된 글은 없는 글로 본다")
    void delete_whenAlreadyDeleted_throwsNotFound() {
        User owner = userWithEmail("owner@example.com", 1L);
        Post post = postOf(owner, 100L);
        ReflectionTestUtils.setField(post, "deletedAt", LocalDateTime.now());
        given(postRepository.findById(100L)).willReturn(Optional.of(post));

        assertThatThrownBy(() -> postService.delete(100L, authOf(owner)))
                .isInstanceOf(PostNotFoundException.class);

        verify(postRepository, never()).softDelete(any(), any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("delete: 그 사이 다른 요청이 먼저 지웠으면(UPDATE 0건) 없는 글로 본다")
    void delete_whenSoftDeleteRaced_throwsNotFound() {
        User owner = userWithEmail("owner@example.com", 1L);
        Post post = postOf(owner, 100L);
        given(postRepository.findById(100L)).willReturn(Optional.of(post));
        given(postRepository.softDelete(eq(100L), any())).willReturn(0);

        assertThatThrownBy(() -> postService.delete(100L, authOf(owner)))
                .isInstanceOf(PostNotFoundException.class);

        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("update: 소프트 삭제된 글은 수정할 수 없다")
    void update_whenDeleted_throwsNotFound() {
        User owner = userWithEmail("owner@example.com", 1L);
        Post post = postOf(owner, 100L);
        ReflectionTestUtils.setField(post, "deletedAt", LocalDateTime.now());
        given(postRepository.findById(100L)).willReturn(Optional.of(post));

        assertThatThrownBy(() -> postService.update(100L,
                new PostUpdateRequestDto("제목", "내용", null, null, null, Category.FREE), 0L, authOf(owner)))
                .isInstanceOf(PostNotFoundException.class);
    }

    @Test
    @DisplayName("purge: 보관 기간이 지난 글은 댓글·추천을 먼저 지우고 이미지는 삭제 예약한 뒤 행을 지운다")
    void purge_whenRetentionPassed_deletesChildrenThenPost() {
        User owner = userWithEmail("owner@example.com", 1L);
        Post post = postOf(owner, 100L);
        ReflectionTestUtils.setField(post, "deletedAt", LocalDateTime.now().minusDays(31));
        given(postRepository.findByIdForPurge(100L)).willReturn(Optional.of(post));
        given(postImageRegistry.markPostImagesForDeletion(100L)).willReturn(List.of(55L));

        boolean purged = postService.purge(100L, LocalDateTime.now().minusDays(30));

        assertThat(purged).isTrue();
        // 예약이 post_id를 비워야 게시글 DELETE가 FK에 걸리지 않고, 댓글·추천이 남아 있어도 걸린다.
        var inOrder = org.mockito.Mockito.inOrder(postImageRegistry, commentRepository, postLikeRepository, postRepository);
        inOrder.verify(postImageRegistry).markPostImagesForDeletion(100L);
        inOrder.verify(commentRepository).deleteRepliesByPostId(100L);
        inOrder.verify(commentRepository).deleteAllByPostId(100L);
        inOrder.verify(postLikeRepository).deleteAllByPostId(100L);
        inOrder.verify(postRepository).delete(post);
        // 트랜잭션 밖에서 호출했으므로 AfterCommit이 즉시 실행된다. 이번 호출이 표시한 id만 넘긴다.
        verify(postImageCleaner).cleanPendingDeletionsFor(List.of(55L));
        // 신고는 소프트 삭제 때 이미 닫았다.
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("purge: 삭제된 적 없거나 보관 기간 안이면(복구 경쟁 포함) 건드리지 않는다")
    void purge_whenNotDeletedOrWithinRetention_doesNothing() {
        User owner = userWithEmail("owner@example.com", 1L);
        Post visible = postOf(owner, 100L);
        Post recent = postOf(owner, 101L);
        ReflectionTestUtils.setField(recent, "deletedAt", LocalDateTime.now().minusDays(1));
        given(postRepository.findByIdForPurge(100L)).willReturn(Optional.of(visible));
        given(postRepository.findByIdForPurge(101L)).willReturn(Optional.of(recent));
        given(postRepository.findByIdForPurge(102L)).willReturn(Optional.empty());

        LocalDateTime threshold = LocalDateTime.now().minusDays(30);
        assertThat(postService.purge(100L, threshold)).isFalse();
        assertThat(postService.purge(101L, threshold)).isFalse();
        assertThat(postService.purge(102L, threshold)).isFalse();

        verify(postRepository, never()).delete(any(Post.class));
        verify(postImageRegistry, never()).markPostImagesForDeletion(any());
    }

    @Test
    @DisplayName("findById: 존재하지 않는 ID면 IllegalArgumentException")
    void findById_whenNotFound_throwsIllegalArgumentException() {
        given(postRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> postQueryService.findById(999L))
                .isInstanceOf(NotFoundException.class)
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
        given(postRepository.search(null, null, false, idDescOf(pageable))).willReturn(page);

        PostsPageResponseDto result = postQueryService.findAllDesc(pageable);

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
        Slice<PostRowDto> slice = new SliceImpl<>(List.of(row), pageable, false);
        given(postRepository.searchWithoutCount("공지", Category.NOTICE, false, idDescOf(pageable))).willReturn(slice);
        given(commentRepository.countByPostIdIn(List.of(1L))).willReturn(Map.of(1L, 3L));

        PostsPageResponseDto result = postQueryService.findAllDesc(pageable, "공지", Category.NOTICE);

        assertThat(result.content().get(0).commentCount()).isEqualTo(3L);
    }

    /**
     * BE-08: 검색어가 있으면 COUNT를 세지 않는다(LIKE '%kw%'는 인덱스를 못 타서 COUNT가 항상 전체
     * 스캔이다). 전체 건수는 null이고 다음 페이지 유무만 안다.
     */
    @Test
    @DisplayName("findAllDesc: 검색어가 있으면 COUNT 없는 쿼리를 쓰고 전체 건수·페이지 수는 null이다")
    void findAllDesc_withKeyword_usesCountlessQueryAndLeavesTotalsNull() {
        User owner = userWithEmail("owner@example.com", 1L);
        Pageable pageable = PageRequest.of(1, 10);
        Slice<PostRowDto> slice = new SliceImpl<>(List.of(rowOf(owner, 1L)), pageable, true);
        given(postRepository.searchWithoutCount("공지", null, false, idDescOf(pageable))).willReturn(slice);

        PostsPageResponseDto result = postQueryService.findAllDesc(pageable, "공지", null);

        assertThat(result.totalElements()).isNull();
        assertThat(result.totalPages()).isNull();
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.first()).isFalse();
        assertThat(result.last()).as("다음 페이지가 있으므로 last=false").isFalse();
        verify(postRepository, never()).search(any(), any(), anyBoolean(), any());
    }

    @Test
    @DisplayName("findAllDesc: 검색어가 없으면 기존처럼 COUNT를 포함한 쿼리를 쓰고 전체 건수가 채워진다")
    void findAllDesc_withoutKeyword_usesCountingQuery() {
        Pageable pageable = PageRequest.of(0, 10);
        given(postRepository.search(null, null, false, idDescOf(pageable)))
                .willReturn(new PageImpl<>(List.of(), pageable, 0));

        PostsPageResponseDto result = postQueryService.findAllDesc(pageable);

        assertThat(result.totalElements()).isZero();
        assertThat(result.totalPages()).isZero();
        verify(postRepository, never()).searchWithoutCount(any(), any(), anyBoolean(), any());
    }

    @Test
    @DisplayName("findAllDesc: 검색어가 공백뿐이면 null로 정규화해 리포지토리에 전달한다")
    void findAllDesc_normalizesBlankKeywordToNull() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<PostRowDto> page = new PageImpl<>(List.of(), pageable, 0);
        given(postRepository.search(null, null, false, idDescOf(pageable))).willReturn(page);

        postQueryService.findAllDesc(pageable, "   ", null);

        verify(postRepository).search(null, null, false, idDescOf(pageable));
    }

    @Test
    @DisplayName("findAllDesc: 100자를 넘는 검색어는 100자로 잘라 리포지토리에 전달한다")
    void findAllDesc_truncatesKeywordLongerThan100Characters() {
        Pageable pageable = PageRequest.of(0, 10);
        String tooLong = "가".repeat(150);
        String truncated = "가".repeat(100);
        Slice<PostRowDto> slice = new SliceImpl<>(List.of(), pageable, false);
        given(postRepository.searchWithoutCount(truncated, null, false, idDescOf(pageable))).willReturn(slice);

        postQueryService.findAllDesc(pageable, tooLong, null);

        verify(postRepository).searchWithoutCount(truncated, null, false, idDescOf(pageable));
    }

    /**
     * A-BE-02 1단계: 이스케이프하지 않으면 사용자가 입력한 {@code %}·{@code _}가 그대로
     * LIKE 와일드카드로 해석된다 — {@code q=%}는 전체 목록과 같아지고 {@code q=_}는 모든
     * 글과 일치한다. PostRepository.search의 {@code ESCAPE '\'}와 짝을 이룬다.
     */
    @Test
    @DisplayName("findAllDesc: 검색어의 %·_·\\는 리포지토리에 전달하기 전에 이스케이프한다")
    void findAllDesc_escapesLikeWildcardsInKeyword() {
        Pageable pageable = PageRequest.of(0, 10);
        Slice<PostRowDto> slice = new SliceImpl<>(List.of(), pageable, false);
        given(postRepository.searchWithoutCount("100\\%\\_할인\\\\", null, false, idDescOf(pageable))).willReturn(slice);

        postQueryService.findAllDesc(pageable, "100%_할인\\", null);

        verify(postRepository).searchWithoutCount("100\\%\\_할인\\\\", null, false, idDescOf(pageable));
    }

    /**
     * A-BE-02 1단계: 1글자 검색어는 선행 와일드카드 LIKE에서 사실상 전체 스캔과 같은 대량의
     * 행을 매치시킨다 — 검색어가 없는 것으로 보고 전체 목록을 보여준다.
     */
    @Test
    @DisplayName("findAllDesc: 2자 미만 검색어는 null로 정규화해 전체 목록을 보여준다")
    void findAllDesc_normalizesTooShortKeywordToNull() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<PostRowDto> page = new PageImpl<>(List.of(), pageable, 0);
        given(postRepository.search(null, null, false, idDescOf(pageable))).willReturn(page);

        postQueryService.findAllDesc(pageable, "a", null);

        verify(postRepository).search(null, null, false, idDescOf(pageable));
    }

    @Test
    @DisplayName("findAllDesc: 정확히 2자인 검색어는 그대로 전달한다(경계값)")
    void findAllDesc_keepsExactlyTwoCharacterKeyword() {
        Pageable pageable = PageRequest.of(0, 10);
        Slice<PostRowDto> slice = new SliceImpl<>(List.of(), pageable, false);
        given(postRepository.searchWithoutCount("ab", null, false, idDescOf(pageable))).willReturn(slice);

        postQueryService.findAllDesc(pageable, "ab", null);

        verify(postRepository).searchWithoutCount("ab", null, false, idDescOf(pageable));
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
        given(postRepository.search(null, null, false, expectedEffective)).willReturn(page);

        postQueryService.findAllDesc(requested);

        verify(postRepository).search(null, null, false, expectedEffective);
    }

    @Test
    @DisplayName("findPopular: 조회수 상위 N개를 반환한다 (댓글 수는 화면에 쓰이지 않아 집계하지 않는다)")
    void findPopular_returnsTopPostsWithoutCommentCountAggregation() {
        User owner = userWithEmail("owner@example.com", 1L);
        PostRowDto row = rowOf(owner, 1L);
        given(postRepository.findTopByViewCountDesc(any(), eq(PageRequest.of(0, 5)))).willReturn(List.of(row));

        List<PostsListResponseDto> result = postQueryService.findPopular(5);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).commentCount()).isZero();
        verify(commentRepository, never()).countByPostIdIn(any());
    }

    @Test
    @DisplayName("findPinned: 고정된 글을 댓글 수와 함께 반환한다(A-BE-05)")
    void findPinned_returnsPinnedPostsWithCommentCounts() {
        User owner = userWithEmail("owner@example.com", 1L);
        PostRowDto row = rowOf(owner, 1L);
        given(postRepository.findPinned(any(LocalDateTime.class), eq(PageRequest.of(0, 5)))).willReturn(List.of(row));
        given(commentRepository.countByPostIdIn(List.of(1L))).willReturn(Map.of(1L, 3L));

        List<PostsListResponseDto> result = postQueryService.findPinned(5);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).commentCount()).isEqualTo(3L);
    }

    @Test
    @DisplayName("findRelated: 분류·제외할 id·limit을 그대로 리포지토리에 넘긴다")
    void findRelated_delegatesToRepositoryWithCategoryExcludeIdAndLimit() {
        User owner = userWithEmail("owner@example.com", 1L);
        PostRowDto row = rowOf(owner, 2L);
        given(postRepository.findRelated(Category.FREE, 1L, PageRequest.of(0, 5))).willReturn(List.of(row));

        List<PostRowDto> result = postQueryService.findRelated(Category.FREE, 1L, 5);

        assertThat(result).containsExactly(row);
    }

    @Test
    @DisplayName("findByIdForView: 조회수는 엔티티를 건드리지 않고 원자적 UPDATE로 올리고, 추천 정보를 함께 담는다")
    void findByIdForView_increasesViewCountAtomicallyAndIncludesLikeInformation() {
        User owner = userWithEmail("owner@example.com", 1L);
        Post post = postOf(owner, 100L);
        given(postRepository.findByIdWithUser(100L)).willReturn(Optional.of(post));
        given(postLikeRepository.summarize(100L, 1L)).willReturn(likeSummary(3L, 1L));

        var result = postQueryService.findByIdForView(100L, authOf(owner));

        // 조회수 증가는 전용 UPDATE 한 문장이다. 엔티티를 바꿔 변경 감지에 맡기면 제목·본문까지
        // 함께 UPDATE에 실려 겹친 편집을 되돌린다(F02). 실제 증가분은 PostViewCountIsolationTest가
        // 진짜 DB로 검증한다 — mock 리포지토리로는 관찰할 수 없는 지점이다.
        var inOrder = org.mockito.Mockito.inOrder(postRepository);
        inOrder.verify(postRepository).increaseViewCount(100L);
        inOrder.verify(postRepository).findByIdWithUser(100L);
        assertThat(post.getViewCount()).isZero();

        assertThat(result.likeCount()).isEqualTo(3L);
        assertThat(result.likedByMe()).isTrue();
    }

    @Test
    @DisplayName("findRecent: 앞쪽 N개만 읽고 Page(COUNT)를 거치지 않으며 댓글 수를 한 번에 묶어 담는다")
    void findRecent_readsTopRowsWithoutCountAndAttachesCommentCounts() {
        User owner = userWithEmail("owner@example.com", 1L);
        PostRowDto row = rowOf(owner, 1L);
        given(postRepository.findRecent(org.springframework.data.domain.PageRequest.of(0, 5))).willReturn(List.of(row));
        given(commentRepository.countByPostIdIn(List.of(1L))).willReturn(Map.of(1L, 3L));

        var result = postQueryService.findRecent(5);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).commentCount()).isEqualTo(3L);
        verify(postRepository, never()).search(any(), any(), anyBoolean(), any());
    }

    @Test
    @DisplayName("findByIdForView: 소프트 삭제된 글은 작성자를 포함한 일반 사용자에게 없는 글이다")
    void findByIdForView_whenDeletedAndNotAdmin_throwsNotFound() {
        User owner = userWithEmail("owner@example.com", 1L);
        Post post = postOf(owner, 100L);
        ReflectionTestUtils.setField(post, "deletedAt", LocalDateTime.now());
        given(postRepository.findByIdWithUser(100L)).willReturn(Optional.of(post));

        assertThatThrownBy(() -> postQueryService.findByIdForView(100L, authOf(owner)))
                .isInstanceOf(PostNotFoundException.class);
        assertThatThrownBy(() -> postQueryService.findByIdForView(100L, null))
                .isInstanceOf(PostNotFoundException.class);
    }

    @Test
    @DisplayName("findByIdForView: 고정 중인 글은 기한을, 기한이 지난 글은 null을 내려준다")
    void findByIdForView_exposesPinnedUntilOnlyWhileActive() {
        User owner = userWithEmail("owner@example.com", 1L);
        Post active = postOf(owner, 100L);
        Post expired = postOf(owner, 101L);
        LocalDateTime until = LocalDateTime.now().plusDays(2).withNano(0);
        ReflectionTestUtils.setField(active, "pinnedUntil", until);
        ReflectionTestUtils.setField(expired, "pinnedUntil", LocalDateTime.now().minusMinutes(1));
        given(postRepository.findByIdWithUser(100L)).willReturn(Optional.of(active));
        given(postRepository.findByIdWithUser(101L)).willReturn(Optional.of(expired));
        given(postLikeRepository.summarize(any(), any())).willReturn(likeSummary(0L, 0L));

        var activeView = postQueryService.findByIdForView(100L, null);
        var expiredView = postQueryService.findByIdForView(101L, null);

        // 서버 시간대(KST) 오프셋을 실어 보낸다 — 오프셋이 없으면 브라우저가 자기 시간대로 해석한다.
        assertThat(activeView.pinnedUntil().toLocalDateTime()).isEqualTo(until);
        assertThat(activeView.pinnedUntil().getOffset().getTotalSeconds()).isEqualTo(9 * 3600);
        assertThat(expiredView.pinnedUntil()).isNull();
    }

    @Test
    @DisplayName("findByIdForView: 관리자는 삭제된 글을 열 수 있고 수정·삭제 버튼 대신 삭제됨 표시를 받는다")
    void findByIdForView_whenDeletedAndAdmin_showsDeletedWithoutManageButtons() {
        User owner = userWithEmail("owner@example.com", 1L);
        User admin = userWithEmail("admin@example.com", 9L, Role.ADMIN);
        Post post = postOf(owner, 100L);
        ReflectionTestUtils.setField(post, "deletedAt", LocalDateTime.now());
        given(postRepository.findByIdWithUser(100L)).willReturn(Optional.of(post));
        given(postLikeRepository.summarize(100L, 9L)).willReturn(likeSummary(0L, 0L));

        var result = postQueryService.findByIdForView(100L, authOf(admin));

        assertThat(result.deleted()).isTrue();
        assertThat(result.canModerate()).isTrue();
        assertThat(result.canManagePost()).isFalse();
    }

    @Test
    @DisplayName("findById(공개 REST): 소프트 삭제된 글은 돌려주지 않는다")
    void findById_whenDeleted_throwsNotFound() {
        Post post = postOf(userWithEmail("owner@example.com", 1L), 100L);
        ReflectionTestUtils.setField(post, "deletedAt", LocalDateTime.now());
        given(postRepository.findById(100L)).willReturn(Optional.of(post));

        assertThatThrownBy(() -> postQueryService.findById(100L)).isInstanceOf(PostNotFoundException.class);
    }

    @Test
    @DisplayName("findByIdForView: 관리자가 숨긴 글은 작성자를 포함한 일반 사용자에게 열리지 않는다(PostHiddenException)")
    void findByIdForView_whenBlindedAndNotAdmin_throwsHidden() {
        User owner = userWithEmail("owner@example.com", 1L);
        Post post = postOf(owner, 100L);
        ReflectionTestUtils.setField(post, "blindedAt", LocalDateTime.now());
        given(postRepository.findByIdWithUser(100L)).willReturn(Optional.of(post));

        assertThatThrownBy(() -> postQueryService.findByIdForView(100L, authOf(owner)))
                .isInstanceOf(PostHiddenException.class)
                // 상태 코드는 없는 글과 같다 — 화면·REST 모두 PostNotFoundException 처리를 따른다.
                .isInstanceOf(PostNotFoundException.class);
        assertThatThrownBy(() -> postQueryService.findByIdForView(100L, null))
                .isInstanceOf(PostHiddenException.class);
    }

    @Test
    @DisplayName("findByIdForView: 관리자는 숨긴 글을 열 수 있고 blinded 표시와 관리 권한을 받는다")
    void findByIdForView_whenBlindedAndAdmin_showsBlinded() {
        User owner = userWithEmail("owner@example.com", 1L);
        User admin = userWithEmail("admin@example.com", 9L, Role.ADMIN);
        Post post = postOf(owner, 100L);
        ReflectionTestUtils.setField(post, "blindedAt", LocalDateTime.now());
        given(postRepository.findByIdWithUser(100L)).willReturn(Optional.of(post));
        given(postLikeRepository.summarize(100L, 9L)).willReturn(likeSummary(0L, 0L));

        var result = postQueryService.findByIdForView(100L, authOf(admin));

        assertThat(result.blinded()).isTrue();
        assertThat(result.deleted()).isFalse();
        assertThat(result.canModerate()).isTrue();
        // 관리자는 숨긴 글도 고치거나 지울 수 있다.
        assertThat(result.canManagePost()).isTrue();
    }

    @Test
    @DisplayName("findById(공개 REST): 숨긴 글은 돌려주지 않는다")
    void findById_whenBlinded_throwsNotFound() {
        Post post = postOf(userWithEmail("owner@example.com", 1L), 100L);
        ReflectionTestUtils.setField(post, "blindedAt", LocalDateTime.now());
        given(postRepository.findById(100L)).willReturn(Optional.of(post));

        assertThatThrownBy(() -> postQueryService.findById(100L)).isInstanceOf(PostNotFoundException.class);
    }

    @Test
    @DisplayName("update·delete: 숨긴 글은 작성자도 고치거나 지울 수 없다(403) — 신고된 내용을 없애는 일을 막는다")
    void updateAndDelete_whenBlindedAndAuthor_areForbidden() {
        User owner = userWithEmail("owner@example.com", 1L);
        Post post = postOf(owner, 100L);
        ReflectionTestUtils.setField(post, "blindedAt", LocalDateTime.now());
        given(postRepository.findById(100L)).willReturn(Optional.of(post));
        given(userRepository.findById(1L)).willReturn(Optional.of(owner));

        assertThatThrownBy(() -> postService.update(100L,
                new PostUpdateRequestDto("제목", "내용", null, null, null, Category.FREE), 0L, authOf(owner)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> postService.delete(100L, authOf(owner)))
                .isInstanceOf(AccessDeniedException.class);

        verify(postRepository, never()).softDelete(any(), any());
    }

    @Test
    @DisplayName("delete: 관리자는 숨긴 글도 지울 수 있다")
    void delete_whenBlindedAndAdmin_softDeletes() {
        User owner = userWithEmail("owner@example.com", 1L);
        User admin = userWithEmail("admin@example.com", 9L, Role.ADMIN);
        Post post = postOf(owner, 100L);
        ReflectionTestUtils.setField(post, "blindedAt", LocalDateTime.now());
        given(postRepository.findById(100L)).willReturn(Optional.of(post));
        given(postRepository.softDelete(eq(100L), any())).willReturn(1);

        postService.delete(100L, authOf(admin));

        verify(postRepository).softDelete(eq(100L), any());
    }

    @Test
    @DisplayName("setLike: 숨긴 글에는 추천할 수 없다 — 열 수 없는 글이다")
    void setLike_whenBlinded_throwsHidden() {
        User viewer = userWithEmail("viewer@example.com", 2L);
        Post post = postOf(userWithEmail("owner@example.com", 1L), 100L);
        ReflectionTestUtils.setField(post, "blindedAt", LocalDateTime.now());
        given(postRepository.findById(100L)).willReturn(Optional.of(post));

        assertThatThrownBy(() -> postService.setLike(100L, true, authOf(viewer)))
                .isInstanceOf(PostHiddenException.class);

        verify(postLikeWriter, never()).insert(any(), any());
    }

    @Test
    @DisplayName("findByIdForView: 익명이면 likedByMe는 항상 false다")
    void findByIdForView_whenAnonymous_likedByMeIsFalse() {
        User owner = userWithEmail("owner@example.com", 1L);
        Post post = postOf(owner, 100L);
        given(postRepository.findByIdWithUser(100L)).willReturn(Optional.of(post));
        given(postLikeRepository.summarize(100L, PostLikeRepository.NO_USER_ID)).willReturn(likeSummary(0L, 0L));

        var result = postQueryService.findByIdForView(100L, null);

        assertThat(result.likedByMe()).isFalse();
        // 익명은 어떤 회원 id와도 일치하지 않는 값으로 한 번만 센다(BE-05).
        verify(postLikeRepository).summarize(100L, PostLikeRepository.NO_USER_ID);
        verify(postLikeRepository, never()).existsByPostIdAndUserId(any(), any());
    }

    @Test
    @DisplayName("findByIdForView: 추천 수와 내가 눌렀는지를 쿼리 하나(summarize)로 구한다")
    void findByIdForView_countsLikesWithSingleQuery() {
        User owner = userWithEmail("owner@example.com", 1L);
        Post post = postOf(owner, 100L);
        given(postRepository.findByIdWithUser(100L)).willReturn(Optional.of(post));
        given(postLikeRepository.summarize(100L, 1L)).willReturn(likeSummary(2L, 0L));

        var result = postQueryService.findByIdForView(100L, authOf(owner));

        assertThat(result.likeCount()).isEqualTo(2L);
        assertThat(result.likedByMe()).isFalse();
        verify(postLikeRepository, never()).countByPostId(any());
        verify(postLikeRepository, never()).existsByPostIdAndUserId(any(), any());
    }

    @Test
    @DisplayName("findByIdForView(countView=false): A-BE-04 중복 방문 판정에 따라 조회수를 올리지 않는다")
    void findByIdForView_whenCountViewFalse_doesNotIncreaseViewCount() {
        User owner = userWithEmail("owner@example.com", 1L);
        Post post = postOf(owner, 100L);
        given(postRepository.findByIdWithUser(100L)).willReturn(Optional.of(post));
        given(postLikeRepository.summarize(100L, PostLikeRepository.NO_USER_ID)).willReturn(likeSummary(0L, 0L));

        postQueryService.findByIdForView(100L, null, false);

        verify(postRepository, never()).increaseViewCount(any());
    }

    @Test
    @DisplayName("setLike(true): 아직 안 눌렀으면 추천을 추가하고 liked=true를 반환한다")
    void setLike_toTrueWhenNotLikedYet_addsLikeAndReturnsLikedTrue() {
        User user = userWithEmail("liker@example.com", 2L);
        Post post = postOf(user, 100L);
        given(postRepository.findById(100L)).willReturn(Optional.of(post));
        given(userRepository.findById(2L)).willReturn(Optional.of(user));
        given(postLikeRepository.existsByPostIdAndUserId(100L, 2L)).willReturn(false);
        given(postLikeWriter.countByPostId(100L)).willReturn(1L);

        var result = postService.setLike(100L, true, authOf(user));

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
        given(userRepository.findById(2L)).willReturn(Optional.of(user));
        given(postLikeWriter.countByPostId(100L)).willReturn(0L);

        var result = postService.setLike(100L, false, authOf(user));

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
        given(userRepository.findById(2L)).willReturn(Optional.of(user));
        given(postLikeRepository.existsByPostIdAndUserId(100L, 2L)).willReturn(true);
        given(postLikeWriter.countByPostId(100L)).willReturn(1L);

        var result = postService.setLike(100L, true, authOf(user));

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
        given(userRepository.findById(2L)).willReturn(Optional.of(user));
        given(postLikeRepository.existsByPostIdAndUserId(100L, 2L)).willReturn(false);
        DataIntegrityViolationException fkViolation = new DataIntegrityViolationException("FK_POST_LIKES_POST");
        org.mockito.BDDMockito.willThrow(fkViolation).given(postLikeWriter).insert(post, user);
        given(postLikeWriter.isDuplicateLikeConstraint(fkViolation)).willReturn(false);

        assertThatThrownBy(() -> postService.setLike(100L, true, authOf(user)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("B09: 검사와 INSERT 사이에 같은 추천이 들어와도(유니크 제약 위반) 성공으로 처리한다")
    void setLike_whenConcurrentInsertWins_treatsAsSuccess() {
        User user = userWithEmail("liker@example.com", 2L);
        Post post = postOf(user, 100L);
        given(postRepository.findById(100L)).willReturn(Optional.of(post));
        given(userRepository.findById(2L)).willReturn(Optional.of(user));
        given(postLikeRepository.existsByPostIdAndUserId(100L, 2L)).willReturn(false);
        given(postLikeWriter.countByPostId(100L)).willReturn(1L);
        DataIntegrityViolationException duplicate = new DataIntegrityViolationException("UK_POST_LIKE_POST_USER");
        org.mockito.BDDMockito.willThrow(duplicate).given(postLikeWriter).insert(post, user);
        given(postLikeWriter.isDuplicateLikeConstraint(duplicate)).willReturn(true);

        var result = postService.setLike(100L, true, authOf(user));

        assertThat(result.liked()).isTrue();
        assertThat(result.likeCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("save: 일반 사용자는 NOTICE 분류로 글을 쓸 수 없다")
    void save_whenUserUsesNoticeCategory_throwsAccessDeniedException() {
        User user = userWithEmail("tester@example.com", 1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        assertThatThrownBy(() -> postService.save(authOf(user),
                new PostSaveRequestDto("공지", "내용", null, null, null, Category.NOTICE)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("공지 분류는 관리자만");

        verify(postRepository, never()).save(any());
    }

    @Test
    @DisplayName("save: 관리자는 NOTICE 분류로 글을 쓸 수 있다")
    void save_whenAdminUsesNoticeCategory_saves() {
        User admin = userWithEmail("admin@example.com", 1L, Role.ADMIN);
        given(userRepository.findById(1L)).willReturn(Optional.of(admin));
        given(postRepository.save(any(Post.class))).willReturn(postOf(admin, 10L));

        Long id = postService.save(authOf(admin),
                new PostSaveRequestDto("공지", "내용", null, null, null, Category.NOTICE));

        assertThat(id).isEqualTo(10L);
    }

    @Test
    @DisplayName("update: 일반 사용자는 자기 글이어도 NOTICE로 바꿀 수 없다")
    void update_whenUserSwitchesToNoticeCategory_throwsAccessDeniedException() {
        User owner = userWithEmail("owner@example.com", 1L);
        Post post = postOf(owner, 100L);
        given(postRepository.findById(100L)).willReturn(Optional.of(post));
        given(userRepository.findById(1L)).willReturn(Optional.of(owner));

        assertThatThrownBy(() -> postService.update(100L,
                new PostUpdateRequestDto("제목", "내용", null, null, null, Category.NOTICE), null,
                authOf(owner)))
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
        given(userRepository.findById(1L)).willReturn(Optional.of(owner));

        assertThatThrownBy(() -> postService.update(100L,
                new PostUpdateRequestDto("나중 저장", "내용", null, null, null, null), 1L,
                authOf(owner)))
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
        given(userRepository.findById(1L)).willReturn(Optional.of(owner));

        postService.update(100L, new PostUpdateRequestDto("수정됨", "내용", null, null, null, null), null,
                authOf(owner));

        assertThat(post.getTitle()).isEqualTo("수정됨");
    }
}
