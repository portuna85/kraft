package com.kraft.post.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PostImageRepository extends JpaRepository<PostImage, Long> {

    Optional<PostImage> findByFileName(String fileName);

    boolean existsByFileName(String fileName);

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
     * 만료된 ORPHAN 이미지를 파일 삭제 전에 조건부로 선점한다(B01). 정리 작업이 대상을 조회한
     * 뒤에도 다른 트랜잭션이 그 사이 게시글에 연결(ATTACHED로 전이)했을 수 있으므로, 파일을
     * 실제로 지우기 전에 "지금도 여전히 ORPHAN인가"를 이 원자적 UPDATE로 다시 확인한다.
     * 반환값이 0이면 이미 상태가 바뀐 것이므로 그 이미지는 건드리지 않고 건너뛴다.
     */
    @Modifying
    @Query("UPDATE PostImage p SET p.status = com.kraft.post.domain.PostImageStatus.PENDING_DELETE "
            + "WHERE p.id = :id AND p.status = com.kraft.post.domain.PostImageStatus.ORPHAN "
            + "AND p.createdAt < :threshold")
    int claimExpiredOrphanForDeletion(@Param("id") Long id, @Param("threshold") LocalDateTime threshold);
}
