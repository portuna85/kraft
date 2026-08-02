package com.kraft.community.reaction;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommunityPostBookmarkRepository extends JpaRepository<CommunityPostBookmark, Long> {

    Optional<CommunityPostBookmark> findByPostIdAndUserId(Long postId, Long userId);

    List<CommunityPostBookmark> findByUserIdAndPostIdIn(Long userId, List<Long> postIds);

    void deleteByUserId(Long userId);

    void deleteByPostIdAndUserId(Long postId, Long userId);
}
