package com.kraft.community.reaction;

import java.time.OffsetDateTime;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * KB-02: 좋아요/북마크 insert 시도를 REQUIRES_NEW로 분리된 트랜잭션에서 실행한다.
 * CommunityPostLike/CommunityPostBookmark는 IDENTITY라 save() 시 즉시 INSERT가 나가고,
 * 유니크 위반이 발생하면 Hibernate 세션이 그 트랜잭션을 rollback-only로 마킹한다.
 * 예외를 여기서 catch하지 않고 그대로 흘려보내는 게 핵심이다 — 이 메서드가 예외 없이
 * 정상 반환했는데 트랜잭션이 rollback-only로 마킹돼 있으면, 커밋 시점에 Spring이
 * UnexpectedRollbackException을 던진다(원래 예외를 삼켜버려 호출자가 뭐가 문제인지
 * 알 수 없는 상태가 된다 — 처음 이 문제를 만든 원인). 대신 예외가 이 메서드 밖으로
 * 정상 전파되게 두면, REQUIRES_NEW 트랜잭션은 표준적으로 롤백되고 원래의
 * DataIntegrityViolationException이 그대로 호출자(CommunityReactionService)에 도달한다 —
 * 그 트랜잭션(사전 존재 확인 · 집계 증가)은 이 새 트랜잭션과 별개라 rollback-only로
 * 물들지 않으므로 안전하게 catch해 멱등 흡수할 수 있다. 별도 빈으로 둬야
 * {@code @Transactional}이 프록시를 거쳐 실제로 적용된다(RecommendationSetHistoryService의
 * 클래스 주석과 같은 이유: 같은 빈 안에서 this.method() 호출은 프록시를 우회한다).
 */
@Component
class CommunityReactionWriter {

    private final CommunityPostLikeRepository communityPostLikeRepository;
    private final CommunityPostBookmarkRepository communityPostBookmarkRepository;

    CommunityReactionWriter(CommunityPostLikeRepository communityPostLikeRepository,
                             CommunityPostBookmarkRepository communityPostBookmarkRepository) {
        this.communityPostLikeRepository = communityPostLikeRepository;
        this.communityPostBookmarkRepository = communityPostBookmarkRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void insertLike(Long postId, Long userId, OffsetDateTime now) {
        communityPostLikeRepository.save(new CommunityPostLike(postId, userId, now));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void insertBookmark(Long postId, Long userId, OffsetDateTime now) {
        communityPostBookmarkRepository.save(new CommunityPostBookmark(postId, userId, now));
    }
}
