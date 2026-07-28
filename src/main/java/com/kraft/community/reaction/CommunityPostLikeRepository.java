package com.kraft.community.reaction;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommunityPostLikeRepository extends JpaRepository<CommunityPostLike, Long> {

    Optional<CommunityPostLike> findByPostIdAndUserId(Long postId, Long userId);

    List<CommunityPostLike> findByUserIdAndPostIdIn(Long userId, List<Long> postIds);

    void deleteByPostIdAndUserId(Long postId, Long userId);
}
