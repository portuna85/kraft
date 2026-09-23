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
 * 디스크의 이미지 파일과 {@code post_images} 대장을 맞추는 정리 작업의 주기 진입점.
 * <p>
 * 두 가지를 치운다:
 * <ul>
 * <li>{@link PostImageStatus#PENDING_DELETE} — 게시글 수정·삭제로 삭제가 예약된 파일.
 * 예약은 DB 커밋으로 확정되고 실제 삭제는 그 뒤에 일어나므로, 커밋 직후 프로세스가 죽어도
 * 예약이 DB에 남아 다음 주기에 다시 시도된다(개선 보고서 F05).</li>
 * <li>{@link PostImageStatus#ORPHAN} 중 만료된 것 — 업로드만 하고 글을 저장하지 않은 파일.
 * 이 정리가 없으면 업로드 반복만으로 디스크가 찬다.</li>
 * </ul>
 * 파일 삭제에 실패하면 행을 남긴다. 다음 주기에 다시 시도하게 하려는 것이다.
 * <p>
 * 배치 하나(최대 {@link #CLEANUP_BATCH_SIZE}행)의 실제 조회·삭제·트랜잭션은
 * {@link PostImageCleanupBatchRunner}가 맡는다(B06) — 이 클래스는 그 배치를 몇 번 반복할지
 * (id 커서 진행, {@link #MAX_BATCHES_PER_CYCLE} 상한)만 결정하는 반복문이며, 그 자체는
 * {@code @Transactional}이 아니다. 배치 호출은 매번 주입받은 빈의 프록시를 거치므로,
 * {@link PostImageCleanupBatchRunner}의 {@code REQUIRES_NEW}가 배치마다 실제로 새 트랜잭션을
 * 연다 — 예전처럼 한 주기 전체(최대 25배치 × 200건, 파일 I/O 포함)가 하나의 긴 트랜잭션으로
 * 묶이지 않는다.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class PostImageCleaner {

    /** 게시글에 연결되지 않은 업로드를 이 시간이 지나면 버린다. */
    static final Duration ORPHAN_TTL = Duration.ofHours(24);

    /**
     * 한 배치에서 조회·처리하는 최대 개수(B10). 대상이 이보다 많으면 여러 번 나눠 부른다 — 전체를
     * 한 트랜잭션에 다 로딩하면 적체가 많을수록 그 주기의 heap·잠금 시간이 함께 늘어난다.
     * 테스트가 배치 경계를 직접 확인할 수 있도록 패키지 가시성으로 둔다.
     */
    static final int CLEANUP_BATCH_SIZE = 200;

    /**
     * 한 주기 안에서 최대 이만큼의 배치만 돈다. 계속 실패하는 행이 있으면(파일 삭제 실패 등)
     * id 커서가 그 행을 지나쳐 진행하므로(B06) 예전처럼 같은 행에 무한히 걸리지는 않지만,
     * 그래도 대기열 전체를 한 주기에서 다 처리한다는 보장은 아니므로 상한을 둔다 — 남은
     * 적체는 다음 주기가 이어받는다.
     */
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

    /**
     * 삭제가 예약된 파일을 전부(최대 {@link #MAX_BATCHES_PER_CYCLE}배치) 지운다. 게시글
     * 수정·삭제 커밋 직후에도 이 배치 반복과는 별개로 {@link #cleanPendingDeletionsFor}가
     * 곧바로 한 번 불린다({@code PostService}의 {@code AfterCommit}).
     */
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

    /**
     * 지정한 이미지 id만 정리한다. {@code PostService}가 게시글 저장·삭제 직후 자신이 방금
     * 삭제 예약한 이미지만 넘긴다 — 그 요청과 무관한 나머지 삭제 대기열까지 매번 훑으면
     * 응답 시간이 그때그때 쌓인 적체량에 좌우된다. 시스템 전체의 밀린 대기열은 예약 작업인
     * {@link #cleanPendingDeletions()}가 전담한다.
     */
    public int cleanPendingDeletionsFor(List<Long> imageIds) {
        return batchRunner.cleanPendingDeletionsFor(imageIds);
    }

    /**
     * 만료된 ORPHAN(업로드만 하고 글을 저장하지 않은 파일)을 전부(최대
     * {@link #MAX_BATCHES_PER_CYCLE}배치) 지운다.
     * <p>
     * 배치마다 선점({@code claimExpiredOrphansBatch})과 실제 삭제({@code cleanPendingDeletionsFor})를
     * 서로 다른 트랜잭션으로 나눠 부른다(개선 보고서 COR-06) — 둘 다 이 클래스가 주입받은
     * {@link PostImageCleanupBatchRunner}의 프록시를 거치는 외부 호출이라, 각자의
     * {@code REQUIRES_NEW}가 실제로 독립된 트랜잭션을 연다. 선점이 먼저 커밋되므로, 뒤이은
     * 삭제가 실패해도 그 행은 PENDING_DELETE로 남아 대장 없는 파일이 생기지 않는다.
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
