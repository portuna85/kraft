package com.kraft.community.post;

import com.kraft.recommend.RecommendationSetAttachmentChecker;
import org.springframework.stereotype.Component;

/** {@link RecommendationSetAttachmentChecker} 포트의 커뮤니티 쪽 구현. */
@Component
public class CommunityPostRecommendationAttachmentChecker implements RecommendationSetAttachmentChecker {

    private final CommunityPostRepository communityPostRepository;

    public CommunityPostRecommendationAttachmentChecker(CommunityPostRepository communityPostRepository) {
        this.communityPostRepository = communityPostRepository;
    }

    @Override
    public boolean isAttachedToPost(Long recommendationSetId) {
        return communityPostRepository.existsByRecommendationSetId(recommendationSetId);
    }
}
