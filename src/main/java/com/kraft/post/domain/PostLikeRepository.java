package com.kraft.post.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostLikeRepository extends JpaRepository<PostLike, Long> {

    boolean existsByPostIdAndUserId(Long postId, Long userId);

    /**
     * 파생 삭제 대신 한 문장으로 지운다. PostLike에는 캐스케이드·리스너가 없어 안전하다.
     */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM PostLike p WHERE p.post.id = :postId AND p.user.id = :userId")
    void deleteByPostIdAndUserId(@Param("postId") Long postId, @Param("userId") Long userId);

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM PostLike p WHERE p.post.id = :postId")
    void deleteAllByPostId(@Param("postId") Long postId);

    long countByPostId(Long postId);

    /**
     * 상세 화면이 필요한 추천 수와 "내가 눌렀는지"를 한 번에 센다 — 예전에는 exists와
     * count가 따로 나갔다. {@code userId}에 존재하지 않는 id(익명이면 {@link #NO_USER_ID})를
     * 넘기면 {@code mine}은 0이다.
     */
    @Query("SELECT COUNT(l) AS total, "
            + "COALESCE(SUM(CASE WHEN l.user.id = :userId THEN 1 ELSE 0 END), 0) AS mine "
            + "FROM PostLike l WHERE l.post.id = :postId")
    LikeSummary summarize(@Param("postId") Long postId, @Param("userId") Long userId);

    /** 어떤 회원 id와도 일치하지 않는 값(회원 id는 1부터 시작한다). */
    long NO_USER_ID = 0L;

    interface LikeSummary {
        Long getTotal();

        Long getMine();
    }
}
