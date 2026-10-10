package com.kraft.post.domain;

import com.kraft.shared.domain.BaseEntity;
import com.kraft.user.domain.User;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 업로드된 이미지 파일 한 개의 대장. {@code Post.picture}는 클라이언트가 보낸 문자열이라 누가 올렸는지 알 수
 * 없으므로, 파일명·업로더·연결된 글·상태를 기록해 게시글 저장 시 소유권을 검사하고, 삭제를
 * {@link PostImageStatus#PENDING_DELETE}로 예약해 실제 파일 삭제를 커밋 이후로 미루고 실패 시 재시도한다.
 */
@Getter
@Entity
@NoArgsConstructor
@Table(name = "post_images",
        uniqueConstraints = @UniqueConstraint(name = "UK_POST_IMAGE_FILE_NAME", columnNames = "file_name"),
        indexes = {
                // 인덱스는 Flyway(V6·V23·V40·V44)와 일치해야 한다 — MariaDbMigrationTest가 대조한다.
                // 업로드 쿼터 합계(sumSizeBytesByOwnerId)가 테이블을 다시 읽지 않고 인덱스만으로 계산한다(V40).
                @Index(name = "IX_POST_IMAGES_OWNER_STATUS_SIZE", columnList = "owner_id, status, size_bytes"),
                // PostImageRepository의 상태 기반 배치 조회·claimExpiredOrphanForDeletion이
                // status·created_at·id를 함께 쓴다(V23).
                @Index(name = "IX_POST_IMAGES_STATUS_CREATED_AT_ID", columnList = "status, created_at, id"),
        })
public class PostImage extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** {@code PostImageService.store()}가 만든 {@code uuid.ext}. URL 접두어는 서빙 방식에 따라 바뀔 수 있어 파일명만 저장한다. */
    @Column(name = "file_name", nullable = false, length = 200)
    private String fileName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    /** 연결된 게시글. 아직 연결되지 않았거나 삭제 예정이면 null이다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id")
    private Post post;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PostImageStatus status;

    /** 저장된 파일의 바이트 수. 계정별 저장량 제한을 계산하는 근거다. */
    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    /** 업로드할 때 서버가 읽은 픽셀 크기. 읽지 못했거나 이 컬럼 도입 전 행이면 null이다. */
    @Column(name = "width")
    private Integer width;

    @Column(name = "height")
    private Integer height;

    /** 동시 첨부 경쟁을 막는 낙관적 잠금 — 같은 미연결 이미지를 두 글이 동시에 붙이면 나중 flush가 실패한다. */
    @Version
    private long version;

    @Builder
    public PostImage(String fileName, User owner, long sizeBytes, Integer width, Integer height) {
        this.fileName = fileName;
        this.owner = owner;
        this.sizeBytes = sizeBytes;
        // 0은 "읽지 못함"(PostImageService.Dimensions.UNKNOWN)이다.
        boolean measured = width != null && height != null && width > 0 && height > 0;
        this.width = measured ? width : null;
        this.height = measured ? height : null;
        this.status = PostImageStatus.ORPHAN;
    }

    public void attachTo(Post post) {
        this.post = post;
        this.status = PostImageStatus.ATTACHED;
    }

    /** 삭제를 예약한다. 게시글 삭제가 {@code FK_POST_IMAGES_POST}에 막히지 않게 {@code post_id}도 비운다. */
    public void markForDeletion() {
        this.post = null;
        this.status = PostImageStatus.PENDING_DELETE;
    }

    public boolean isOwnedBy(User user) {
        return user != null && this.owner != null && this.owner.getId() != null
                && this.owner.getId().equals(user.getId());
    }

    public boolean isAttachedToOtherThan(Post target) {
        return this.post != null && (target == null || !this.post.getId().equals(target.getId()));
    }
}
