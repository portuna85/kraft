package com.kraft.community.block;

import com.kraft.common.error.ApiErrorCode;
import com.kraft.common.error.ApiException;
import com.kraft.community.user.CommunityUserRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CommunityBlockService {

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

    /** 멱등 — 이미 차단돼 있어도 USER_ALREADY_BLOCKED 오류 대신 성공으로 흡수한다. */
    public void block(Long blockerUserId, Long blockedUserId) {
        if (blockerUserId.equals(blockedUserId)) {
            throw new ApiException(ApiErrorCode.COMMUNITY_SELF_BLOCK_NOT_ALLOWED,
                    "자기 자신은 차단할 수 없습니다.");
        }
        if (!communityUserRepository.existsById(blockedUserId)) {
            throw new ApiException(ApiErrorCode.COMMUNITY_USER_NOT_FOUND, "사용자를 찾을 수 없습니다.");
        }
        if (communityUserBlockRepository.findByBlockerUserIdAndBlockedUserId(blockerUserId, blockedUserId).isEmpty()) {
            communityUserBlockRepository.save(
                    new CommunityUserBlock(blockerUserId, blockedUserId, OffsetDateTime.now(clock)));
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
