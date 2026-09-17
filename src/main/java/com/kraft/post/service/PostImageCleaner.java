package com.kraft.post.service;

import com.kraft.post.domain.PostImage;
import com.kraft.post.domain.PostImageRepository;
import com.kraft.post.domain.PostImageStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 디스크의 이미지 파일과 {@code post_images} 대장을 맞추는 정리 작업.
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
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class PostImageCleaner {

    /** 게시글에 연결되지 않은 업로드를 이 시간이 지나면 버린다. */
    static final Duration ORPHAN_TTL = Duration.ofHours(24);

    /**
     * 한 번에 조회·처리하는 최대 개수(B10). 대상이 이보다 많으면 여러 번 나눠 부른다 — 전체를
     * 한 트랜잭션에 다 로딩하면 적체가 많을수록 그 주기의 heap·잠금 시간이 함께 늘어난다.
     * 테스트가 배치 경계를 직접 확인할 수 있도록 패키지 가시성으로 둔다.
     */
    static final int CLEANUP_BATCH_SIZE = 200;

    /**
     * 한 주기 안에서 최대 이만큼의 배치만 돈다. 계속 실패하는 행이 있으면(파일 삭제 실패 등)
     * 그 행이 다음 배치 조회에도 다시 걸려 무한 반복할 수 있으므로 상한을 둔다 — 남은 적체는
     * 다음 주기가 이어받는다.
     */
    private static final int MAX_BATCHES_PER_CYCLE = 25;

    private final PostImageRepository postImageRepository;
    private final PostImageService postImageService;

    @Value("${app.upload.cleanup-enabled:true}")
    private boolean enabled;

    /**
     * 주기 실행 진입점. 아래 두 메서드를 같은 객체 안에서 호출하므로 그쪽의
     * {@code REQUIRES_NEW}는 적용되지 않는다(프록시를 거치지 않는 self-invocation) —
     * 그래서 이 메서드가 직접 트랜잭션 경계를 연다. 한 주기의 정리가 한 트랜잭션이면 된다.
     */
    @Scheduled(initialDelayString = "${app.upload.cleanup-initial-delay-ms:600000}",
            fixedDelayString = "${app.upload.cleanup-interval-ms:3600000}")
    @Transactional
    public void clean() {
        if (!enabled) {
            return;
        }
        cleanPendingDeletions();
        cleanExpiredOrphans();
    }

    /**
     * 삭제가 예약된 파일을 실제로 지운다. 게시글 수정·삭제 커밋 직후에도 곧바로 불린다
     * ({@code PostService}의 {@code AfterCommit}) — 그때는 이미 바깥 트랜잭션이 끝난 뒤이므로
     * 자기 트랜잭션을 새로 연다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int cleanPendingDeletions() {
        int total = 0;
        for (int batch = 0; batch < MAX_BATCHES_PER_CYCLE; batch++) {
            List<PostImage> page = postImageRepository.findAllByStatus(
                    PostImageStatus.PENDING_DELETE, PageRequest.of(0, CLEANUP_BATCH_SIZE));
            if (page.isEmpty()) {
                break;
            }
            total += deleteAll(page);
            if (page.size() < CLEANUP_BATCH_SIZE) {
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
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int cleanPendingDeletionsFor(List<Long> imageIds) {
        if (imageIds.isEmpty()) {
            return 0;
        }
        return deleteAll(postImageRepository.findAllByIdInAndStatus(imageIds, PostImageStatus.PENDING_DELETE));
    }

    /**
     * ORPHAN 조회와 실제 파일 삭제 사이에 다른 트랜잭션이 같은 이미지를 게시글에 연결(ATTACHED로
     * 전이)할 수 있다(B01). 파일을 지우기 전에 {@link PostImageRepository#claimExpiredOrphanForDeletion}로
     * "지금도 여전히 ORPHAN인가"를 원자적으로 다시 확인해, 그 사이 연결된 이미지는 건드리지 않고
     * 건너뛴다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int cleanExpiredOrphans() {
        LocalDateTime threshold = LocalDateTime.now().minus(ORPHAN_TTL);
        int total = 0;
        for (int batch = 0; batch < MAX_BATCHES_PER_CYCLE; batch++) {
            List<PostImage> page = postImageRepository.findAllByStatusAndCreatedAtBefore(
                    PostImageStatus.ORPHAN, threshold, PageRequest.of(0, CLEANUP_BATCH_SIZE));
            if (page.isEmpty()) {
                break;
            }
            for (PostImage image : page) {
                if (postImageRepository.claimExpiredOrphanForDeletion(image.getId(), threshold) == 0) {
                    // 조회 이후 다른 트랜잭션이 먼저 연결했다 — 파일을 지우면 안 된다.
                    continue;
                }
                total += deleteOne(image) ? 1 : 0;
            }
            if (page.size() < CLEANUP_BATCH_SIZE) {
                break;
            }
        }
        return total;
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
