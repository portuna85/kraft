package com.kraft.post.service;

import com.kraft.comment.domain.CommentRepository;
import com.kraft.post.domain.Post;
import com.kraft.post.domain.PostLikeRepository;
import com.kraft.post.domain.PostNotFoundException;
import com.kraft.post.domain.PostRepository;
import com.kraft.post.dto.PostLikeResponseDto;
import com.kraft.post.dto.PostSaveRequestDto;
import com.kraft.post.dto.PostUpdateRequestDto;
import com.kraft.report.domain.ReportTargetType;
import com.kraft.report.event.TargetDeletedEvent;
import com.kraft.shared.domain.VersionCheck;
import com.kraft.shared.security.CurrentUser;
import com.kraft.shared.security.OwnershipPolicy;
import com.kraft.shared.security.WriteAccessPolicy;
import com.kraft.shared.transaction.AfterCommit;
import com.kraft.shared.transaction.OnRollback;
import com.kraft.user.domain.User;
import com.kraft.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

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
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 이미지를 저장하고 업로더를 대장에 기록한다. 업로드 권한을 글쓰기 권한과 같게 맞춘다 —
     * 예전에는 이메일 미인증(GUEST)도 업로드 API를 쓸 수 있었다(개선 보고서 F06).
     * <p>
     * 대장 등록(용량 검사 포함)이 실패하면 방금 디스크에 쓴 파일을 곧바로 지운다 — 예전에는
     * 파일 저장과 DB 등록이 원자적이지 않아, DB 쪽이 실패하면 대장 없는 파일이 디스크에 남고
     * {@link PostImageCleaner}는 DB에 등록된 파일만 찾으므로 그 파일을 영영 발견하지 못했다
     * (개선 보고서 "파일 저장 성공 후 DB 롤백 시 대장 없는 파일").
     * <p>
     * 등록이 {@link PostImageRegistry#validateQuotaAndRegister}(자신의 트랜잭션) 안에서는
     * 성공해도, 반환 이후 <b>그 트랜잭션의 최종 커밋 자체</b>가 실패할 수 있다(개선 보고서
     * "파일 저장 성공 후 최종 커밋 실패 시 대장 없는 파일") — 그 보상({@link OnRollback})은
     * 실제로 그 트랜잭션을 여는 {@code validateQuotaAndRegister} 안에 등록되어 있다. 그래도
     * 놓치는 경우(커밋 직후 프로세스 종료 등)의 최후 수단은 별도의 주기적 디스크-대장 대조가
     * 맡는다.
     * <p>
     * 이 메서드는 {@code SUPPORTS}로 돈다(BE-23) — 파일 형식 검증(ImageIO 디코드 포함)과
     * 디스크 쓰기({@code postImageService.store})는 DB를 전혀 쓰지 않는데, 평범한
     * {@code @Transactional}로 감싸면 그 시간만큼 DB 커넥션이 아무 일도 안 하며 붙잡혀
     * 있었다. 실제 DB 작업(쿼터 검사 + 등록)은 {@code validateQuotaAndRegister}가 맡는다.
     * <p>
     * {@code SUPPORTS}(readOnly=false 명시)를 고른 이유는 두 가지를 동시에 만족해야 하기
     * 때문이다:
     * <ol>
     * <li>실제 호출 경로(REST 컨트롤러, 바깥 트랜잭션 없음)에서는 이 메서드가 트랜잭션을
     * 새로 열지 않는다(SUPPORTS는 이미 트랜잭션이 있을 때만 참여하고, 없으면 트랜잭션
     * 없이 그대로 실행한다) — 그래서 파일 I/O 동안 커넥션을 붙잡지 않는다는 목적이
     * 그대로 유지된다.</li>
     * <li>{@code PostImageLifecycleTest}의 "바깥 트랜잭션이 커밋되지 않으면 저장한 파일도
     * 함께 없어진다"처럼, 이 메서드를 감싸는 바깥 트랜잭션이 <b>이미 있는</b> 상황에서는
     * 그 트랜잭션에 그대로 참여해야 한다 — 그래야 그 안에서 부른
     * {@code validateQuotaAndRegister}(REQUIRED)의 등록도 같은 트랜잭션에 묶이고,
     * 바깥이 롤백되면 등록도 함께 롤백되며 {@code OnRollback} 보상도 제대로 걸린다.</li>
     * </ol>
     * {@code @Transactional}을 아예 생략하면(클래스 기본값 {@code readOnly = true}를 그대로
     * 물려받아) 바깥 트랜잭션이 없을 때도 이 메서드가 읽기 전용 트랜잭션으로 실행되고,
     * REQUIRED로 참여한 {@code validateQuotaAndRegister}의 쓰기(SELECT ... FOR UPDATE 포함)가
     * MariaDB에서 "Cannot execute statement in a READ ONLY transaction"으로 실패했다
     * (H2는 이 제약을 강제하지 않아 처음엔 못 잡았다 — BackupRestoreRehearsalTest에서 실제로
     * 재현). 반대로 {@code NOT_SUPPORTED}를 쓰면 이번에는 바깥에 이미 트랜잭션이 있어도
     * 무조건 중단시켜, 위 2번(바깥 트랜잭션과의 합류)이 깨진다 — 그래서 SUPPORTS가 맞다.
     */
    @Transactional(propagation = Propagation.SUPPORTS, readOnly = false)
    public PostImageService.StoredImage uploadImage(MultipartFile file, Authentication authentication) {
        User user = findUser(authentication);
        WriteAccessPolicy.requireVerified(user);

        PostImageService.StoredImage stored = postImageService.store(file);
        try {
            // 쿼터는 업로드 원본이 아니라 실제로 디스크에 저장된(메타데이터를 뺀) 크기로 센다.
            postImageRegistry.validateQuotaAndRegister(
                    stored.url(), user, stored.sizeBytes(), stored.width(), stored.height());
        } catch (RuntimeException e) {
            postImageService.deleteIfExists(stored.url());
            throw e;
        }
        return stored;
    }

    @CacheEvict(value = "pinnedNotices", allEntries = true)
    @Transactional
    public Long save(Authentication authentication, PostSaveRequestDto requestDto) {
        User user = findUser(authentication);
        WriteAccessPolicy.requireVerified(user);
        CategoryPolicy.requireCanUse(authentication, requestDto.category());

        Post post = postRepository.save(requestDto.toEntity(user));
        postImageRegistry.attach(requestDto.picture(), user, post)
                .ifPresent(size -> post.updatePictureSize(size.width(), size.height()));
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
    @CacheEvict(value = "pinnedNotices", allEntries = true)
    @Transactional
    public Long update(Long id, PostUpdateRequestDto requestDto, Authentication authentication) {
        Post post = findPost(id);
        User actor = findUser(authentication);
        WriteAccessPolicy.requireVerified(actor);
        validateOwner(post, authentication);
        VersionCheck.require(Post.class, post.getId(), post.getVersion(), requestDto.version());
        CategoryPolicy.requireCanUse(authentication, requestDto.category());

        String oldPicture = post.getPicture();
        String newPicture = requestDto.picture();
        boolean pictureChanged = oldPicture == null ? newPicture != null : !oldPicture.equals(newPicture);

        PostImageRegistry.MeasuredSize measured = pictureChanged && newPicture != null
                ? postImageRegistry.attach(newPicture, actor, post).orElse(null)
                : null;

        // 이미지를 지웠으면(newPicture == null) 크기도 함께 비운다 — picture 없이 크기만
        // 남으면 다음 열람 때 쓸모없는 값이 된다(A-FE-09). 새 이미지는 서버가 측정한 크기를 쓰고(BE-24),
        // 이미지가 그대로면 이미 저장된 크기를 유지한다. 측정값이 없는 옛 이미지만 클라이언트 값에 기댄다.
        Integer newWidth;
        Integer newHeight;
        if (newPicture == null) {
            newWidth = null;
            newHeight = null;
        } else if (measured != null) {
            newWidth = measured.width();
            newHeight = measured.height();
        } else if (!pictureChanged) {
            newWidth = post.getPictureWidth();
            newHeight = post.getPictureHeight();
        } else {
            newWidth = requestDto.pictureWidth();
            newHeight = requestDto.pictureHeight();
        }
        post.update(requestDto.title(), requestDto.content(), newPicture, newWidth, newHeight, requestDto.category());

        if (pictureChanged && oldPicture != null) {
            Long deletedImageId = postImageRegistry.markForDeletion(oldPicture).orElse(null);
            cleanUpAfterCommit(deletedImageId == null ? List.of() : List.of(deletedImageId));
        }
        return id;
    }

    /**
     * 게시글을 소프트 삭제한다 — {@code deletedAt}만 남기고 댓글·추천·이미지는 그대로 둔다. 관리자가
     * 복구할 수 있고, 보관 기간이 지나면 {@link #purge}가 행과 딸린 것들을 지운다.
     * <p>
     * 글이 목록에서 사라지는 순간 그 글과 아래 댓글에 걸린 대기 신고도 닫아야 하므로, 지우기 전에 댓글
     * id를 알아 두고 {@link TargetDeletedEvent}를 발행한다(A-BE-01). 관리자가 신고를 처리하며 지운
     * 경우는 이 이벤트가 아무 일도 하지 않는다 — 그 경로는 {@code ReportService.resolve()}가 관련 신고를
     * 이미 RESOLVED로 직접 처리해, 이 이벤트가 커밋 직전에 실행될 때는 더 이상 PENDING이 아니다
     * ({@code TargetDeletedEvent} 문서 참고).
     */
    @Caching(evict = {
            @CacheEvict(value = "pinnedNotices", allEntries = true),
            @CacheEvict(value = "popularPosts", allEntries = true)
    })
    @Transactional
    public void delete(Long id, Authentication authentication) {
        Post post = findPost(id);
        validateOwner(post, authentication);

        List<Long> commentIds = commentRepository.findIdsByPostId(id);
        // 0이면 그 사이 다른 요청이 먼저 지운 것이다 — 없는 글로 본다.
        if (postRepository.softDelete(id, LocalDateTime.now()) == 0) {
            throw new PostNotFoundException(id);
        }

        eventPublisher.publishEvent(new TargetDeletedEvent(ReportTargetType.POST, List.of(id)));
        if (!commentIds.isEmpty()) {
            eventPublisher.publishEvent(new TargetDeletedEvent(ReportTargetType.COMMENT, commentIds));
        }
    }

    /**
     * 소프트 삭제된 지 {@code threshold}가 지난 글을 영구 삭제한다. 소유권을 묻지 않으므로 호출자
     * ({@code PostPurger})가 대상을 고른다. 행을 잠근 뒤 다시 확인해, 그 사이 복구됐거나 아직 보관
     * 기간 안인 글은 건너뛴다. 신고는 소프트 삭제 때 이미 닫았으므로 이벤트를 다시 발행하지 않는다.
     *
     * @return 실제로 지웠으면 true
     */
    @Caching(evict = {
            @CacheEvict(value = "pinnedNotices", allEntries = true),
            @CacheEvict(value = "popularPosts", allEntries = true)
    })
    @Transactional
    public boolean purge(Long id, LocalDateTime threshold) {
        Post post = postRepository.findByIdForPurge(id).orElse(null);
        if (post == null || post.getDeletedAt() == null || !post.getDeletedAt().isBefore(threshold)) {
            return false;
        }

        // 삭제 예약이 post_id를 비워야 FK 제약(FK_POST_IMAGES_POST)이 게시글 삭제를 막지 않는다.
        List<Long> deletedImageIds = postImageRegistry.markPostImagesForDeletion(id);
        // 댓글·추천이 남아 있으면 FK 제약 위반으로 삭제가 실패하므로 먼저 지운다. 답글을 먼저
        // 지우지 않으면 MariaDB에서 자기참조 FK(FK_COMMENTS_PARENT) 위반이 날 수 있다
        // (CommentRepository.deleteRepliesByPostId 주석 참고).
        commentRepository.deleteRepliesByPostId(id);
        commentRepository.deleteAllByPostId(id);
        postLikeRepository.deleteAllByPostId(id);
        postRepository.delete(post);

        cleanUpAfterCommit(deletedImageIds);
        return true;
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
        User user = findUser(authentication);

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

    private User findUser(Authentication authentication) {
        return CurrentUser.require(authentication, userRepository);
    }

    /** 삭제되지 않은 글만 찾는다. 소프트 삭제된 글은 수정·추천·삭제의 대상이 아니다. */
    private Post findPost(Long id) {
        return postRepository.findById(id)
                .filter(post -> !post.isDeleted())
                .orElseThrow(() -> new PostNotFoundException(id));
    }

    /**
     * 작성자 본인 또는 관리자만 게시글을 수정·삭제할 수 있다.
     */
    private void validateOwner(Post post, Authentication authentication) {
        OwnershipPolicy.validateOwner(authentication, post.getUser(), post.getId());
    }
}
