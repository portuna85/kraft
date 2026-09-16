package com.kraft.post.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostLikeRepository extends JpaRepository<PostLike, Long> {

    boolean existsByPostIdAndUserId(Long postId, Long userId);

    /**
     * 파생 삭제 대신 한 문장으로 지운다. PostLike에는 캐스케이드·리스너가 없어 안전하다
     * (개선 보고서 "파생 delete 메서드의 엔티티별 삭제").
     */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM PostLike p WHERE p.post.id = :postId AND p.user.id = :userId")
    void deleteByPostIdAndUserId(@Param("postId") Long postId, @Param("userId") Long userId);

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM PostLike p WHERE p.post.id = :postId")
    void deleteAllByPostId(@Param("postId") Long postId);

    long countByPostId(Long postId);
}
