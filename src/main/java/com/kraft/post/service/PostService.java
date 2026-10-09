package com.kraft.post.service;

import com.kraft.comment.domain.CommentRepository;
import com.kraft.post.domain.Post;
import com.kraft.post.domain.PostLikeRepository;
import com.kraft.post.domain.PostHiddenException;
import com.kraft.post.domain.PostNotFoundException;
import com.kraft.post.domain.PostRepository;
import com.kraft.post.dto.PostLikeResponseDto;
import com.kraft.post.dto.PostSaveRequestDto;
import com.kraft.post.dto.PostUpdateRequestDto;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
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

    /**
     * 이미지를 저장하고 업로더를 대장에 기록한다(인증 회원만). 대장 등록이 실패하면 방금 쓴 파일을
     * 지운다. 등록 트랜잭션의 커밋 실패는 {@link OnRollback}이, 그마저 놓친 경우는 주기적 디스크-대장
     * 대조가 맡는다.
     * <p>
     * {@code SUPPORTS}(readOnly=false)인 이유: 바깥 트랜잭션이 없으면 파일 I/O 동안 커넥션을 쥐지 않고,
     * 있으면 거기에 합류해 등록이 함께 롤백된다. 클래스 기본값(readOnly)을 물려받으면 MariaDB가
     * {@code validateQuotaAndRegister}의 쓰기를 거절하고, {@code NOT_SUPPORTED}는 합류를 깨뜨린다.
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

    @CacheEvict(value = "pinnedPosts", allEntries = true)
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
     * 게시글을 수정한다. 새 이미지는 소유권을 먼저 검사하고, 기존 이미지는 삭제 예약만 남겨 롤백 시
     * 보존되게 한다. 저장 직후 flush해 확정된 버전(응답 ETag)을 돌려준다.
     *
     * @param expectedVersion {@code If-Match}의 기준 버전. {@code null}이면 검사하지 않는다.
     */
    @CacheEvict(value = "pinnedPosts", allEntries = true)
    @Transactional
    public PostUpdateResult update(Long id, PostUpdateRequestDto requestDto, Long expectedVersion,
                                    Authentication authentication) {
        Post post = findPost(id);
        User actor = findUser(authentication);
        WriteAccessPolicy.requireVerified(actor);
        validateOwner(post, authentication);
        requireNotBlindedUnlessAdmin(post, authentication);
        VersionCheck.require(Post.class, post.getId(), post.getVersion(), expectedVersion);
        CategoryPolicy.requireCanUse(authentication, requestDto.category());

        String oldPicture = post.getPicture();
        String newPicture = requestDto.picture();
        boolean pictureChanged = oldPicture == null ? newPicture != null : !oldPicture.equals(newPicture);

        PostImageRegistry.MeasuredSize measured = pictureChanged && newPicture != null
                ? postImageRegistry.attach(newPicture, actor, post).orElse(null)
                : null;

        // 크기: 이미지 제거 → 비움, 새 이미지 → 서버 측정값, 그대로 → 기존 값,
        // 측정값 없는 옛 이미지 → 클라이언트 값.
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
        postRepository.flush();
        return new PostUpdateResult(id, post.getVersion());
    }

    /** 수정 결과. {@code version}은 저장 뒤 확정된 새 버전이다. */
    public record PostUpdateResult(Long id, Long version) {
    }

    /** 소프트 삭제({@code deletedAt}만 기록). 보관 기간이 지나면 {@link #purge}가 지운다. */
    @Caching(evict = {
            @CacheEvict(value = "pinnedPosts", allEntries = true),
            @CacheEvict(value = "popularPosts", allEntries = true)
    })
    @Transactional
    public void delete(Long id, Authentication authentication) {
        Post post = findPost(id);
        validateOwner(post, authentication);
        requireNotBlindedUnlessAdmin(post, authentication);

        // 0이면 그 사이 다른 요청이 먼저 지운 것이다 — 없는 글로 본다.
        if (postRepository.softDelete(id, LocalDateTime.now()) == 0) {
            throw new PostNotFoundException(id);
        }
    }

    /**
     * 소프트 삭제 후 {@code threshold}가 지난 글을 영구 삭제한다(소유권 검사 없음, {@code PostPurger} 전용).
     * 행을 잠근 뒤 다시 확인해 그 사이 복구된 글은 건너뛴다.
     *
     * @return 실제로 지웠으면 true
     */
    @Caching(evict = {
            @CacheEvict(value = "pinnedPosts", allEntries = true),
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
        // FK 때문에 댓글·추천을 먼저 지운다. 답글이 먼저여야 자기참조 FK(FK_COMMENTS_PARENT)에 걸리지 않는다.
        commentRepository.deleteRepliesByPostId(id);
        commentRepository.deleteAllByPostId(id);
        postLikeRepository.deleteAllByPostId(id);
        postRepository.delete(post);

        cleanUpAfterCommit(deletedImageIds);
        return true;
    }

    /**
     * 추천을 원하는 최종 상태로 맞춘다(멱등). 경쟁으로 INSERT가 유니크 제약에 걸려도 결과가 같으니 성공이다.
     * <p>
     * 쓰기와 개수 조회는 {@link PostLikeWriter}가 REQUIRES_NEW로 한다. 이 메서드가 트랜잭션을 열면
     * 요청 하나가 커넥션 두 개를 동시에 쥐게 되므로 {@code NOT_SUPPORTED}로 둔다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PostLikeResponseDto setLike(Long id, boolean liked, Authentication authentication) {
        Post post = findPost(id);
        // 숨겨진 글은 관리자 말고는 열 수 없으므로 추천도 없는 글로 본다.
        if (post.isBlinded() && !OwnershipPolicy.isAdmin(authentication)) {
            throw new PostHiddenException(id);
        }
        User user = findUser(authentication);

        if (liked) {
            addLikeIfAbsent(post, user);
        } else {
            // 별도 트랜잭션에서 커밋해야 뒤이은 countByPostId(REQUIRES_NEW)가 이 삭제를 본다.
            postLikeWriter.delete(id, user.getId());
        }

        // 새 트랜잭션에서 세야 방금 커밋된 추천이 보인다(REPEATABLE READ 스냅샷 회피).
        return new PostLikeResponseDto(liked, postLikeWriter.countByPostId(id));
    }

    /**
     * 중복 추천 제약 위반만 성공으로 삼키고 그 밖(FK 위반 등)은 다시 던진다. REQUIRES_NEW 경계 안에서
     * 삼키면 {@code UnexpectedRollbackException}이 나므로 경계 밖인 여기서 판단한다.
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
     * 이번 요청이 삭제 예약한 이미지만 커밋 직후 치운다. 실패해도 예약이 남아 {@link PostImageCleaner}가
     * 다시 시도한다(전체 스윕도 그쪽 몫).
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

    /** 관리자가 숨긴 글은 작성자도 고치거나 지울 수 없다(증거 인멸 방지). */
    private void requireNotBlindedUnlessAdmin(Post post, Authentication authentication) {
        if (post.isBlinded() && !OwnershipPolicy.isAdmin(authentication)) {
            throw new AccessDeniedException("관리자가 숨긴 글은 수정·삭제할 수 없습니다. id=" + post.getId());
        }
    }

    /** 삭제되지 않은 글만 찾는다. 소프트 삭제된 글은 수정·추천·삭제의 대상이 아니다. */
    private Post findPost(Long id) {
        return postRepository.findById(id)
                .filter(post -> !post.isDeleted())
                .orElseThrow(() -> new PostNotFoundException(id));
    }

    /** 작성자 본인 또는 관리자만 수정·삭제할 수 있다. */
    private void validateOwner(Post post, Authentication authentication) {
        OwnershipPolicy.validateOwner(authentication, post.getUser(), post.getId());
    }
}
