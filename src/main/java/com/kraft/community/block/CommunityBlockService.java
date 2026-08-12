package com.kraft.community.block;

import com.kraft.common.error.ApiErrorCode;
import com.kraft.common.error.ApiException;
import com.kraft.community.user.CommunityUserRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CommunityBlockService {

    private static final Logger log = LoggerFactory.getLogger(CommunityBlockService.class);
    private static final int BLOCK_MAX_ATTEMPTS = 3;

    private final CommunityUserBlockRepository communityUserBlockRepository;
    private final CommunityUserRepository communityUserRepository;
    private final Clock clock;

    public CommunityBlockService(CommunityUserBlockRepository communityUserBlockRepository,
                                  CommunityUserRepository communityUserRepository,
                                  Clock clock) {
        this.communityUserBlockRepository = communityUserBlockRepository;
        this.communityUserRepository = communityUserRepository;
        this.clock = clock;
    }

    /**
     * 멱등 — 이미 차단돼 있어도 USER_ALREADY_BLOCKED 오류 대신 성공으로 흡수한다.
     *
     * M-03: find-then-save는 "존재하지 않음을 확인 → 삽입" 사이에 경합 창이 있어, 두 동시
     * 요청이 똑같이 없음을 보고 둘 다 삽입을 시도하면 하나는 유니크 위반(500)으로 실패했다
     * — PUT의 멱등 계약과 어긋난다. upsertBlock(REQUIRES_NEW)으로 대체해 경합 창 자체를
     * 없앤다. 재시도 루프는 CommunityReactionService.unlike()와 같은 이유다 — 같은 유니크
     * 키를 두고 여러 요청이 경합하면 MariaDB가 드물게 SnapshotIsolationException을 던질 수
     * 있는데, 매 재시도가 REQUIRES_NEW 덕에 완전히 새 트랜잭션이라 안전하게 다시 시도할 수
     * 있다(upsert 자체가 멱등이라 재시도해도 중복 행이 생기지 않는다).
     */
    public void block(Long blockerUserId, Long blockedUserId) {
        if (blockerUserId.equals(blockedUserId)) {
            throw new ApiException(ApiErrorCode.COMMUNITY_SELF_BLOCK_NOT_ALLOWED,
                    "자기 자신은 차단할 수 없습니다.");
        }
        if (!communityUserRepository.existsById(blockedUserId)) {
            throw new ApiException(ApiErrorCode.COMMUNITY_USER_NOT_FOUND, "사용자를 찾을 수 없습니다.");
        }

        for (int attempt = 1; attempt <= BLOCK_MAX_ATTEMPTS; attempt++) {
            try {
                communityUserBlockRepository.upsertBlock(blockerUserId, blockedUserId, OffsetDateTime.now(clock));
                return;
            } catch (DataAccessException raceLost) {
                if (attempt == BLOCK_MAX_ATTEMPTS) {
                    throw raceLost;
                }
                log.debug("block 경합으로 재시도: blockerUserId={} blockedUserId={} attempt={}",
                        blockerUserId, blockedUserId, attempt);
                sleepBriefly(attempt);
            }
        }
    }

    // 동시 재시도가 곧바로 다시 충돌하지 않도록 시도 횟수에 비례한 짧은 무작위 지연을 둔다
    // (CommunityReactionService.sleepBriefly와 동일한 이유).
    private void sleepBriefly(int attempt) {
        try {
            Thread.sleep((long) (Math.random() * 10 * attempt));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** 멱등 — 차단돼 있지 않아도 그냥 성공(204)으로 처리한다. */
    public void unblock(Long blockerUserId, Long blockedUserId) {
        communityUserBlockRepository.deleteByBlockerUserIdAndBlockedUserId(blockerUserId, blockedUserId);
    }

    @Transactional(readOnly = true)
    public List<Long> blockedUserIds(Long blockerUserId) {
        return communityUserBlockRepository.findByBlockerUserId(blockerUserId).stream()
                .map(CommunityUserBlock::getBlockedUserId)
                .toList();
    }

    /**
     * C-1: 차단은 방향이 있는 관계지만(A가 B를 차단), 쓰기 경로(댓글·좋아요·북마크)를 막을
     * 때는 방향과 무관하게 둘 중 누구든 서로를 차단했으면 상호작용을 막는다 — "내가 차단한
     * 사람의 글에 내가 댓글을 못 다는" 것뿐 아니라 "나를 차단한 사람의 글에 댓글을 못 다는"
     * 것도 막아야 차단이 실제로 보호 기능을 한다.
     */
    @Transactional(readOnly = true)
    public boolean isBlockedEitherWay(Long userA, Long userB) {
        return communityUserBlockRepository.existsByBlockerUserIdAndBlockedUserId(userA, userB)
                || communityUserBlockRepository.existsByBlockerUserIdAndBlockedUserId(userB, userA);
    }
}
