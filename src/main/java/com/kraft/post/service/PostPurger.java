package com.kraft.post.service;

import com.kraft.post.domain.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 소프트 삭제 후 보관 기간이 지난 게시글을 영구 삭제한다({@link PostService#purge}). 글 하나는 {@code PostService.purge}의 자기
 * 트랜잭션으로 지워, 한 건이 실패(예: 데드락)해도 앞서 지운 글이 롤백되지 않고 그 한 건만 다음 주기에 다시 시도된다 — 그래서 이
 * 클래스에는 {@code @Transactional}을 붙이지 않는다. 실패한 행을 계속 다시 읽지 않게 offset이 아니라 id 커서로 나아간다.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class PostPurger {

    private final PostRepository postRepository;
    private final PostService postService;

    @Value("${app.post.purge-retention-days:30}")
    private int retentionDays;

    @Value("${app.post.purge-enabled:true}")
    private boolean enabled;

    /** 한 번에 읽는 글 수. 묶음 안에서도 글마다 따로 커밋한다. */
    @Value("${app.post.purge-batch-size:100}")
    private int batchSize;

    @Scheduled(initialDelayString = "${app.post.purge-initial-delay-ms:1800000}",
            fixedDelayString = "${app.post.purge-interval-ms:86400000}")
    public void purgeDeletedPosts() {
        if (!enabled) {
            return;
        }
        purgeDeletedBefore(LocalDateTime.now().minus(Duration.ofDays(retentionDays)));
    }

    /** {@code threshold} 이전에 삭제된 글을 모두 영구 삭제하고 지운 수를 돌려준다(시각을 받는 것은 테스트가 보관 기간 경계를 정하기 위해서다). */
    int purgeDeletedBefore(LocalDateTime threshold) {
        int purged = 0;
        int failed = 0;
        long cursor = 0L;
        while (true) {
            List<Long> ids = postRepository.findIdsDeletedBefore(threshold, cursor, PageRequest.of(0, batchSize));
            if (ids.isEmpty()) {
                break;
            }
            for (Long id : ids) {
                try {
                    if (postService.purge(id, threshold)) {
                        purged++;
                    }
                } catch (RuntimeException e) {
                    failed++;
                    log.warn("삭제된 게시글을 영구 삭제하지 못했습니다. 다음 주기에 다시 시도합니다. postId={}", id, e);
                }
                cursor = id;
            }
        }
        if (purged > 0 || failed > 0) {
            log.info("보관 기간이 지난 삭제 게시글을 영구 삭제했습니다. 성공={}, 실패={}", purged, failed);
        }
        return purged;
    }
}
