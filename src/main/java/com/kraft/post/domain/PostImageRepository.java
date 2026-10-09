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

    /** 관측용 집계 — 삭제 예약됐지만 아직 실제로 지우지 못한 파일 수(정리 주기의 backlog). */
    long countByStatus(PostImageStatus status);

    /**
     * 후보 파일명 중 실제로 대장에 있는 것만 돌려준다. {@code OrphanFileReconciler}가
     * 파일마다 따로 존재 여부를 묻지 않고 청크 단위로 이 메서드를 한 번씩만 불러, 파일 수만큼
     * 쿼리가 늘지 않게 한다.
     */
    @Query("SELECT p.fileName FROM PostImage p WHERE p.fileName IN :fileNames")
    List<String> findFileNamesIn(@Param("fileNames") List<String> fileNames);

    List<PostImage> findAllByPostId(Long postId);

    List<PostImage> findAllByIdInAndStatus(List<Long> ids, PostImageStatus status);

    /**
     * id 커서 방식 배치 조회. 대상 전체가 아니라 {@code pageable}만큼만 가져오고, 호출하는
     * 쪽({@code PostImageCleanupBatchRunner})이 여러 번 나눠 부른다. 매 배치를 같은 페이지(0)로
     * 다시 부르면 계속 실패해 상태가 그대로인 행이 항상 맨 앞에 걸려 뒤쪽의 정상 행이 한 주기
     * (최대 25배치) 동안 전혀 처리되지 못할 수 있다. id가 이전 배치의 마지막 id보다 큰 것만
     * 가져와 실패한 행을 지나쳐 진행한다.
     */
    List<PostImage> findAllByStatusAndIdGreaterThanOrderByIdAsc(PostImageStatus status, Long id, Pageable pageable);

    /** {@link #findAllByStatusAndIdGreaterThanOrderByIdAsc}와 같은 이유로 ORPHAN 정리에도 커서를 쓴다. */
    List<PostImage> findAllByStatusAndCreatedAtBeforeAndIdGreaterThanOrderByIdAsc(
            PostImageStatus status, LocalDateTime threshold, Long id, Pageable pageable);

    /**
     * 한 계정이 현재 차지하고 있는 저장량(바이트). 삭제 예약된 파일은 곧 사라지므로 제외한다.
     * 소프트 삭제된 글의 이미지도 뺀다 — 복구를 위해 ATTACHED로 남아 있지만 사용자 눈에는 이미 지운
     * 글이라, 그대로 세면 "게시글을 정리하세요"는 안내와 실제 쿼터가 보관 기간 동안 어긋난다. 글에
     * 붙지 않은 이미지(post가 null)는 그대로 센다.
     */
    @Query("SELECT COALESCE(SUM(i.sizeBytes), 0) FROM PostImage i LEFT JOIN i.post p "
            + "WHERE i.owner.id = :ownerId AND i.status <> com.kraft.post.domain.PostImageStatus.PENDING_DELETE "
            + "AND (p IS NULL OR p.deletedAt IS NULL)")
    long sumSizeBytesByOwnerId(@Param("ownerId") Long ownerId);

    /**
     * 만료된 ORPHAN 이미지를 파일 삭제 전에 조건부로 선점한다. 정리 작업이 대상을 조회한
     * 뒤에도 다른 트랜잭션이 그 사이 게시글에 연결(ATTACHED로 전이)했을 수 있으므로, 파일을
     * 실제로 지우기 전에 "지금도 여전히 ORPHAN인가"를 이 원자적 UPDATE로 다시 확인한다.
     * 반환값이 0이면 이미 상태가 바뀐 것이므로 그 이미지는 건드리지 않고 건너뛴다.
     * <p>
     * {@code version}도 함께 올린다 — 이 UPDATE 이전에 이미 엔티티를 읽어
     * 둔 attach 트랜잭션(예: {@code PostImageRegistry.attach}가 이 이미지를 findByFileName으로
     * 이미 들고 있는 경우)이 있다면, 그 트랜잭션이 나중에 flush될 때 낙관적 잠금이 버전 불일치로
     * 실패해야 한다. version을 그대로 두면 벌크 UPDATE가 영속성 컨텍스트를 갱신하지 않는다는
     * JPA의 일반적 특성과 맞물려, attach가 이 선점을 전혀 모른 채 그대로 성공할 수 있었다.
     */
    @Modifying
    @Query("UPDATE PostImage p SET p.status = com.kraft.post.domain.PostImageStatus.PENDING_DELETE, "
            + "p.version = p.version + 1 "
            + "WHERE p.id = :id AND p.status = com.kraft.post.domain.PostImageStatus.ORPHAN "
            + "AND p.createdAt < :threshold")
    int claimExpiredOrphanForDeletion(@Param("id") Long id, @Param("threshold") LocalDateTime threshold);

    /**
     * {@link #claimExpiredOrphanForDeletion}의 배치판 — 한 배치(최대
     * {@code PostImageCleaner.CLEANUP_BATCH_SIZE}건)를 건당 UPDATE 대신 한 번의 UPDATE로
     * 선점한다. 이 UPDATE만으로는 그 사이 다른 트랜잭션이 연결(ATTACHED로 전이)해 조건에서
     * 빠진 id를 구분할 수 없으므로, 호출하는 쪽이 {@link #findIdsByIdInAndStatus}로 실제로
     * PENDING_DELETE가 된 id만 다시 가려낸다.
     */
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
