package com.kraft.post.service;

import com.kraft.shared.exception.BusinessValidationException;
import com.kraft.post.domain.Post;
import com.kraft.post.domain.PostImage;
import com.kraft.post.domain.PostImageRepository;
import com.kraft.post.domain.PostImageStatus;
import com.kraft.shared.transaction.OnRollback;
import com.kraft.user.domain.User;
import com.kraft.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * {@code post_images} 대장 서비스: 누가 올린 파일인지, 어느 글에 붙어 있는지, 지워도 되는지를 다룬다. 파일
 * 자체는 {@link PostImageService}가 맡아, 그쪽이 DB 없이 임시 디렉터리만으로 검증되는 순수 유틸로 남는다.
 */
@RequiredArgsConstructor
@Service
@Transactional(readOnly = true)
public class PostImageRegistry {

    /** 계정 하나가 쓸 수 있는 이미지 저장량 상한. */
    static final long MAX_BYTES_PER_USER = 50L * 1024 * 1024;

    private final PostImageRepository postImageRepository;
    private final UserRepository userRepository;
    private final PostImageService postImageService;

    /**
     * 업로드 파일을 검사해 대장에 올린다(아직 어떤 글에도 속하지 않으므로 ORPHAN). 계정 행
     * ({@link UserRepository#findByIdForUpdate})을 먼저 잠그고 용량 검사·등록을 같은 트랜잭션에서 끝내 동시 업로드가
     * 둘 다 검사를 통과하는 경쟁을 막는다. 계정 행은 항상 있어, 이미지가 없는 계정의 첫 업로드도 막고 이미지 행
     * 전체를 잠그는 비용도 없다.
     */
    public void validateQuotaAndRegister(String url, User owner, long sizeBytes) {
        validateQuotaAndRegister(url, owner, sizeBytes, null, null);
    }

    /** 서버가 측정한 픽셀 크기까지 대장에 남기는 등록. */
    @Transactional
    public void validateQuotaAndRegister(String url, User owner, long sizeBytes, Integer width, Integer height) {
        userRepository.findByIdForUpdate(owner.getId());

        long used = postImageRepository.sumSizeBytesByOwnerId(owner.getId());
        if (used + sizeBytes > MAX_BYTES_PER_USER) {
            throw new BusinessValidationException(
                    "이미지 저장 공간을 모두 사용했습니다(계정당 " + MAX_BYTES_PER_USER / (1024 * 1024)
                            + "MB). 쓰지 않는 이미지가 있는 게시글을 정리한 뒤 다시 시도해 주세요.");
        }

        String fileName = PostImageService.fileNameOf(url);
        if (fileName == null) {
            return;
        }
        postImageRepository.save(PostImage.builder()
                .fileName(fileName)
                .owner(owner)
                .sizeBytes(sizeBytes)
                .width(width)
                .height(height)
                .build());
        // 커밋이 나중에 실패할 수 있어 catch로는 못 잡는다. 파일은 이 트랜잭션 밖(PostService.uploadImage)에서
        // 쓰이므로 보상을 여기서 건다.
        OnRollback.run(() -> postImageService.deleteIfExists(url));
    }

    /**
     * 게시글 저장·수정의 {@code picture}를 그 글에 연결한다. 업로드한 본인의 파일이고 다른 글이 쓰고 있지 않을
     * 때만 통과하는 실제 경계다({@code /images/UUID} 형식 검사로는 못 막는다).
     *
     * @param uploader 요청한 사용자. 글 작성자가 아니라 <b>업로더</b>와 비교해야 관리자도 자기 이미지만 붙인다.
     */
    @Transactional
    public Optional<MeasuredSize> attach(String url, User uploader, Post post) {
        if (url == null || url.isBlank()) {
            return Optional.empty();
        }

        String fileName = PostImageService.fileNameOf(url);
        if (fileName == null) {
            throw new BusinessValidationException("이미지 주소가 올바르지 않습니다.");
        }

        PostImage image = postImageRepository.findByFileName(fileName)
                .orElseThrow(() -> new BusinessValidationException("업로드 기록이 없는 이미지입니다. 이미지를 다시 올려 주세요."));

        if (!image.isOwnedBy(uploader)) {
            throw new AccessDeniedException("직접 업로드한 이미지만 사용할 수 있습니다. fileName=" + fileName);
        }
        if (image.isAttachedToOtherThan(post)) {
            throw new BusinessValidationException("이미 다른 게시글에서 사용 중인 이미지입니다. 이미지를 다시 올려 주세요.");
        }
        // 삭제 예약된 이미지는 정리 작업이 선점한 뒤라 다시 연결할 수 없다.
        if (image.getStatus() == PostImageStatus.PENDING_DELETE) {
            throw new BusinessValidationException("삭제 예정인 이미지입니다. 이미지를 다시 올려 주세요.");
        }

        image.attachTo(post);
        return image.getWidth() == null || image.getHeight() == null
                ? Optional.empty()
                : Optional.of(new MeasuredSize(image.getWidth(), image.getHeight()));
    }

    /** 업로드 때 서버가 측정한 픽셀 크기. 게시글의 {@code <img width height>}는 클라이언트 값이 아니라 이 값을 쓴다. */
    public record MeasuredSize(int width, int height) {
    }

    /** 이미지 하나의 삭제를 예약하고 id를 돌려준다. 파일은 건드리지 않아 롤백되면 예약도 사라진다. 이 id로 커밋 직후 정리를 좁힌다. */
    @Transactional
    public Optional<Long> markForDeletion(String url) {
        String fileName = PostImageService.fileNameOf(url);
        if (fileName == null) {
            return Optional.empty();
        }
        return postImageRepository.findByFileName(fileName)
                .map(image -> {
                    image.markForDeletion();
                    return image.getId();
                });
    }

    /** 게시글에 붙은 이미지 전부의 삭제를 예약해 id를 돌려준다. {@code post_id}를 비우는 UPDATE가 글 DELETE보다 먼저 닿도록 곧바로 flush한다(FK). */
    @Transactional
    public List<Long> markPostImagesForDeletion(Long postId) {
        List<PostImage> images = postImageRepository.findAllByPostId(postId);
        images.forEach(PostImage::markForDeletion);
        postImageRepository.flush();
        return images.stream().map(PostImage::getId).toList();
    }
}
