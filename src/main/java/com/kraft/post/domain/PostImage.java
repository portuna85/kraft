package com.kraft.post.domain;

import com.kraft.shared.domain.BaseEntity;
import com.kraft.user.domain.User;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 업로드된 이미지 파일 한 개의 대장(臺帳). {@code Post.picture}는 클라이언트가 요청 본문에
 * 그대로 실어 보내는 문자열이라 그것만으로는 "누가 올린 파일인지"를 알 수 없었다 — 그래서
 * 자기 게시글에 다른 사람의 이미지 URL을 넣고 그 글을 지우면 남의 파일이 사라졌다
 * (개선 보고서 F01).
 * <p>
 * 이 엔티티가 파일명·업로더·연결된 게시글·상태를 기록해 두 가지를 가능하게 한다:
 * <ul>
 * <li>게시글 저장 시 "이 이미지를 이 사람이 올렸는가"를 검사한다(F01).</li>
 * <li>삭제를 {@link PostImageStatus#PENDING_DELETE} 표시로 예약해, 실제 파일 삭제를
 * DB 커밋 이후로 미루고 실패 시 재시도할 수 있게 한다(F05).</li>
 * </ul>
 */
@Getter
@Entity
@NoArgsConstructor
@Table(name = "post_images",
        uniqueConstraints = @UniqueConstraint(name = "UK_POST_IMAGE_FILE_NAME", columnNames = "file_name"))
public class PostImage extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * {@code PostImageService.store()}가 만든 {@code uuid.ext}. 공개 URL({@code /images/uuid.ext})이
     * 아니라 파일명만 저장한다 — URL 접두어는 서빙 방식이 바뀌면 달라질 수 있는 표현이고,
     * 소유권의 실제 대상은 디스크 위의 파일 하나이기 때문이다.
     */
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

    /**
     * 동시 첨부 경쟁을 막는다. 예전에는 조회 후 상태만 바꾸는 방식이라, 같은 미연결 이미지를
     * 서로 다른 두 게시글이 동시에 붙이면 둘 다 검사를 통과해 마지막에 쓴 쪽이 조용히 이겼다.
     * 낙관적 잠금이 있으면 나중에 flush되는 쪽이 {@code OptimisticLockingFailureException}으로
     * 실패한다.
     */
    @Version
    private long version;

    @Builder
    public PostImage(String fileName, User owner, long sizeBytes) {
        this.fileName = fileName;
        this.owner = owner;
        this.sizeBytes = sizeBytes;
        this.status = PostImageStatus.ORPHAN;
    }

    public void attachTo(Post post) {
        this.post = post;
        this.status = PostImageStatus.ATTACHED;
    }

    /**
     * 삭제를 예약한다. {@code post_id}를 함께 비우는 이유는, 게시글을 지우는 흐름에서 이 행이
     * 남아 있으면 {@code FK_POST_IMAGES_POST} 제약이 게시글 삭제를 막기 때문이다.
     */
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
