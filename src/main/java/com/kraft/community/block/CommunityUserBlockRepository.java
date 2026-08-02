package com.kraft.community.block;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommunityUserBlockRepository extends JpaRepository<CommunityUserBlock, Long> {

    Optional<CommunityUserBlock> findByBlockerUserIdAndBlockedUserId(Long blockerUserId, Long blockedUserId);

    List<CommunityUserBlock> findByBlockerUserId(Long blockerUserId);

    void deleteByBlockerUserIdOrBlockedUserId(Long blockerUserId, Long blockedUserId);

    void deleteByBlockerUserIdAndBlockedUserId(Long blockerUserId, Long blockedUserId);
}
