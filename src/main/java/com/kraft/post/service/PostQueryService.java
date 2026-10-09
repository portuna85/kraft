package com.kraft.post.service;

import com.kraft.comment.domain.CommentRepository;
import com.kraft.post.domain.Category;
import com.kraft.post.domain.Post;
import com.kraft.post.domain.PostHiddenException;
import com.kraft.post.domain.PostLikeRepository;
import com.kraft.post.domain.PostNotFoundException;
import com.kraft.post.domain.PostRepository;
import com.kraft.post.dto.PostResponseDto;
import com.kraft.post.dto.PostRowDto;
import com.kraft.post.dto.PostsListResponseDto;
import com.kraft.post.dto.PostsPageResponseDto;
import com.kraft.post.dto.PostViewDto;
import com.kraft.shared.security.CurrentUser;
import com.kraft.shared.security.OwnershipPolicy;
import com.kraft.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 게시글 조회 전용 서비스(쓰기는 {@link PostService}). 상세 조회({@link #findByIdForView})만 조회수
 * UPDATE를 함께 하므로 쓰기 트랜잭션으로 연다.
 */
@RequiredArgsConstructor
@Service
@Transactional(readOnly = true)
public class PostQueryService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final CommentRepository commentRepository;
    private final PostLikeRepository postLikeRepository;

    /** 검색어 상한. 지나치게 긴 검색어까지 그대로 LIKE 조건에 실을 이유가 없다. */
    private static final int MAX_KEYWORD_LENGTH = 100;

    /** 검색어 최소 길이. 1글자 LIKE는 사실상 전체 스캔이라, 더 짧으면 검색어가 없는 것으로 본다(오류 아님). */
    private static final int MIN_KEYWORD_LENGTH = 2;


    public PostResponseDto findById(Long id) {
        return new PostResponseDto(findPost(id));
    }

    /** 항상 조회수를 올리는 상세 조회. 컨트롤러는 중복 방문 여부를 넘기는 오버로드를 쓴다. */
    @Transactional
    public PostViewDto findByIdForView(Long id, Authentication authentication) {
        return findByIdForView(id, authentication, true);
    }

    /**
     * 상세 화면용 조회. 관리 권한을 여기서 계산해 화면 DTO에 담는다. 조회수는 엔티티를 읽기 <b>전에</b>
     * 원자적 UPDATE로 올려(변경 감지에 맡기면 겹친 편집을 되돌린다) 화면에 반영되게 한다.
     * {@code countView}가 false면({@code PostViewDedup}가 재방문으로 판정) 올리지 않는다.
     */
    @Transactional
    public PostViewDto findByIdForView(Long id, Authentication authentication, boolean countView) {
        if (countView) {
            postRepository.increaseViewCount(id);
        }

        // 조회수 UPDATE가 영속성 컨텍스트를 비우므로 이 조회는 항상 DB를 다시 읽는다.
        Post post = postRepository.findByIdWithUser(id)
                .orElseThrow(() -> new PostNotFoundException(id));
        // 삭제된 글과 숨긴 글은 관리자만 열 수 있다(숨김은 404지만 안내 문구가 다르다).
        boolean admin = OwnershipPolicy.isAdmin(authentication);
        if (post.isDeleted() && !admin) {
            throw new PostNotFoundException(id);
        }
        if (post.isBlinded() && !admin) {
            throw new PostHiddenException(id);
        }
        Long userId = currentUserId(authentication);
        PostLikeRepository.LikeSummary likes = postLikeRepository.summarize(
                id, userId != null ? userId : PostLikeRepository.NO_USER_ID);
        boolean likedByMe = userId != null && likes.getMine() > 0;
        // 삭제된 글에는 수정·삭제 버튼을 보이지 않는다(할 수 있는 일은 복구뿐).
        boolean canManage = !post.isDeleted() && OwnershipPolicy.canManage(authentication, post.getUser());
        return new PostViewDto(post, canManage, likes.getTotal(), likedByMe, admin);
    }

    public PostsPageResponseDto findAllDesc(Pageable pageable) {
        return findAllDesc(pageable, null, null, false);
    }

    /** 검색 범위를 지정하지 않는 호출은 기본값(제목만)으로 좁힌다. */
    public PostsPageResponseDto findAllDesc(Pageable pageable, String keyword, Category category) {
        return findAllDesc(pageable, keyword, category, false);
    }

    /**
     * 검색어·분류로 목록을 좁힌다(둘 다 없으면 전체 최신순). 댓글 수는 페이지의 글 id로 한 번에 묶어 센다.
     * 정렬은 여기서 {@code PostSortPolicy.effectiveSort}로 보정하고, {@code searchContent}가 false(기본)면
     * 제목만 검색한다 — 본문 LIKE가 가장 비싸다.
     */
    public PostsPageResponseDto findAllDesc(Pageable pageable, String keyword, Category category, boolean searchContent) {
        Pageable effective = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                PostSortPolicy.effectiveSort(pageable.getSort()));
        String normalized = normalize(keyword);
        if (normalized == null) {
            // 검색어 없음: 인덱스로 세는 COUNT가 싸고 화면이 총 건수와 번호 이동을 그린다.
            Page<PostRowDto> page = postRepository.search(null, category, searchContent, effective);
            Map<Long, Long> commentCounts = commentCountsOf(page);
            return new PostsPageResponseDto(page.map(row ->
                    new PostsListResponseDto(row, commentCounts.getOrDefault(row.id(), 0L))));
        }
        // 검색어 있음: LIKE '%kw%'의 COUNT는 전체 스캔이라 세지 않고 다음 페이지 유무만 안다.
        Slice<PostRowDto> slice = postRepository.searchWithoutCount(normalized, category, searchContent, effective);
        Map<Long, Long> commentCounts = commentCountsOf(slice);
        return new PostsPageResponseDto(slice.map(row ->
                new PostsListResponseDto(row, commentCounts.getOrDefault(row.id(), 0L))));
    }

    /** 인기글 후보를 이 기간 이내에 작성된 글로 좁힌다(오래된 글의 독점 방지). */
    private static final Duration POPULAR_WINDOW = Duration.ofDays(7);

    /**
     * 최근 {@link #POPULAR_WINDOW} 글 중 조회수 상위 {@code limit}개. 화면이 댓글 수를 쓰지 않아 세지 않고,
     * 짧게 캐시한다(spring.cache.caffeine.spec — 순위 반영이 그만큼 늦어도 된다).
     */
    @Cacheable("popularPosts")
    public List<PostsListResponseDto> findPopular(int limit) {
        LocalDateTime since = LocalDateTime.now().minus(POPULAR_WINDOW);
        List<PostRowDto> rows = postRepository.findTopByViewCountDesc(since, PageRequest.of(0, limit));
        return rows.stream()
                .map(row -> new PostsListResponseDto(row, 0L))
                .toList();
    }

    /** 동시에 고정할 수 있는 글 수의 상한(서비스도 같은 값을 쓴다). */
    public static final int PINNED_LIMIT = 5;

    /**
     * 목록 첫 페이지 상단에 고정할 글(기한이 남은 것) 최대 {@code limit}개. 일반 행과 같은 모양이라 댓글 수도
     * 담는다. 짧게 캐시하며 글을 쓰거나 고치거나 지우거나 고정·숨김을 바꿀 때 비운다. 기한 만료는 캐시 유효시간
     * 안에 저절로 반영된다.
     */
    @Cacheable("pinnedPosts")
    public List<PostsListResponseDto> findPinned(int limit) {
        List<PostRowDto> rows = postRepository.findPinned(LocalDateTime.now(), PageRequest.of(0, limit));
        Map<Long, Long> commentCounts = commentRepository.countByPostIdIn(rows.stream().map(PostRowDto::id).toList());
        return rows.stream()
                .map(row -> new PostsListResponseDto(row, commentCounts.getOrDefault(row.id(), 0L)))
                .toList();
    }

    /** 상세 화면 하단의 관련 글(같은 분류, 현재 글 제외, 최신순 최대 {@code limit}개). */
    public List<PostRowDto> findRelated(Category category, Long excludeId, int limit) {
        return postRepository.findRelated(category, excludeId, PageRequest.of(0, limit));
    }


    /** 이 페이지에 담긴 글들의 댓글 수를 한 번에 묶어 조회한다(N+1 방지). */
    private Map<Long, Long> commentCountsOf(Slice<PostRowDto> rows) {
        return commentRepository.countByPostIdIn(rows.getContent().stream().map(PostRowDto::id).toList());
    }

    private String normalize(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String trimmed = keyword.trim();
        if (trimmed.length() < MIN_KEYWORD_LENGTH) {
            return null;
        }
        String truncated = trimmed.length() > MAX_KEYWORD_LENGTH ? trimmed.substring(0, MAX_KEYWORD_LENGTH) : trimmed;
        return escapeLikeWildcards(truncated);
    }

    /**
     * 사용자가 입력한 {@code %}·{@code _}·{@code \}를 문자 그대로 매치되게 이스케이프한다
     * ({@code PostRepository.search}의 {@code ESCAPE '\'}와 한 쌍). 역슬래시를 먼저 처리해야 한다.
     */
    private static String escapeLikeWildcards(String keyword) {
        return keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }


    private Long currentUserId(Authentication authentication) {
        return CurrentUser.userIdOrNull(authentication, userRepository);
    }

    /** 공개 REST 조회는 삭제되지 않고 숨겨지지 않은 글만 돌려준다. */
    private Post findPost(Long id) {
        return postRepository.findById(id)
                .filter(post -> !post.isDeleted() && !post.isBlinded())
                .orElseThrow(() -> new PostNotFoundException(id));
    }

}
