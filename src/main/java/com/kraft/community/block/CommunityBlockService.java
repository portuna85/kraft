package com.kraft.community.block;

import com.kraft.common.error.ApiException;
import com.kraft.community.user.CommunityUserRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
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

    /** 멱등 — 이미 차단돼 있어도 오류 대신 성공으로 흡수한다(문서 13.5 USER_ALREADY_BLOCKED). */
    public void block(Long blockerUserId, Long blockedUserId) {
        if (blockerUserId.equals(blockedUserId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "COMMUNITY_SELF_BLOCK_NOT_ALLOWED",
                    "자기 자신은 차단할 수 없습니다.");
        }
        if (!communityUserRepository.existsById(blockedUserId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "COMMUNITY_USER_NOT_FOUND", "사용자를 찾을 수 없습니다.");
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
}
