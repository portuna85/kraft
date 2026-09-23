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

    boolean existsByFileName(String fileName);

    /** 관측용 집계(O03) — 삭제 예약됐지만 아직 실제로 지우지 못한 파일 수(정리 주기의 backlog). */
    long countByStatus(PostImageStatus status);

    /**
     * 후보 파일명 중 실제로 대장에 있는 것만 돌려준다(B11). {@code OrphanFileReconciler}가
     * 예전에는 디렉터리의 파일마다 {@code existsByFileName}을 따로 불러, 파일 수만큼 쿼리가
     * 늘었다. 청크 단위로 이 메서드를 한 번씩만 불러 왕복 수를 줄인다.
     */
    @Query("SELECT p.fileName FROM PostImage p WHERE p.fileName IN :fileNames")
    List<String> findFileNamesIn(@Param("fileNames") List<String> fileNames);

    List<PostImage> findAllByPostId(Long postId);

    List<PostImage> findAllByIdInAndStatus(List<Long> ids, PostImageStatus status);

    /**
     * 대상 전체가 아니라 {@code pageable}만큼만 가져온다(B10). 정리 대상이 대량으로 쌓이면
     * 예전에는 한 트랜잭션이 전부 로딩해 그만큼 heap·잠금 시간이 늘었다 — 호출하는 쪽
     * ({@code PostImageCleaner})이 여러 번 나눠 부른다.
     */
    List<PostImage> findAllByStatus(PostImageStatus status, Pageable pageable);

    /** {@link #findAllByStatus(PostImageStatus, Pageable)}와 같은 이유로 배치 크기를 받는다(B10). */
    List<PostImage> findAllByStatusAndCreatedAtBefore(PostImageStatus status, LocalDateTime threshold, Pageable pageable);

    /**
     * id 커서 방식 배치 조회(B06). {@link #findAllByStatus}를 매 배치마다 같은 페이지(0)로
     * 다시 부르면, 계속 실패해 상태가 그대로인 행이 항상 맨 앞에 걸려 뒤쪽의 정상 행이 한
     * 주기(최대 25배치) 동안 전혀 처리되지 못할 수 있다. id가 이전 배치의 마지막 id보다 큰
     * 것만 가져와 실패한 행을 지나쳐 진행한다.
     */
    List<PostImage> findAllByStatusAndIdGreaterThanOrderByIdAsc(PostImageStatus status, Long id, Pageable pageable);

    /** {@link #findAllByStatusAndIdGreaterThanOrderByIdAsc}와 같은 이유로 ORPHAN 정리에도 커서를 쓴다(B06). */
    List<PostImage> findAllByStatusAndCreatedAtBeforeAndIdGreaterThanOrderByIdAsc(
            PostImageStatus status, LocalDateTime threshold, Long id, Pageable pageable);

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
     * <p>
     * {@code version}도 함께 올린다(개선 보고서 COR-06) — 이 UPDATE 이전에 이미 엔티티를 읽어
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
}
