package com.kraft.community.user;

import com.kraft.common.error.ApiException;
import com.kraft.community.comment.CommunityCommentRepository;
import com.kraft.community.post.CommunityPostRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * KB-04: 탈퇴 처리 — 계정 행을 잠그고 닉네임을 익명화한 뒤, 그 사용자가 쓴 기존 게시글·댓글의
 * 작성자 표기까지 같은 값으로 일괄 재작성한다(README가 광고하는 tombstone 정책을 실제로 이행).
 * 저장 번호·추천 이력 등 ownerId로 연결된 다른 데이터는 그대로 남는다 — adr/0001에서
 * 의도적으로 이번 범위 밖으로 뒀다.
 */
@Service
public class CommunityWithdrawalService {

    private final CommunityUserRepository communityUserRepository;
    private final CommunityPostRepository communityPostRepository;
    private final CommunityCommentRepository communityCommentRepository;
    private final Clock clock;

    public CommunityWithdrawalService(CommunityUserRepository communityUserRepository,
                                       CommunityPostRepository communityPostRepository,
                                       CommunityCommentRepository communityCommentRepository,
                                       Clock clock) {
        this.communityUserRepository = communityUserRepository;
        this.communityPostRepository = communityPostRepository;
        this.communityCommentRepository = communityCommentRepository;
        this.clock = clock;
    }

    @Transactional
    public void withdraw(Long userId) {
        CommunityUser user = communityUserRepository.lockById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "COMMUNITY_USER_NOT_FOUND",
                        "사용자를 찾을 수 없습니다."));
        if (user.isWithdrawn()) {
            return;
        }
        String pseudonymizedNickname = "탈퇴한 사용자#" + user.getId();
        user.withdraw(pseudonymizedNickname, OffsetDateTime.now(clock));
        communityUserRepository.save(user);
        communityPostRepository.renameAuthorForOwner(userId, pseudonymizedNickname);
        communityCommentRepository.renameAuthorForOwner(userId, pseudonymizedNickname);
    }
}
