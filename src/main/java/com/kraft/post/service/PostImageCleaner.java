package com.kraft.post.service;

import com.kraft.post.domain.PostImageStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 디스크의 이미지 파일과 {@code post_images} 대장을 맞추는 정리 작업의 주기 진입점. 치우는 것:
 * <ul>
 * <li>{@link PostImageStatus#PENDING_DELETE} — 수정·삭제로 삭제가 예약된 파일(커밋 직후 죽어도 예약이 남아 재시도된다).</li>
 * <li>만료된 {@link PostImageStatus#ORPHAN} — 업로드만 하고 저장하지 않은 파일(없으면 업로드 반복만으로 디스크가 찬다).</li>
 * </ul>
 * 삭제에 실패하면 행을 남겨 다음 주기에 다시 시도한다. 배치 하나의 조회·삭제·트랜잭션은
 * {@link PostImageCleanupBatchRunner}가 맡고(프록시를 거쳐 배치마다 {@code REQUIRES_NEW}), 이 클래스는
 * 반복 횟수({@link #MAX_BATCHES_PER_CYCLE})만 정하며 자신은 {@code @Transactional}이 아니다.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class PostImageCleaner {

    /** 게시글에 연결되지 않은 업로드를 이 시간이 지나면 버린다. */
    static final Duration ORPHAN_TTL = Duration.ofHours(24);

    /** 배치 하나의 최대 행 수(전체를 한 트랜잭션에 올리면 heap·잠금 시간이 늘어난다). 테스트가 경계를 보도록 패키지 가시성. */
    static final int CLEANUP_BATCH_SIZE = 200;

    /** 한 주기에서 돌 최대 배치 수. 남은 적체는 다음 주기가 잇는다(id 커서가 계속 실패하는 행을 지나쳐 진행한다). */
    private static final int MAX_BATCHES_PER_CYCLE = 25;

    private final PostImageCleanupBatchRunner batchRunner;

    @Value("${app.upload.cleanup-enabled:true}")
    private boolean enabled;

    /** 주기 실행 진입점. */
    @Scheduled(initialDelayString = "${app.upload.cleanup-initial-delay-ms:600000}",
            fixedDelayString = "${app.upload.cleanup-interval-ms:3600000}")
    public void clean() {
        if (!enabled) {
            return;
        }
        cleanPendingDeletions();
        cleanExpiredOrphans();
    }

    /** 삭제 예약된 파일을 최대 {@link #MAX_BATCHES_PER_CYCLE}배치까지 지운다(커밋 직후에는 {@link #cleanPendingDeletionsFor}가 따로 불린다). */
    public int cleanPendingDeletions() {
        int total = 0;
        long lastId = 0L;
        for (int batch = 0; batch < MAX_BATCHES_PER_CYCLE; batch++) {
            PostImageCleanupBatchRunner.BatchResult result = batchRunner.cleanPendingDeletionsBatch(lastId);
            if (result.pageSize() == 0) {
                break;
            }
            total += result.deleted();
            lastId = result.lastId();
            if (result.pageSize() < CLEANUP_BATCH_SIZE) {
                break;
            }
        }
        return total;
    }

    /** 지정한 이미지 id만 정리한다. 저장·삭제 직후 자기 예약분만 넘겨, 응답 시간이 전체 적체량에 좌우되지 않게 한다. */
    public int cleanPendingDeletionsFor(List<Long> imageIds) {
        return batchRunner.cleanPendingDeletionsFor(imageIds);
    }

    /**
     * 만료된 ORPHAN을 최대 {@link #MAX_BATCHES_PER_CYCLE}배치까지 지운다. 선점과 삭제는 프록시를 거쳐 서로 다른
     * 트랜잭션이라, 선점이 먼저 커밋되고 삭제가 실패해도 행이 PENDING_DELETE로 남아 대장 없는 파일이 생기지 않는다.
     */
    public int cleanExpiredOrphans() {
        LocalDateTime threshold = LocalDateTime.now().minus(ORPHAN_TTL);
        int total = 0;
        long lastId = 0L;
        for (int batch = 0; batch < MAX_BATCHES_PER_CYCLE; batch++) {
            PostImageCleanupBatchRunner.OrphanClaimResult claimed = batchRunner.claimExpiredOrphansBatch(lastId, threshold);
            if (claimed.pageSize() == 0) {
                break;
            }
            lastId = claimed.lastId();
            total += batchRunner.cleanPendingDeletionsFor(claimed.claimedIds());
            if (claimed.pageSize() < CLEANUP_BATCH_SIZE) {
                break;
            }
        }
        return total;
    }
}
