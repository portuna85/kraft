package com.kraft.post.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PostImageRepository extends JpaRepository<PostImage, Long> {

    Optional<PostImage> findByFileName(String fileName);

    List<PostImage> findAllByPostId(Long postId);

    List<PostImage> findAllByStatus(PostImageStatus status);

    List<PostImage> findAllByStatusAndCreatedAtBefore(PostImageStatus status, LocalDateTime threshold);

    /**
     * 한 계정이 현재 차지하고 있는 저장량(바이트). 삭제 예약된 파일은 곧 사라지므로 제외한다.
     */
    @Query("SELECT COALESCE(SUM(i.sizeBytes), 0) FROM PostImage i "
            + "WHERE i.owner.id = :ownerId AND i.status <> com.kraft.post.domain.PostImageStatus.PENDING_DELETE")
    long sumSizeBytesByOwnerId(@Param("ownerId") Long ownerId);
}
