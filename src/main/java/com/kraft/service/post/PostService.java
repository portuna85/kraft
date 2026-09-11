package com.kraft.service.post;

import com.kraft.domain.comment.CommentRepository;
import com.kraft.domain.post.Category;
import com.kraft.domain.post.Post;
import com.kraft.domain.post.PostLike;
import com.kraft.domain.post.PostLikeRepository;
import com.kraft.domain.post.PostRepository;
import com.kraft.domain.user.EmailHasher;
import com.kraft.domain.user.User;
import com.kraft.domain.user.UserRepository;
import com.kraft.service.support.OwnershipPolicy;
import com.kraft.service.support.WriteAccessPolicy;
import com.kraft.web.dto.post.PostLikeResponseDto;
import com.kraft.web.dto.post.PostResponseDto;
import com.kraft.web.dto.post.PostSaveRequestDto;
import com.kraft.web.dto.post.PostUpdateRequestDto;
import com.kraft.web.dto.post.PostViewDto;
import com.kraft.web.dto.post.PostsListResponseDto;
import com.kraft.web.dto.post.PostsPageResponseDto;
import com.kraft.web.exception.PostNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@Service
@Transactional(readOnly = true)
public class PostService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final CommentRepository commentRepository;
    private final PostImageService postImageService;
    private final PostLikeRepository postLikeRepository;

    @Transactional
    public Long save(String email, PostSaveRequestDto requestDto) {
        User user = userRepository.findByEmailHash(EmailHasher.sha512Hex(email))
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다. email=" + email));
        WriteAccessPolicy.requireVerified(user);
        return postRepository.save(requestDto.toEntity(user)).getId();
    }

    @Transactional
    public Long update(Long id, PostUpdateRequestDto requestDto, Authentication authentication) {
        Post post = findPost(id);
        validateOwner(post, authentication);
        String oldPicture = post.getPicture();
        post.update(requestDto.title(), requestDto.content(), requestDto.picture(), requestDto.category());
        if (oldPicture != null && !oldPicture.equals(requestDto.picture())) {
            postImageService.deleteIfExists(oldPicture);
        }
        return id;
    }

    @Transactional
    public void delete(Long id, Authentication authentication) {
        Post post = findPost(id);
        validateOwner(post, authentication);
        // 댓글·추천이 남아 있으면 FK 제약 위반으로 삭제가 실패하므로 먼저 지운다.
        commentRepository.deleteAllByPostId(id);
        postLikeRepository.deleteAllByPostId(id);
        postRepository.delete(post);
        postImageService.deleteIfExists(post.getPicture());
    }

    public PostResponseDto findById(Long id) {
        return new PostResponseDto(findPost(id));
    }

    /**
     * 상세 화면용 조회. 인증 객체와 엔티티를 함께 볼 수 있는 이 지점에서 관리 권한을 계산해
     * 화면 전용 DTO로 내려준다. 화면이 작성자 이름과 로그인 이메일을 비교하는 방식(잘못된
     * 소유권 추정)을 쓰지 않게 하려는 것이다. 공개 REST DTO는 그대로 둔다.
     * <p>
     * 상세 화면을 열 때마다 조회수를 1 늘린다(새로고침·중복 방문에 대한 별도 방지 로직은
     * 없음 — 단순한 카운터다). 쓰기 작업이라 클래스 기본값(readOnly)을 오버라이드한다.
     */
    @Transactional
    public PostViewDto findByIdForView(Long id, Authentication authentication) {
        Post post = findPost(id);
        post.increaseViewCount();
        Long userId = currentUserId(authentication);
        boolean likedByMe = userId != null && postLikeRepository.existsByPostIdAndUserId(id, userId);
        long likeCount = postLikeRepository.countByPostId(id);
        return new PostViewDto(post, OwnershipPolicy.canManage(authentication, post.getUser()), likeCount, likedByMe);
    }

    public PostsPageResponseDto findAllDesc(Pageable pageable) {
        return findAllDesc(pageable, null, null);
    }

    /**
     * 검색어·분류로 목록을 좁힌다. 두 조건 모두 없으면 전체 목록을 최신순으로 반환한다.
     * 목록에 필요한 댓글 수는 게시글마다 따로 조회하지 않고, 이 페이지에 담긴 게시글
     * ID로 한 번에 묶어 조회한다(N+1 방지).
     */
    public PostsPageResponseDto findAllDesc(Pageable pageable, String keyword, Category category) {
        Page<Post> page = postRepository.search(normalize(keyword), category, pageable);
        Map<Long, Long> commentCounts = commentRepository.countByPostIdIn(
                page.getContent().stream().map(Post::getId).toList());
        Page<PostsListResponseDto> mapped = page.map(post ->
                new PostsListResponseDto(post, commentCounts.getOrDefault(post.getId(), 0L)));
        return new PostsPageResponseDto(mapped);
    }

    /**
     * 조회수 기준 상위 {@code limit}개(인기글). 목록 화면 상단의 별도 섹션에 쓰인다.
     */
    public List<PostsListResponseDto> findPopular(int limit) {
        List<Post> posts = postRepository.findTopByViewCountDesc(PageRequest.of(0, limit));
        Map<Long, Long> commentCounts = commentRepository.countByPostIdIn(
                posts.stream().map(Post::getId).toList());
        return posts.stream()
                .map(post -> new PostsListResponseDto(post, commentCounts.getOrDefault(post.getId(), 0L)))
                .toList();
    }

    /**
     * 게시글 추천을 토글한다(이미 눌렀으면 취소). 이 엔드포인트는 항상 인증된 사용자만
     * 호출할 수 있으므로(SecurityConfig의 {@code /api/v1/**} authenticated() 규칙) 익명
     * 처리를 따로 두지 않는다.
     */
    @Transactional
    public PostLikeResponseDto toggleLike(Long id, Authentication authentication) {
        Post post = findPost(id);
        User user = userRepository.findByEmailHash(EmailHasher.sha512Hex(authentication.getName()))
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다. email=" + authentication.getName()));

        boolean alreadyLiked = postLikeRepository.existsByPostIdAndUserId(id, user.getId());
        if (alreadyLiked) {
            postLikeRepository.deleteByPostIdAndUserId(id, user.getId());
        } else {
            postLikeRepository.save(PostLike.builder().post(post).user(user).build());
        }

        return new PostLikeResponseDto(!alreadyLiked, postLikeRepository.countByPostId(id));
    }

    private String normalize(String keyword) {
        return (keyword == null || keyword.isBlank()) ? null : keyword.trim();
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return userRepository.findByEmailHash(EmailHasher.sha512Hex(authentication.getName()))
                .map(User::getId)
                .orElse(null);
    }

    private Post findPost(Long id) {
        return postRepository.findById(id)
                .orElseThrow(() -> new PostNotFoundException(id));
    }

    /**
     * 작성자 본인 또는 관리자만 게시글을 수정·삭제할 수 있다.
     */
    private void validateOwner(Post post, Authentication authentication) {
        OwnershipPolicy.validateOwner(authentication, post.getUser(), post.getId());
    }
}
