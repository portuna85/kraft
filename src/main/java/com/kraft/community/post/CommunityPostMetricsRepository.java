package com.kraft.community.post;

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
}
