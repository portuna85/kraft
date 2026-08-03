package com.kraft.community.post;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface CommunityPostMetricsRepository extends JpaRepository<CommunityPostMetrics, Long> {

    Optional<CommunityPostMetrics> findByPostId(Long postId);

    // clearAutomatically: 벌크 UPDATE는 영속성 컨텍스트를 거치지 않으므로, 같은 트랜잭션
    // 안에서 이미 로드된(1차 캐시에 있는) CommunityPostMetrics를 그대로 두면 이후 조회가
    // DB의 최신 값 대신 캐시된 옛 값을 돌려준다 — 매 증감 후 컨텍스트를 비워 다음 조회가
    // 항상 DB를 다시 읽게 한다.
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE CommunityPostMetrics m SET m.likeCount = m.likeCount + 1 WHERE m.postId = :postId")
    int incrementLikeCount(@Param("postId") Long postId);

    // clearAutomatically: 벌크 UPDATE는 영속성 컨텍스트를 거치지 않으므로, 같은 트랜잭션
    // 안에서 이미 로드된(1차 캐시에 있는) CommunityPostMetrics를 그대로 두면 이후 조회가
    // DB의 최신 값 대신 캐시된 옛 값을 돌려준다 — 매 증감 후 컨텍스트를 비워 다음 조회가
    // 항상 DB를 다시 읽게 한다.
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE CommunityPostMetrics m SET m.likeCount = m.likeCount - 1 WHERE m.postId = :postId AND m.likeCount > 0")
    int decrementLikeCount(@Param("postId") Long postId);

    // clearAutomatically: 벌크 UPDATE는 영속성 컨텍스트를 거치지 않으므로, 같은 트랜잭션
    // 안에서 이미 로드된(1차 캐시에 있는) CommunityPostMetrics를 그대로 두면 이후 조회가
    // DB의 최신 값 대신 캐시된 옛 값을 돌려준다 — 매 증감 후 컨텍스트를 비워 다음 조회가
    // 항상 DB를 다시 읽게 한다.
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE CommunityPostMetrics m SET m.commentCount = m.commentCount + 1 WHERE m.postId = :postId")
    int incrementCommentCount(@Param("postId") Long postId);

    // clearAutomatically: 벌크 UPDATE는 영속성 컨텍스트를 거치지 않으므로, 같은 트랜잭션
    // 안에서 이미 로드된(1차 캐시에 있는) CommunityPostMetrics를 그대로 두면 이후 조회가
    // DB의 최신 값 대신 캐시된 옛 값을 돌려준다 — 매 증감 후 컨텍스트를 비워 다음 조회가
    // 항상 DB를 다시 읽게 한다.
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE CommunityPostMetrics m SET m.commentCount = m.commentCount - 1 WHERE m.postId = :postId AND m.commentCount > 0")
    int decrementCommentCount(@Param("postId") Long postId);

    // clearAutomatically: 벌크 UPDATE는 영속성 컨텍스트를 거치지 않으므로, 같은 트랜잭션
    // 안에서 이미 로드된(1차 캐시에 있는) CommunityPostMetrics를 그대로 두면 이후 조회가
    // DB의 최신 값 대신 캐시된 옛 값을 돌려준다 — 매 증감 후 컨텍스트를 비워 다음 조회가
    // 항상 DB를 다시 읽게 한다.
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE CommunityPostMetrics m SET m.viewCount = m.viewCount + 1 WHERE m.postId = :postId")
    int incrementViewCount(@Param("postId") Long postId);

    // M-8: 탈퇴 처리가 좋아요마다 decrementLikeCount를 반복 호출하던 N+1을 없앤다.
    // uk_community_post_likes_post_user 제약상 사용자당 게시글 하나에 좋아요는 최대 1개라
    // postIds에 중복이 없으므로 IN 절 한 번으로 각 게시글을 정확히 1씩 감산하면 된다.
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE CommunityPostMetrics m SET m.likeCount = m.likeCount - 1 WHERE m.postId IN :postIds AND m.likeCount > 0")
    int decrementLikeCountForPosts(@Param("postIds") List<Long> postIds);

    // M-8: 탈퇴 처리가 댓글마다 decrementCommentCount를 반복 호출하던 N+1을 없앤다. 댓글은
    // 좋아요와 달리 같은 게시글에 여러 개 있을 수 있어 게시글별로 실제 삭제한 개수(amount)만큼
    // 한 번에 감산한다 — 음수로 내려가지 않도록 CASE로 clamp한다.
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE CommunityPostMetrics m SET m.commentCount = "
            + "CASE WHEN m.commentCount > :amount THEN m.commentCount - :amount ELSE 0 END "
            + "WHERE m.postId = :postId")
    int decrementCommentCountBy(@Param("postId") Long postId, @Param("amount") int amount);
}
