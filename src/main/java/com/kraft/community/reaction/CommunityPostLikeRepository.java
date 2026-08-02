package com.kraft.community.reaction;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommunityPostLikeRepository extends JpaRepository<CommunityPostLike, Long> {

    Optional<CommunityPostLike> findByPostIdAndUserId(Long postId, Long userId);

    List<CommunityPostLike> findByUserIdAndPostIdIn(Long userId, List<Long> postIds);

    List<CommunityPostLike> findByUserId(Long userId);

    // KB-06: 삭제된 행 수를 반환해야 unlike()가 "내가 실제로 지웠을 때만" like_count를
    // 감산할 수 있다 — 파생 delete(이름만으로 자동 생성되는 deleteBy...)는 건수를
    // 반환하더라도 내부적으로 entityManager.remove()를 예약만 하고 flush 시점까지
    // 미룬다. 바로 뒤이어 decrementLikeCount의 clearAutomatically=true가 영속성
    // 컨텍스트를 비우면 그 예약이 flush 없이 통째로 사라져 삭제 자체가 유실된다(실측
    // 확인). 벌크 @Modifying 쿼리로 executeUpdate() 즉시 실행시켜야 이 문제가 없다.
    @Modifying
    @Query("delete from CommunityPostLike l where l.postId = :postId and l.userId = :userId")
    long deleteByPostIdAndUserId(@Param("postId") Long postId, @Param("userId") Long userId);
}
