package com.kraft.post.service;

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
    private final UserRepository userRepository;
    private final PostImageService postImageService;

    /**
     * 업로드 파일을 검사하고 대장에 올린다. 이 시점에는 아직 어떤 게시글에도 속하지 않으므로
     * ORPHAN이다.
     * <p>
     * 예전에는 용량 검사({@code validateQuota})와 등록({@code register})이 서로 다른 트랜잭션
     * 호출로 나뉘어 있어, 같은 계정의 동시 업로드 두 건이 모두 검사를 통과한 뒤 각자 등록될 수
     * 있었다(개선 보고서 "업로드 용량 검사 경쟁"). 지금은 같은 트랜잭션 안에서 계정 행 자체를
     * 먼저 잠그고(B07이 추가한 {@link UserRepository#findByIdForUpdate}) 그 안에서 검사·등록까지
     * 끝낸다(B03). 이전에는 이 계정의 기존 {@code PostImage} 행을 전부 잠갔는데, 이미지가 없는
     * 계정은 잠글 행이 없어 첫 업로드 두 건이 경쟁을 통과할 수 있었고, 이미지가 많은 계정은 매
     * 업로드마다 그 행 전체를 잠그는 비용을 치렀다. User 행은 항상 존재하므로 두 문제 모두
     * 사라진다.
     */
    @Transactional
    public void validateQuotaAndRegister(String url, User owner, long sizeBytes) {
        userRepository.findByIdForUpdate(owner.getId());

        long used = postImageRepository.sumSizeBytesByOwnerId(owner.getId());
        if (used + sizeBytes > MAX_BYTES_PER_USER) {
            throw new IllegalArgumentException(
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
                .build());
        // 이 메서드 안에서는 저장이 성공해도, 반환 이후 이 트랜잭션의 최종 커밋 자체가 실패할
        // 수 있다(개선 보고서 "파일 저장 성공 후 최종 커밋 실패 시 대장 없는 파일") — 그 실패는
        // 여기 catch로 잡을 수 없다. BE-23로 파일 쓰기가 이 트랜잭션 밖(PostService.uploadImage)
        // 으로 옮겨가면서, 그 보상도 이 트랜잭션을 실제로 갖고 있는 여기로 함께 옮겼다.
        OnRollback.run(() -> postImageService.deleteIfExists(url));
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
        // 삭제가 예약된 이미지는 정리 작업이 파일을 지우는 도중일 수 있다(B01). 상태만으로
        // 막아 두면, 정리 작업이 파일 삭제 전 조건부로 선점한 뒤에는 이 이미지를 다시 연결할
        // 방법이 아예 없어져 정리와 연결 사이의 경쟁이 성립하지 않는다.
        if (image.getStatus() == PostImageStatus.PENDING_DELETE) {
            throw new IllegalArgumentException("삭제 예정인 이미지입니다. 이미지를 다시 올려 주세요.");
        }

        image.attachTo(post);
    }

    /**
     * 이미지 하나의 삭제를 예약하고 그 이미지의 id를 돌려준다. 실제 파일은 건드리지 않으므로,
     * 이 트랜잭션이 롤백되면 예약도 함께 사라지고 파일은 그대로 남는다.
     * 돌려준 id는 커밋 직후 정리 작업을 <b>이 이미지로만</b> 좁히는 데 쓴다 — 전체 삭제
     * 대기열을 매번 훑지 않기 위해서다.
     */
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

    /**
     * 게시글에 붙은 이미지 전부의 삭제를 예약하고 그 id 목록을 돌려준다. 게시글 삭제 직전에
     * 부른다 — {@code post_id}를 비우는 UPDATE가 게시글 DELETE보다 먼저 DB에 도달해야 FK
     * 제약에 걸리지 않으므로, 표시 후 곧바로 flush한다.
     */
    @Transactional
    public List<Long> markPostImagesForDeletion(Long postId) {
        List<PostImage> images = postImageRepository.findAllByPostId(postId);
        images.forEach(PostImage::markForDeletion);
        postImageRepository.flush();
        return images.stream().map(PostImage::getId).toList();
    }
}
