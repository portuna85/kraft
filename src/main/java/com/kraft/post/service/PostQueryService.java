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
 * 게시글 조회 전용 서비스(BE-42). 목록·검색·인기글·공지·관련 글·상세 화면용 조회를 맡는다.
 * 쓰기(저장·수정·삭제·추천·이미지 업로드)는 {@link PostService}가 맡는다 — 예전에는 한 클래스가
 * 둘 다 들고 있어 의존성이 9개까지 늘었다. 상세 조회({@link #findByIdForView})만 조회수를 올리는
 * 원자적 UPDATE를 함께 실행하므로 쓰기 트랜잭션으로 연다.
 */
@RequiredArgsConstructor
@Service
@Transactional(readOnly = true)
public class PostQueryService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final CommentRepository commentRepository;
    private final PostLikeRepository postLikeRepository;

    /** 검색어 상한. 지나치게 긴 검색어까지 그대로 LIKE 조건에 실을 이유가 없다(개선 보고서 "검색과 깊은 페이지의 비용"). */
    private static final int MAX_KEYWORD_LENGTH = 100;

    /**
     * 검색어 최소 길이(개선 보고서 A-BE-02 1단계). 1글자 검색은 인덱스를 못 타는 선행
     * 와일드카드 LIKE에서 사실상 전체 스캔과 같은 대량의 행을 매치시켜, 검색 폼 연타만으로
     * DB CPU를 쉽게 점유할 수 있었다. 이보다 짧으면 검색어가 없는 것으로 보고 전체 목록을
     * 보여준다 — 오류로 거절하지 않는다(공백 검색어를 무시하는 기존 동작과 같은 관례).
     */
    private static final int MIN_KEYWORD_LENGTH = 2;


    public PostResponseDto findById(Long id) {
        return new PostResponseDto(findPost(id));
    }

    /**
     * 상세 화면용 조회. 항상 조회수를 올린다 — 호출자가 중복 방문 여부를 판단하지 않는
     * 내부·테스트 호출에 쓴다. 실제 컨트롤러 경로는 {@link #findByIdForView(Long, Authentication, boolean)}로
     * 중복 방문 여부(A-BE-04)를 넘긴다.
     */
    @Transactional
    public PostViewDto findByIdForView(Long id, Authentication authentication) {
        return findByIdForView(id, authentication, true);
    }

    /**
     * 상세 화면용 조회. 인증 객체와 엔티티를 함께 볼 수 있는 이 지점에서 관리 권한을 계산해
     * 화면 전용 DTO로 내려준다. 화면이 작성자 이름과 로그인 이메일을 비교하는 방식(잘못된
     * 소유권 추정)을 쓰지 않게 하려는 것이다. 공개 REST DTO는 그대로 둔다.
     * <p>
     * 조회수는 엔티티를 읽기 <b>전에</b> 별도의 원자적 UPDATE로 올린다. 엔티티를 바꿔 변경
     * 감지에 맡기면 제목·본문·분류까지 함께 UPDATE에 실려, 단순 열람이 다른 트랜잭션의 편집을
     * 되돌리고 최종수정일까지 바꿨다(개선 보고서 F02·F11). 순서를 이렇게 두면 늘어난 조회수가
     * 그대로 화면에 반영된다.
     * <p>
     * {@code countView}가 false면 조회수를 올리지 않는다 — {@code PostViewDedup}(A-BE-04)가
     * 같은 방문자가 짧은 시간 안에 같은 글을 다시 열었다고 판단했을 때 컨트롤러가 넘기는 값이다.
     * 새로고침·봇·링크 미리보기·재방문이 매번 1씩 올리던 것을 줄인다.
     */
    @Transactional
    public PostViewDto findByIdForView(Long id, Authentication authentication, boolean countView) {
        if (countView) {
            postRepository.increaseViewCount(id);
        }

        // 작성자를 같은 쿼리로 가져오고(BE-05), 추천 수·내가 눌렀는지도 한 번에 센다. 조회수
        // UPDATE가 영속성 컨텍스트를 비우므로 이 조회는 항상 DB를 다시 읽는다.
        Post post = postRepository.findByIdWithUser(id)
                .orElseThrow(() -> new PostNotFoundException(id));
        // 소프트 삭제된 글은 관리자만 열 수 있다(복구하려면 내용을 봐야 한다). 판정은 메모리에서 하므로
        // 쿼리 수가 늘지 않는다.
        boolean admin = OwnershipPolicy.isAdmin(authentication);
        if (post.isDeleted() && !admin) {
            throw new PostNotFoundException(id);
        }
        // 관리자가 숨긴 글도 관리자만 연다. 상태 코드는 404지만 안내 문구는 다르다(PostHiddenException).
        if (post.isBlinded() && !admin) {
            throw new PostHiddenException(id);
        }
        Long userId = currentUserId(authentication);
        PostLikeRepository.LikeSummary likes = postLikeRepository.summarize(
                id, userId != null ? userId : PostLikeRepository.NO_USER_ID);
        boolean likedByMe = userId != null && likes.getMine() > 0;
        // 삭제된 글에는 수정·삭제 버튼을 보이지 않는다 — 할 수 있는 일은 관리자의 복구뿐이다.
        boolean canManage = !post.isDeleted() && OwnershipPolicy.canManage(authentication, post.getUser());
        return new PostViewDto(post, canManage, likes.getTotal(), likedByMe, admin);
    }

    public PostsPageResponseDto findAllDesc(Pageable pageable) {
        return findAllDesc(pageable, null, null, false);
    }

    /** 검색 범위를 지정하지 않는 호출은 기본값(제목만, A-BE-02 2단계)으로 좁힌다. */
    public PostsPageResponseDto findAllDesc(Pageable pageable, String keyword, Category category) {
        return findAllDesc(pageable, keyword, category, false);
    }

    /**
     * 검색어·분류로 목록을 좁힌다. 두 조건 모두 없으면 전체 목록을 최신순으로 반환한다.
     * 목록에 필요한 댓글 수는 게시글마다 따로 조회하지 않고, 이 페이지에 담긴 게시글
     * ID로 한 번에 묶어 조회한다(N+1 방지).
     * <p>
     * 정렬은 여기서 {@code PostSortPolicy.effectiveSort}로 보정한다(B10) — 호출자
     * (SSR/REST 두 컨트롤러)가 이미 허용 목록으로 걸러 둔 Sort를, id 동점 처리를 포함한
     * 실제 정렬로 바꿔 리포지토리에 넘긴다. 두 컨트롤러가 각자 이 변환을 반복하지 않도록
     * 여기 한 곳에만 둔다.
     * <p>
     * {@code searchContent}가 false면 제목만 검색한다(A-BE-02 2단계, 기본값) — 본문(TEXT)
     * 까지 뒤지는 선행 와일드카드 LIKE가 이 검색에서 가장 비용이 큰 부분이라, 사용자가
     * "제목+내용"을 직접 고를 때만 켠다.
     */
    public PostsPageResponseDto findAllDesc(Pageable pageable, String keyword, Category category, boolean searchContent) {
        Pageable effective = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                PostSortPolicy.effectiveSort(pageable.getSort()));
        String normalized = normalize(keyword);
        if (normalized == null) {
            // 검색어 없음(너무 짧아 무시된 경우 포함): 인덱스로 세는 COUNT가 싸고 화면이 총 건수와
            // 번호 이동을 그린다.
            Page<PostRowDto> page = postRepository.search(null, category, searchContent, effective);
            Map<Long, Long> commentCounts = commentCountsOf(page);
            return new PostsPageResponseDto(page.map(row ->
                    new PostsListResponseDto(row, commentCounts.getOrDefault(row.id(), 0L))));
        }
        // 검색어 있음: LIKE '%kw%'는 인덱스를 못 타서 COUNT가 항상 전체 스캔이다. 세지 않고 다음
        // 페이지가 있는지만 안다(BE-08).
        Slice<PostRowDto> slice = postRepository.searchWithoutCount(normalized, category, searchContent, effective);
        Map<Long, Long> commentCounts = commentCountsOf(slice);
        return new PostsPageResponseDto(slice.map(row ->
                new PostsListResponseDto(row, commentCounts.getOrDefault(row.id(), 0L))));
    }

    /**
     * 최근 {@link #POPULAR_WINDOW}(7일) 이내에 작성된 글 중 조회수 기준 상위 {@code limit}개
     * (인기글). 목록 화면 상단의 별도 섹션에 쓰인다.
     * <p>
     * 누적 조회수만 보면 오래전에 조회수를 많이 쌓은 글이 자리를 영영 독점한다(A-BE-10) —
     * 최근 글로 후보를 좁혀 새 글도 인기글에 오를 수 있게 한다.
     * <p>
     * 인기글 템플릿은 제목·조회수만 보여주고 댓글 수는 쓰지 않는다(index.html 확인). 예전에는
     * 여기서도 목록과 같은 댓글 수 집계 쿼리를 돌렸다(개선 보고서 "게시판 목록의 불필요한 열과
     * 집계") — 화면에 쓰이지 않는 값을 매번 계산한 것이다.
     * <p>
     * 홈 화면 진입마다 매번 다시 계산하지 않고 짧게 캐시한다(BE-25, TTL·크기는
     * application.yml의 spring.cache.caffeine.spec). 새 글의 조회수가 인기글 순위에 반영되는
     * 데 최대 캐시 유효시간만큼 지연이 생길 수 있지만, 실시간성이 중요한 값이 아니다.
     */
    /** 인기글 후보를 이 기간 이내에 작성된 글로 좁힌다(A-BE-10). */
    private static final Duration POPULAR_WINDOW = Duration.ofDays(7);

    @Cacheable("popularPosts")
    public List<PostsListResponseDto> findPopular(int limit) {
        LocalDateTime since = LocalDateTime.now().minus(POPULAR_WINDOW);
        List<PostRowDto> rows = postRepository.findTopByViewCountDesc(since, PageRequest.of(0, limit));
        return rows.stream()
                .map(row -> new PostsListResponseDto(row, 0L))
                .toList();
    }

    /** 목록 상단에 한 번에 고정하는 글 수의 상한. 관리자가 이보다 많이 고정하지 못하게 서비스도 같은 값을 쓴다. */
    public static final int PINNED_LIMIT = 5;

    /**
     * 목록 첫 페이지 상단에 고정할 글 최대 {@code limit}개(A-BE-05) — 관리자가 기한({@code pinned_until})을
     * 정해 고정한 글 중 아직 기한이 남은 것이다. 고정하지 않으면 공지도 일반 글과 똑같이 최신순으로 섞여,
     * 오래되면 뒤 페이지로 밀려 사실상 보이지 않는다.
     * <p>
     * 일반 목록 행과 같은 모양(post-list__item)으로 보여주므로 댓글 수도 실제 값을 담는다
     * (findPopular의 인기글 위젯과 달리 여기는 "0건"이 눈에 띄게 어색하다).
     * <p>
     * {@link #findPopular}와 같은 이유로 짧게 캐시한다. 글을 쓰거나 고치거나 지우거나 고정·숨김을 바꿀 때는 이
     * 캐시를 비운다(BE-16) — 지운 글이 최대 45초 동안 목록 위에 남아 404 링크가 되지 않게 한다. 기한이 지나 풀리는
     * 고정은 비울 계기가 없으므로 캐시 유효시간(45초) 안에 저절로 반영된다.
     */
    @Cacheable("pinnedPosts")
    public List<PostsListResponseDto> findPinned(int limit) {
        List<PostRowDto> rows = postRepository.findPinned(LocalDateTime.now(), PageRequest.of(0, limit));
        Map<Long, Long> commentCounts = commentRepository.countByPostIdIn(rows.stream().map(PostRowDto::id).toList());
        return rows.stream()
                .map(row -> new PostsListResponseDto(row, commentCounts.getOrDefault(row.id(), 0L)))
                .toList();
    }

    /**
     * 상세 화면 하단의 관련 게시글(같은 분류, 현재 글 제외, 최신순 최대 {@code limit}개).
     */
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
     * LIKE 패턴에서 특별한 의미를 갖는 문자(개선 보고서 A-BE-02 1단계)를 문자 그대로 매치되게
     * 이스케이프한다 — 이스케이프하지 않으면 사용자가 입력한 {@code %}·{@code _}가 그대로
     * 와일드카드로 해석된다({@code q=%}는 전체 목록과 같고 {@code q=_}는 모든 글과 일치).
     * 이스케이프 문자 자신({@code \})도 먼저 이스케이프해야 한다 — 그러지 않으면 사용자가
     * 입력한 역슬래시가 뒤따르는 문자와 합쳐져 의도치 않은 이스케이프 시퀀스가 된다.
     * {@code PostRepository.search}의 {@code ESCAPE '\'}와 반드시 함께 쓴다.
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
