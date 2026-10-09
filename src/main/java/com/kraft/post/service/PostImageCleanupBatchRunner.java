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
 * {@link PostImageCleaner}가 부르는 한 배치(최대 {@link PostImageCleaner#CLEANUP_BATCH_SIZE}행) 실행부. 별도
 * 빈이라야 프록시를 거쳐 {@code REQUIRES_NEW}가 배치마다 실제로 새 트랜잭션을 연다(같은 객체 안 호출이면 한 주기
 * 전체가 긴 트랜잭션 하나로 묶인다).
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

    /** 삭제 예약된 파일 한 배치를 지운다. id 커서로 이어 읽어, 계속 실패하는 행이 맨 앞을 차지해 뒤쪽 정상 행이 굶지 않게 한다. */
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

    /** 지정한 이미지 id만 정리한다(자기 예약분만 처리해 응답 시간이 전체 적체량에 좌우되지 않게). 전체 적체는 {@link PostImageCleaner#cleanPendingDeletions()}가 맡는다. */
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
     * 만료된 ORPHAN 한 배치를 선점한다. 조회와 삭제 사이에 다른 트랜잭션이 게시글에 연결할 수 있어
     * {@link PostImageRepository#claimExpiredOrphanForDeletion}로 "지금도 ORPHAN인가"를 원자적으로 다시 확인한다.
     * 여기서는 선점만 짧게 커밋하고 파일·행 삭제는 호출자가 재시도 안전한 {@link #cleanPendingDeletionsFor}에
     * 맡긴다 — 같은 트랜잭션에서 지우면 커밋 실패 시 이미 지운 파일의 선점만 롤백되어 대장 없는 파일이 남는다.
     * id 커서를 쓰는 이유는 {@link #cleanPendingDeletionsBatch}와 같다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OrphanClaimResult claimExpiredOrphansBatch(long afterId, LocalDateTime threshold) {
        List<PostImage> page = postImageRepository.findAllByStatusAndCreatedAtBeforeAndIdGreaterThanOrderByIdAsc(
                PostImageStatus.ORPHAN, threshold, afterId, PageRequest.of(0, PostImageCleaner.CLEANUP_BATCH_SIZE));
        if (page.isEmpty()) {
            return OrphanClaimResult.empty(afterId);
        }
        // 한 번의 UPDATE로 배치 전체를 선점하고, 그 사이 연결된 id는 아래 재조회로 거른다.
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
