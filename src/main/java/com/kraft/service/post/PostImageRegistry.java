package com.kraft.service.post;

import com.kraft.domain.post.Post;
import com.kraft.domain.post.PostImage;
import com.kraft.domain.post.PostImageRepository;
import com.kraft.domain.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * {@code post_images} 대장을 다루는 서비스. 파일 자체를 읽고 쓰는 일은 {@link PostImageService}가
 * 그대로 맡고, 여기서는 "누가 올린 파일인지 / 어느 게시글에 붙어 있는지 / 지워도 되는지"만 다룬다.
 * <p>
 * 두 관심사를 나눈 이유는 {@link PostImageService}가 DB 없이 임시 디렉터리만으로 검증 가능한
 * 순수 파일 유틸리티로 남는 편이 낫기 때문이다(기존 {@code PostImageServiceTest} 15개가 그렇게 돈다).
 */
@RequiredArgsConstructor
@Service
@Transactional(readOnly = true)
public class PostImageRegistry {

    /** 계정 하나가 쓸 수 있는 이미지 저장량 상한. */
    static final long MAX_BYTES_PER_USER = 50L * 1024 * 1024;

    private final PostImageRepository postImageRepository;

    /**
     * 업로드 직후 대장에 올린다. 이 시점에는 아직 어떤 게시글에도 속하지 않으므로 ORPHAN이다.
     */
    @Transactional
    public void register(String url, User owner, long sizeBytes) {
        String fileName = PostImageService.fileNameOf(url);
        if (fileName == null) {
            return;
        }
        postImageRepository.save(PostImage.builder()
                .fileName(fileName)
                .owner(owner)
                .sizeBytes(sizeBytes)
                .build());
    }

    /**
     * 계정별 누적 저장량을 확인한다. 파일 하나당 5MB 제한만으로는 반복 업로드로 디스크를
     * 채우는 것을 막을 수 없다(개선 보고서 F06). 업로드 <b>전에</b> 검사해, 한도를 넘으면
     * 파일을 디스크에 쓰기 전에 거절한다.
     */
    public void validateQuota(User owner, long incomingBytes) {
        long used = postImageRepository.sumSizeBytesByOwnerId(owner.getId());
        if (used + incomingBytes > MAX_BYTES_PER_USER) {
            throw new IllegalArgumentException(
                    "이미지 저장 공간을 모두 사용했습니다(계정당 " + MAX_BYTES_PER_USER / (1024 * 1024)
                            + "MB). 쓰지 않는 이미지가 있는 게시글을 정리한 뒤 다시 시도해 주세요.");
        }
    }

    /**
     * 게시글 저장·수정에서 {@code picture}로 넘어온 이미지를 그 게시글에 연결한다.
     * 이것이 F01의 실제 경계다 — 업로드한 본인의 파일이고, 다른 게시글이 이미 쓰고 있지 않을 때만
     * 통과한다. {@code /images/UUID} 형식만 확인하는 방식으로는 막을 수 없다.
     *
     * @param uploader 지금 요청을 보낸 사용자. 게시글 작성자가 아니라 <b>업로더</b>와 비교해야
     *                 관리자가 남의 글을 수정할 때도 자기 이미지만 붙일 수 있다.
     */
    @Transactional
    public void attach(String url, User uploader, Post post) {
        if (url == null || url.isBlank()) {
            return;
        }

        String fileName = PostImageService.fileNameOf(url);
        if (fileName == null) {
            throw new IllegalArgumentException("이미지 주소가 올바르지 않습니다.");
        }

        PostImage image = postImageRepository.findByFileName(fileName)
                .orElseThrow(() -> new IllegalArgumentException("업로드 기록이 없는 이미지입니다. 이미지를 다시 올려 주세요."));

        if (!image.isOwnedBy(uploader)) {
            throw new AccessDeniedException("직접 업로드한 이미지만 사용할 수 있습니다. fileName=" + fileName);
        }
        if (image.isAttachedToOtherThan(post)) {
            throw new IllegalArgumentException("이미 다른 게시글에서 사용 중인 이미지입니다. 이미지를 다시 올려 주세요.");
        }

        image.attachTo(post);
    }

    /**
     * 이미지 하나의 삭제를 예약한다. 실제 파일은 건드리지 않으므로, 이 트랜잭션이 롤백되면
     * 예약도 함께 사라지고 파일은 그대로 남는다(F05).
     */
    @Transactional
    public void markForDeletion(String url) {
        String fileName = PostImageService.fileNameOf(url);
        if (fileName == null) {
            return;
        }
        postImageRepository.findByFileName(fileName).ifPresent(PostImage::markForDeletion);
    }

    /**
     * 게시글에 붙은 이미지 전부의 삭제를 예약한다. 게시글 삭제 직전에 부른다 —
     * {@code post_id}를 비우는 UPDATE가 게시글 DELETE보다 먼저 DB에 도달해야 FK 제약에
     * 걸리지 않으므로, 표시 후 곧바로 flush한다.
     */
    @Transactional
    public void markPostImagesForDeletion(Long postId) {
        List<PostImage> images = postImageRepository.findAllByPostId(postId);
        images.forEach(PostImage::markForDeletion);
        postImageRepository.flush();
    }
}
