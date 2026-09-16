package com.kraft.post.domain;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PostImageRepository extends JpaRepository<PostImage, Long> {

    Optional<PostImage> findByFileName(String fileName);

    List<PostImage> findAllByPostId(Long postId);

    List<PostImage> findAllByStatus(PostImageStatus status);

    List<PostImage> findAllByIdInAndStatus(List<Long> ids, PostImageStatus status);

    List<PostImage> findAllByStatusAndCreatedAtBefore(PostImageStatus status, LocalDateTime threshold);

    /**
     * 한 계정이 현재 차지하고 있는 저장량(바이트). 삭제 예약된 파일은 곧 사라지므로 제외한다.
     */
    @Query("SELECT COALESCE(SUM(i.sizeBytes), 0) FROM PostImage i "
            + "WHERE i.owner.id = :ownerId AND i.status <> com.kraft.post.domain.PostImageStatus.PENDING_DELETE")
    long sumSizeBytesByOwnerId(@Param("ownerId") Long ownerId);

    /**
     * 같은 계정의 기존 이미지 행을 잠근다. 용량 검사(SUM) 자체에는 직접 잠금을 걸 수 없으므로,
     * 검사와 등록 사이에 같은 계정의 동시 업로드가 끼어들지 못하도록 이 잠금으로 대신
     * 직렬화한다. {@code timeout=0}(NOWAIT)이라 이미 잠겨 있으면 기다리지 않고 즉시 실패한다 —
     * 짧게 끝나는 검사에서 오래 대기하게 두지 않기 위해서다.
     * <p>
     * <b>알려진 한계:</b> 이 계정에 기존 행이 하나도 없으면(첫 업로드) 잠글 행이 없어 두 개의
     * 첫 업로드가 동시에 들어오는 경우까지는 막지 못한다. MariaDB(운영)는 인덱스가 걸린
     * {@code owner_id} 범위에 대해 갭 락을 걸 수 있어 이 경우도 직렬화되지만, 모든 환경에서
     * 보장되는 동작은 아니므로 best-effort로 문서화해 둔다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "0"))
    @Query("SELECT i FROM PostImage i WHERE i.owner.id = :ownerId")
    List<PostImage> lockAllByOwnerId(@Param("ownerId") Long ownerId);
}
