package com.kraft.community.post;

import com.kraft.common.error.ApiException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommunityPostService {

    private static final int MAX_PAGE_SIZE = 50;

    private final CommunityPostRepository communityPostRepository;
    private final Clock clock;
    private final Counter versionConflictCounter;

    public CommunityPostService(CommunityPostRepository communityPostRepository, Clock clock,
                                 MeterRegistry meterRegistry) {
        this.communityPostRepository = communityPostRepository;
        this.clock = clock;
        this.versionConflictCounter = Counter.builder("kraft_community_post_version_conflict_total")
                .description("게시글 낙관적 잠금 버전 충돌(409)로 거부된 수정 요청 수")
                .register(meterRegistry);
    }

    @Transactional
    public CommunityPost create(Long ownerId, String authorNickname, CreatePostRequest request) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        return communityPostRepository.save(
                new CommunityPost(ownerId, authorNickname, request.title(), request.content(), now, now));
    }

    @Transactional(readOnly = true)
    public CommunityPost get(Long postId) {
        return communityPostRepository.findById(postId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "COMMUNITY_POST_NOT_FOUND",
                        "게시글을 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public Page<CommunityPost> list(int page, int size) {
        int clampedPage = Math.max(0, page);
        int clampedSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        return communityPostRepository.findAll(
                PageRequest.of(clampedPage, clampedSize, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
    }

    @Transactional
    public CommunityPost update(Long ownerId, Long postId, UpdatePostRequest request) {
        CommunityPost post = get(postId);
        requireOwner(post, ownerId);
        if (post.getVersion() != request.expectedVersion()) {
            throw versionConflict(null);
        }
        post.update(request.title(), request.content(), OffsetDateTime.now(clock));
        try {
            return communityPostRepository.saveAndFlush(post);
        } catch (DataAccessException raceLostAfterCheck) {
            // 버전 사전 검증 이후에도 동시 수정이 끼어든 경우(§6 개선②) — 광역 예외 대신
            // 이 리소스에 특정된 409로 변환한다. Hibernate의 자체 행 수 검증은
            // ObjectOptimisticLockingFailureException으로 오지만, 실 MariaDB에서는 드라이버가
            // "Record has changed since last read"(1020)를 먼저 던져 JpaSystemException으로도
            // 온다는 사실을 Testcontainers 동시성 테스트로 확인했다 — 두 경로 모두 이 시점에서는
            // 버전 경합 외의 원인이 있을 수 없으므로 DataAccessException을 폭넓게 잡는다.
            throw versionConflict(raceLostAfterCheck);
        }
    }

    @Transactional
    public void delete(Long ownerId, Long postId, long expectedVersion) {
        CommunityPost post = get(postId);
        requireOwner(post, ownerId);
        if (post.getVersion() != expectedVersion) {
            throw versionConflict(null);
        }
        try {
            communityPostRepository.delete(post);
            communityPostRepository.flush();
        } catch (DataAccessException raceLostAfterCheck) {
            // update()와 동일한 이유(§6 개선②, §P1-04) — 버전 사전 검증 이후 삭제 flush
            // 시점에 끼어든 동시 수정/삭제 경합을 광역 500 대신 이 리소스에 특정된 409로 변환한다.
            throw versionConflict(raceLostAfterCheck);
        }
    }

    private void requireOwner(CommunityPost post, Long ownerId) {
        if (!post.getOwnerId().equals(ownerId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "COMMUNITY_POST_NOT_OWNER", "본인 게시글만 수정·삭제할 수 있습니다.");
        }
    }

    // B-08: update()/delete() 양쪽에서 사전 검증(버전 불일치)과 사후 경합(저장/삭제 시점에
    // 끼어든 동시 수정) 모두 같은 카운터 증가 + 같은 409 메시지를 냈다 — cause를 null로
    // 넘기면 사전 검증 경로, 실제 예외를 넘기면 사후 경합 경로다.
    private ApiException versionConflict(DataAccessException cause) {
        versionConflictCounter.increment();
        return cause == null
                ? new ApiException(HttpStatus.CONFLICT, "COMMUNITY_POST_VERSION_CONFLICT",
                        "다른 곳에서 먼저 수정되었습니다. 새로고침 후 다시 시도하세요.")
                : new ApiException(HttpStatus.CONFLICT, "COMMUNITY_POST_VERSION_CONFLICT",
                        "다른 곳에서 먼저 수정되었습니다. 새로고침 후 다시 시도하세요.", cause);
    }
}
