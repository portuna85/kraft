package com.kraft.recommend;

/**
 * 추천 세트가 어딘가에 "첨부"되어 삭제하면 안 되는 상태인지 확인하는 포트.
 * 실제 첨부 개념(커뮤니티 게시글)은 recommend 패키지가 몰라야 하므로(의존 방향 역전 방지),
 * 이 인터페이스만 여기 두고 구현체는 그 개념을 아는 패키지(community.post)가 제공한다.
 */
public interface RecommendationSetAttachmentChecker {

    boolean isAttachedToPost(Long recommendationSetId);
}
