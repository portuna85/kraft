package com.kraft.post.service;

import com.kraft.comment.domain.CommentRepository;
import com.kraft.post.domain.Category;
import com.kraft.post.domain.Post;
import com.kraft.post.domain.PostLikeRepository;
import com.kraft.post.domain.PostNotFoundException;
import com.kraft.post.domain.PostRepository;
import com.kraft.post.dto.PostLikeResponseDto;
import com.kraft.post.dto.PostResponseDto;
import com.kraft.post.dto.PostRowDto;
import com.kraft.post.dto.PostSaveRequestDto;
import com.kraft.post.dto.PostsListResponseDto;
import com.kraft.post.dto.PostsPageResponseDto;
import com.kraft.post.dto.PostUpdateRequestDto;
import com.kraft.post.dto.PostViewDto;
import com.kraft.shared.security.OwnershipPolicy;
import com.kraft.shared.security.WriteAccessPolicy;
import com.kraft.shared.transaction.AfterCommit;
import com.kraft.shared.transaction.OnRollback;
import com.kraft.user.domain.EmailHasher;
import com.kraft.user.domain.EmailMasker;
import com.kraft.user.domain.User;
import com.kraft.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

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
    private final PostImageRegistry postImageRegistry;
    private final PostImageCleaner postImageCleaner;
    private final PostLikeWriter postLikeWriter;

    /** 검색어 상한. 지나치게 긴 검색어까지 그대로 LIKE 조건에 실을 이유가 없다(개선 보고서 "검색과 깊은 페이지의 비용"). */
    private static final int MAX_KEYWORD_LENGTH = 100;

    /**
     * 이미지를 저장하고 업로더를 대장에 기록한다. 업로드 권한을 글쓰기 권한과 같게 맞춘다 —
     * 예전에는 이메일 미인증(GUEST)도 업로드 API를 쓸 수 있었다(개선 보고서 F06).
     * <p>
     * 대장 등록(용량 검사 포함)이 실패하면 방금 디스크에 쓴 파일을 곧바로 지운다 — 예전에는
     * 파일 저장과 DB 등록이 원자적이지 않아, DB 쪽이 실패하면 대장 없는 파일이 디스크에 남고
     * {@link PostImageCleaner}는 DB에 등록된 파일만 찾으므로 그 파일을 영영 발견하지 못했다
     * (개선 보고서 "파일 저장 성공 후 DB 롤백 시 대장 없는 파일").
     * <p>
     * 등록이 이 메서드 안에서는 성공해도, 반환 이후 바깥 트랜잭션의 <b>최종 커밋 자체</b>가
     * 실패할 수 있다(개선 보고서 "파일 저장 성공 후 최종 커밋 실패 시 대장 없는 파일") — 그
     * 실패는 메서드 안의 {@code catch}로 잡을 수 없으므로, 트랜잭션이 커밋 이외로 끝나면
     * 파일을 지우는 보상을 {@link OnRollback}으로 등록해 둔다. 그래도 놓치는 경우(커밋 직후
     * 프로세스 종료 등)의 최후 수단은 별도의 주기적 디스크-대장 대조가 맡는다.
     */
    @Transactional
    public String uploadImage(MultipartFile file, Authentication authentication) {
        User user = findUser(authentication.getName());
        WriteAccessPolicy.requireVerified(user);

        String url = postImageService.store(file);
        try {
            postImageRegistry.validateQuotaAndRegister(url, user, file.getSize());
        } catch (RuntimeException e) {
            postImageService.deleteIfExists(url);
            throw e;
        }
        OnRollback.run(() -> postImageService.deleteIfExists(url));
        return url;
    }

    @Transactional
    public Long save(Authentication authentication, PostSaveRequestDto requestDto) {
        User user = findUser(authentication.getName());
        WriteAccessPolicy.requireVerified(user);
        CategoryPolicy.requireCanUse(authentication, requestDto.category());

        Post post = postRepository.save(requestDto.toEntity(user));
        postImageRegistry.attach(requestDto.picture(), user, post);
        return post.getId();
    }

    /**
     * 게시글을 수정한다. 이미지 교체가 있으면 <b>새 이미지의 소유권을 먼저 검사</b>하고, 기존
     * 이미지는 파일을 바로 지우는 대신 삭제 예약만 남긴다 — 이 트랜잭션이 실패해 롤백되면
     * 예약도 함께 사라져 기존 이미지가 그대로 보존된다(개선 보고서 F05).
     * <p>
     * 정지된 계정도 이 경로로 자신의 기존 글을 계속 바꿀 수 있었다 — 생성·업로드만 작성
     * 정책을 검사하고 수정은 소유권만 봤기 때문이다. 삭제는 의도적으로 그대로 둔다 —
     * 정지된 사용자도 자신의 글을 지우는 것까지 막지는 않는다.
     */
    @Transactional
    public Long update(Long id, PostUpdateRequestDto requestDto, Authentication authentication) {
        Post post = findPost(id);
        User actor = findUser(authentication.getName());
        WriteAccessPolicy.requireVerified(actor);
        validateOwner(post, authentication);
        validateVersion(post, requestDto.version());
        CategoryPolicy.requireCanUse(authentication, requestDto.category());

        String oldPicture = post.getPicture();
        String newPicture = requestDto.picture();
        boolean pictureChanged = oldPicture == null ? newPicture != null : !oldPicture.equals(newPicture);

        if (pictureChanged && newPicture != null) {
            postImageRegistry.attach(newPicture, actor, post);
        }

        post.update(requestDto.title(), requestDto.content(), newPicture, requestDto.category());

        if (pictureChanged && oldPicture != null) {
            Long deletedImageId = postImageRegistry.markForDeletion(oldPicture).orElse(null);
            cleanUpAfterCommit(deletedImageId == null ? List.of() : List.of(deletedImageId));
        }
        return id;
    }

    @Transactional
    public void delete(Long id, Authentication authentication) {
        Post post = findPost(id);
        validateOwner(post, authentication);

        // 삭제 예약이 post_id를 비워야 FK 제약(FK_POST_IMAGES_POST)이 게시글 삭제를 막지 않는다.
        List<Long> deletedImageIds = postImageRegistry.markPostImagesForDeletion(id);
        // 댓글·추천이 남아 있으면 FK 제약 위반으로 삭제가 실패하므로 먼저 지운다.
        commentRepository.deleteAllByPostId(id);
        postLikeRepository.deleteAllByPostId(id);
        postRepository.delete(post);

        cleanUpAfterCommit(deletedImageIds);
    }

    public PostResponseDto findById(Long id) {
        return new PostResponseDto(findPost(id));
    }

    /**
     * 상세 화면용 조회. 인증 객체와 엔티티를 함께 볼 수 있는 이 지점에서 관리 권한을 계산해
     * 화면 전용 DTO로 내려준다. 화면이 작성자 이름과 로그인 이메일을 비교하는 방식(잘못된
     * 소유권 추정)을 쓰지 않게 하려는 것이다. 공개 REST DTO는 그대로 둔다.
     * <p>
     * 조회수는 엔티티를 읽기 <b>전에</b> 별도의 원자적 UPDATE로 올린다. 엔티티를 바꿔 변경
     * 감지에 맡기면 제목·본문·분류까지 함께 UPDATE에 실려, 단순 열람이 다른 트랜잭션의 편집을
     * 되돌리고 최종수정일까지 바꿨다(개선 보고서 F02·F11). 순서를 이렇게 두면 늘어난 조회수가
     * 그대로 화면에 반영된다(새로고침·중복 방문 방지는 여전히 없는 단순 카운터다).
     */
    @Transactional
    public PostViewDto findByIdForView(Long id, Authentication authentication) {
        postRepository.increaseViewCount(id);

        Post post = findPost(id);
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
        Page<PostRowDto> page = postRepository.search(normalize(keyword), category, pageable);
        Map<Long, Long> commentCounts = commentRepository.countByPostIdIn(
                page.getContent().stream().map(PostRowDto::id).toList());
        Page<PostsListResponseDto> mapped = page.map(row ->
                new PostsListResponseDto(row, commentCounts.getOrDefault(row.id(), 0L)));
        return new PostsPageResponseDto(mapped);
    }

    /**
     * 조회수 기준 상위 {@code limit}개(인기글). 목록 화면 상단의 별도 섹션에 쓰인다.
     * <p>
     * 인기글 템플릿은 제목·조회수만 보여주고 댓글 수는 쓰지 않는다(index.html 확인). 예전에는
     * 여기서도 목록과 같은 댓글 수 집계 쿼리를 돌렸다(개선 보고서 "게시판 목록의 불필요한 열과
     * 집계") — 화면에 쓰이지 않는 값을 매번 계산한 것이다.
     */
    public List<PostsListResponseDto> findPopular(int limit) {
        List<PostRowDto> rows = postRepository.findTopByViewCountDesc(PageRequest.of(0, limit));
        return rows.stream()
                .map(row -> new PostsListResponseDto(row, 0L))
                .toList();
    }

    /**
     * 상세 화면 하단의 관련 게시글(같은 분류, 현재 글 제외, 최신순 최대 {@code limit}개).
     */
    public List<PostRowDto> findRelated(Category category, Long excludeId, int limit) {
        return postRepository.findRelated(category, excludeId, PageRequest.of(0, limit));
    }

    /**
     * 게시글 추천을 <b>원하는 상태로 맞춘다</b>. 이 엔드포인트는 항상 인증된 사용자만
     * 호출할 수 있으므로(SecurityConfig의 {@code /api/v1/**} authenticated() 규칙) 익명
     * 처리를 따로 두지 않는다.
     * <p>
     * 예전에는 현재 상태를 뒤집는 토글이었다. 그래서 네트워크가 불안해 같은 요청이 두 번
     * 도달하면 사용자의 의도가 되돌아갔고, 두 요청이 겹치면 둘 다 "아직 안 눌렀음"을 읽어
     * 한쪽이 유니크 제약 위반으로 실패했다(개선 보고서 F10).
     * <p>
     * 지금은 호출하는 쪽이 원하는 최종 상태를 지정하므로 <b>몇 번을 보내도 결과가 같다</b>.
     * 경쟁에서 밀려 INSERT가 제약에 걸리는 경우도 결국 원하던 상태("추천됨")와 같으므로
     * 성공으로 처리한다.
     * <p>
     * 이 메서드 자체는 아무것도 쓰지 않는다 — 실제 쓰기(insert/delete)와 최신 개수 조회는
     * {@link PostLikeWriter}가 전부 REQUIRES_NEW로 독립 수행한다(B09). 그런데도 이 메서드가
     * (클래스 기본값인 readOnly 트랜잭션이라도) 자신의 트랜잭션을 열면, findPost·findUser가
     * 커넥션 하나를 쥔 채로 그 REQUIRES_NEW 호출들이 <b>추가</b> 커넥션을 요구한다 — 동시
     * 좋아요 요청이 몰리면 요청 하나가 커넥션을 최대 2개씩 동시에 물고 있는 셈이라 풀 압박이
     * 커진다. {@code NOT_SUPPORTED}로 이 메서드 자신의 트랜잭션을 열지 않으면, findPost·
     * findUser는 각자 리포지토리 기본 트랜잭션으로 짧게 커넥션을 빌렸다 곧바로 돌려주고,
     * REQUIRES_NEW 호출들도 그때그때 자기 커넥션만 쓴다 — 어느 시점에도 한 요청이 커넥션
     * 두 개를 동시에 쥐지 않는다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PostLikeResponseDto setLike(Long id, boolean liked, Authentication authentication) {
        Post post = findPost(id);
        User user = findUser(authentication.getName());

        if (liked) {
            addLikeIfAbsent(post, user);
        } else {
            // REQUIRES_NEW로 지운다(B09 회귀 수정) — 이 메서드(바깥 트랜잭션) 안에서 그냥
            // 지우면, 아직 커밋 전인 상태에서 뒤이은 countByPostId(REQUIRES_NEW)가 별도
            // 트랜잭션이라 이 DELETE를 보지 못해 추천 취소 뒤에도 개수가 그대로 남았다.
            postLikeWriter.delete(id, user.getId());
        }

        // postLikeWriter.countByPostId도 새 트랜잭션에서 읽는다(B09) — 이 메서드의 트랜잭션이
        // 이미 잡아 둔 REPEATABLE READ 스냅샷은 REQUIRES_NEW로 방금 커밋된 추천을 못 볼 수 있다.
        return new PostLikeResponseDto(liked, postLikeWriter.countByPostId(id));
    }

    /**
     * 검사와 INSERT 사이에 같은 추천이 들어와 유니크 제약에 걸리면 원하던 최종 상태와
     * 같으므로 그대로 둔다. 그 외의 원인(부모 게시글이 막 삭제된 경우의 FK 위반 등)은
     * "이미 추천됨"으로 위장하지 않고 다시 던진다(B09) — {@code PostLikeWriter.insert}
     * 자체는 아무것도 삼키지 않으므로(REQUIRES_NEW 트랜잭션 경계 안에서 삼키면
     * {@code UnexpectedRollbackException}이 난다), 그 경계 밖인 여기서 판단한다.
     */
    private void addLikeIfAbsent(Post post, User user) {
        if (postLikeRepository.existsByPostIdAndUserId(post.getId(), user.getId())) {
            return;
        }
        try {
            postLikeWriter.insert(post, user);
        } catch (DataIntegrityViolationException e) {
            if (!postLikeWriter.isDuplicateLikeConstraint(e)) {
                throw e;
            }
        }
    }

    /**
     * 삭제가 예약된 이미지 파일을 커밋 직후 곧바로 치운다. 실패하거나 그 직후 프로세스가
     * 죽더라도 예약은 DB에 남아 {@link PostImageCleaner}의 주기 작업이 다시 시도한다.
     * <p>
     * 이번 요청이 방금 표시한 이미지 id만 넘긴다 — 예전에는 인자 없이
     * {@code cleanPendingDeletions()} 전체를 불러 시스템 전체의 삭제 대기열을 요청마다
     * 훑었다. 게시글 한 건 저장·삭제의 응답이 그때그때 쌓인 적체량에 좌우되던 문제라
     * 전체 스윕은 {@link PostImageCleaner}의 예약 작업에만 맡긴다.
     */
    private void cleanUpAfterCommit(List<Long> imageIds) {
        if (imageIds.isEmpty()) {
            return;
        }
        AfterCommit.run(() -> postImageCleaner.cleanPendingDeletionsFor(imageIds));
    }

    /**
     * 화면이 받아간 버전과 지금 DB의 버전이 다르면, 그 사이 다른 곳에서 저장이 일어난 것이다.
     * 버전을 보내지 않는 요청은 기존처럼 그대로 저장한다.
     */
    private void validateVersion(Post post, Long expectedVersion) {
        if (expectedVersion != null && !expectedVersion.equals(post.getVersion())) {
            throw new ObjectOptimisticLockingFailureException(Post.class, post.getId());
        }
    }

    private String normalize(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String trimmed = keyword.trim();
        return trimmed.length() > MAX_KEYWORD_LENGTH ? trimmed.substring(0, MAX_KEYWORD_LENGTH) : trimmed;
    }

    private User findUser(String email) {
        return userRepository.findByEmailHash(EmailHasher.sha512Hex(email))
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다. email=" + EmailMasker.mask(email)));
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
