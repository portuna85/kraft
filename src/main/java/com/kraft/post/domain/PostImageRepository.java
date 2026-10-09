package com.kraft.post.domain;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PostImageRepository extends JpaRepository<PostImage, Long> {

    Optional<PostImage> findByFileName(String fileName);

    /** 관측용 집계(삭제 예약됐지만 아직 지우지 못한 파일 수 등). */
    long countByStatus(PostImageStatus status);

    /** 후보 파일명 중 대장에 있는 것만 돌려준다 — {@code OrphanFileReconciler}가 청크 단위로 한 번씩만 불러 파일 수만큼 쿼리가 늘지 않게. */
    @Query("SELECT p.fileName FROM PostImage p WHERE p.fileName IN :fileNames")
    List<String> findFileNamesIn(@Param("fileNames") List<String> fileNames);

    List<PostImage> findAllByPostId(Long postId);

    List<PostImage> findAllByIdInAndStatus(List<Long> ids, PostImageStatus status);

    /** id 커서 배치 조회: 이전 배치 마지막 id보다 큰 것만 가져와, 계속 실패하는 행에 막히지 않고 진행한다. */
    List<PostImage> findAllByStatusAndIdGreaterThanOrderByIdAsc(PostImageStatus status, Long id, Pageable pageable);

    /** {@link #findAllByStatusAndIdGreaterThanOrderByIdAsc}와 같은 이유로 ORPHAN 정리에도 커서를 쓴다. */
    List<PostImage> findAllByStatusAndCreatedAtBeforeAndIdGreaterThanOrderByIdAsc(
            PostImageStatus status, LocalDateTime threshold, Long id, Pageable pageable);

    /**
     * 한 계정이 차지한 저장량(바이트). 삭제 예약된 파일과 소프트 삭제된 글의 이미지는 사용자에게 이미 지운 것이라
     * 뺀다. 글에 붙지 않은 이미지(post가 null)는 센다.
     */
    @Query("SELECT COALESCE(SUM(i.sizeBytes), 0) FROM PostImage i LEFT JOIN i.post p "
            + "WHERE i.owner.id = :ownerId AND i.status <> com.kraft.post.domain.PostImageStatus.PENDING_DELETE "
            + "AND (p IS NULL OR p.deletedAt IS NULL)")
    long sumSizeBytesByOwnerId(@Param("ownerId") Long ownerId);

    /**
     * 만료된 ORPHAN을 파일 삭제 전에 조건부로 선점한다("지금도 ORPHAN인가"를 원자적으로 재확인). 0이면 그 사이
     * 연결된 것이니 건너뛴다. {@code version}도 올려, 이미 엔티티를 읽어 둔 attach 트랜잭션이 flush 때 낙관적 잠금
     * 실패를 받게 한다(벌크 UPDATE는 영속성 컨텍스트를 갱신하지 않는다).
     */
    @Modifying
    @Query("UPDATE PostImage p SET p.status = com.kraft.post.domain.PostImageStatus.PENDING_DELETE, "
            + "p.version = p.version + 1 "
            + "WHERE p.id = :id AND p.status = com.kraft.post.domain.PostImageStatus.ORPHAN "
            + "AND p.createdAt < :threshold")
    int claimExpiredOrphanForDeletion(@Param("id") Long id, @Param("threshold") LocalDateTime threshold);

    /** {@link #claimExpiredOrphanForDeletion}의 배치판 — 한 번의 UPDATE로 선점하고, 그 사이 연결돼 빠진 id는 호출자가 {@link #findIdsByIdInAndStatus}로 가려낸다. */
    @Modifying
    @Query("UPDATE PostImage p SET p.status = com.kraft.post.domain.PostImageStatus.PENDING_DELETE, "
            + "p.version = p.version + 1 "
            + "WHERE p.id IN :ids AND p.status = com.kraft.post.domain.PostImageStatus.ORPHAN "
            + "AND p.createdAt < :threshold")
    int claimExpiredOrphansForDeletion(@Param("ids") List<Long> ids, @Param("threshold") LocalDateTime threshold);

    /** 위 {@link #claimExpiredOrphansForDeletion} 실행 후, 실제로 선점에 성공한 id만 가려낸다. */
    @Query("SELECT p.id FROM PostImage p WHERE p.id IN :ids AND p.status = :status")
    List<Long> findIdsByIdInAndStatus(@Param("ids") List<Long> ids, @Param("status") PostImageStatus status);
}
