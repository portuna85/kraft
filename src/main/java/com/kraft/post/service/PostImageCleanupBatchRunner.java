package com.kraft.post.service;

import com.kraft.post.domain.PostImage;
import com.kraft.post.domain.PostImageRepository;
import com.kraft.post.domain.PostImageStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * {@link PostImageCleaner}가 한 주기 안에서 부르는 한 배치(최대
 * {@link PostImageCleaner#CLEANUP_BATCH_SIZE}행) 실행부를 별도 빈으로 분리했다(B06).
 * <p>
 * 예전에는 {@code PostImageCleaner.clean()}이 {@code @Transactional}인 채로 같은 객체 안의
 * {@code cleanPendingDeletions()}/{@code cleanExpiredOrphans()}를 직접 호출했다(self-invocation).
 * 그 두 메서드의 {@code REQUIRES_NEW}는 <b>프록시를 거치지 않아 적용되지 않았고</b>, 결과적으로
 * 한 주기의 정리(최대 25배치 × 200건, 파일 삭제까지 포함) 전체가 {@code clean()}이 연 <b>하나의
 * 긴 트랜잭션</b>으로 실행됐다 — DB 커넥션이 그동안 잡혀 있었다.
 * <p>
 * 이제 {@link PostImageCleaner}는 이 빈을 <b>주입받아</b> 배치 하나씩 호출한다. 다른 빈을 거치는
 * 호출은 프록시를 지나므로, 이 클래스의 {@code REQUIRES_NEW}가 배치마다 실제로 새 트랜잭션을
 * 연다 — 파일 I/O와 DB 커밋이 최대 200행 단위로 묶인다.
 */
@Slf4j
@RequiredArgsConstructor
@Component
class PostImageCleanupBatchRunner {

    private final PostImageRepository postImageRepository;
    private final PostImageService postImageService;

    /** 한 배치의 결과. {@code pageSize}가 배치 크기보다 작으면(또는 0이면) 그 대기열은 바닥났다는 뜻이다. */
    record BatchResult(int deleted, long lastId, int pageSize) {

        static BatchResult empty(long lastId) {
            return new BatchResult(0, lastId, 0);
        }
    }

    /**
     * 삭제가 예약된 파일 한 배치를 지운다. id 커서로 이전 배치의 마지막 id 다음부터 조회해,
     * 계속 실패해 상태가 그대로인 행이 다음 배치 조회를 막지 않게 한다(B06 추가 발견) —
     * {@code findAllByStatus}로 매번 같은 페이지(0)를 다시 보면 실패 행이 항상 맨 앞을 차지해
     * 뒤쪽 정상 행이 한 주기 내내 굶을 수 있었다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BatchResult cleanPendingDeletionsBatch(long afterId) {
        List<PostImage> page = postImageRepository.findAllByStatusAndIdGreaterThanOrderByIdAsc(
                PostImageStatus.PENDING_DELETE, afterId, PageRequest.of(0, PostImageCleaner.CLEANUP_BATCH_SIZE));
        if (page.isEmpty()) {
            return BatchResult.empty(afterId);
        }
        int deleted = deleteAll(page);
        return new BatchResult(deleted, page.get(page.size() - 1).getId(), page.size());
    }

    /**
     * 지정한 이미지 id만 정리한다. {@code PostService}가 게시글 저장·삭제 직후 자신이 방금
     * 삭제 예약한 이미지만 넘긴다 — 그 요청과 무관한 나머지 삭제 대기열까지 매번 훑으면
     * 응답 시간이 그때그때 쌓인 적체량에 좌우된다. 시스템 전체의 밀린 대기열은 예약 작업인
     * {@link PostImageCleaner#cleanPendingDeletions()}가 전담한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int cleanPendingDeletionsFor(List<Long> imageIds) {
        if (imageIds.isEmpty()) {
            return 0;
        }
        return deleteAll(postImageRepository.findAllByIdInAndStatus(imageIds, PostImageStatus.PENDING_DELETE));
    }

    /** 만료된 ORPHAN 한 배치의 선점 결과. {@code pageSize}의 의미는 {@link BatchResult}와 같다. */
    record OrphanClaimResult(List<Long> claimedIds, long lastId, int pageSize) {

        static OrphanClaimResult empty(long lastId) {
            return new OrphanClaimResult(List.of(), lastId, 0);
        }
    }

    /**
     * ORPHAN 조회와 실제 파일 삭제 사이에 다른 트랜잭션이 같은 이미지를 게시글에 연결(ATTACHED로
     * 전이)할 수 있다(B01). {@link PostImageRepository#claimExpiredOrphanForDeletion}로 "지금도
     * 여전히 ORPHAN인가"를 원자적으로 다시 확인해, 그 사이 연결된 이미지는 건드리지 않고 건너뛴다.
     * id 커서를 쓰는 이유는 위 {@link #cleanPendingDeletionsBatch}와 같다(B06).
     * <p>
     * <b>여기서는 선점만 하고 파일은 지우지 않는다</b>(개선 보고서 COR-06). 예전에는 이 메서드
     * 하나가 선점과 파일 삭제를 모두 한 트랜잭션(REQUIRES_NEW) 안에서 했다 — 배치 마지막에 그
     * 트랜잭션의 커밋 자체가 실패하면, 이미 디스크에서 지운 파일(되돌릴 수 없다)의 선점만
     * 통째로 ORPHAN으로 롤백되어 대장 없는 파일이 남았다. 이제 선점은 이 메서드가 짧게 커밋하고,
     * 실제 파일·행 삭제는 호출하는 쪽({@link com.kraft.post.service.PostImageCleaner})이 이미
     * 재시도 안전한(idempotent) {@link #cleanPendingDeletionsFor}에 맡긴다 — 그 단계가 실패해도
     * 행은 PENDING_DELETE로 남아 다음 주기의 {@link #cleanPendingDeletionsBatch}가 이어받는다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OrphanClaimResult claimExpiredOrphansBatch(long afterId, LocalDateTime threshold) {
        List<PostImage> page = postImageRepository.findAllByStatusAndCreatedAtBeforeAndIdGreaterThanOrderByIdAsc(
                PostImageStatus.ORPHAN, threshold, afterId, PageRequest.of(0, PostImageCleaner.CLEANUP_BATCH_SIZE));
        if (page.isEmpty()) {
            return OrphanClaimResult.empty(afterId);
        }
        // 건당 UPDATE 최대 200회 대신 한 번의 UPDATE로 배치 전체를 선점한다(개선 보고서 BE-24).
        // 조건에서 빠진(그 사이 다른 트랜잭션이 연결한) id는 아래 재조회로 걸러진다.
        List<Long> pageIds = page.stream().map(PostImage::getId).toList();
        postImageRepository.claimExpiredOrphansForDeletion(pageIds, threshold);
        List<Long> claimedIds = postImageRepository.findIdsByIdInAndStatus(pageIds, PostImageStatus.PENDING_DELETE);
        return new OrphanClaimResult(claimedIds, page.get(page.size() - 1).getId(), page.size());
    }

    private int deleteAll(List<PostImage> images) {
        int deleted = 0;
        for (PostImage image : images) {
            deleted += deleteOne(image) ? 1 : 0;
        }
        return deleted;
    }

    private boolean deleteOne(PostImage image) {
        try {
            postImageService.deleteIfExists(PostImageService.PUBLIC_PREFIX + image.getFileName());
            postImageRepository.delete(image);
            return true;
        } catch (RuntimeException e) {
            // 행을 남겨 다음 주기에 재시도한다. 한 파일의 실패가 나머지 정리를 막지 않게 한다.
            log.warn("이미지 파일 정리에 실패했습니다. 다음 주기에 다시 시도합니다. fileName={}", image.getFileName(), e);
            return false;
        }
    }
}
