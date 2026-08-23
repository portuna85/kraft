package com.kraft.community.block;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public interface CommunityUserBlockRepository extends JpaRepository<CommunityUserBlock, Long> {

    Optional<CommunityUserBlock> findByBlockerUserIdAndBlockedUserId(Long blockerUserId, Long blockedUserId);

    boolean existsByBlockerUserIdAndBlockedUserId(Long blockerUserId, Long blockedUserId);

    /**
     * BE-PERF-04(docs/improvement.md): {@code CommunityBlockService.isBlockedEitherWay}가
     * 방향별로 {@code existsByBlockerUserIdAndBlockedUserId}를 최대 2회(단축 평가로 1회일 수도
     * 있음) 부르던 것을 단일 쿼리로 합친다. 댓글·좋아요·북마크 등 커뮤니티 쓰기 경로마다
     * 실행되는 쿼리라 왕복 수를 줄이는 효과가 있다. C-1의 상호 차단 의미론(A→B 또는 B→A 둘
     * 중 하나라도 있으면 차단)은 OR 조건으로 그대로 유지한다 — 로컬 MariaDB `EXPLAIN`으로 실측
     * 확인: 옵티마이저가 이 OR을 유니크 인덱스
     * {@code uk_community_user_blocks_blocker_blocked(blocker_user_id, blocked_user_id)} 위의
     * 단일 range 스캔으로 접어 `Using where; Using index`(커버링 인덱스, 테이블 접근 없음)로
     * 실행한다 — 풀스캔도, 별도 index merge도 아니다.
     */
    @Query("select case when count(b) > 0 then true else false end from CommunityUserBlock b "
            + "where (b.blockerUserId = :userA and b.blockedUserId = :userB) "
            + "or (b.blockerUserId = :userB and b.blockedUserId = :userA)")
    boolean existsBlockedEitherWay(@Param("userA") Long userA, @Param("userB") Long userB);

    List<CommunityUserBlock> findByBlockerUserId(Long blockerUserId);

    /**
     * I-17: {@code /me/blocked-users}가 무제한 전체 목록을 반환하던 것을 막는다 —
     * {@code /me/interactions}의 {@code postIds} 상한(100, KB-10)과 같은 값으로 맞춘다.
     * 프런트는 이 ID 목록을 차단 여부 필터링(Set 멤버십 확인)에만 쓰고 "차단 관리" 화면을
     * 별도로 렌더하지 않으므로, 페이지네이션이 아니라 최신 순 상한이면 충분하다.
     */
    List<CommunityUserBlock> findTop100ByBlockerUserIdOrderByCreatedAtDesc(Long blockerUserId);

    void deleteByBlockerUserIdOrBlockedUserId(Long blockerUserId, Long blockedUserId);

    void deleteByBlockerUserIdAndBlockedUserId(Long blockerUserId, Long blockedUserId);

    /**
     * M-03: find-then-save 대신 실제 upsert로 PUT을 멱등하게 만든다 —
     * {@link com.kraft.saved.SavedNumberClientLockRepository#ensureLockRowExists}와 같은
     * 패턴. 두 동시 요청이 똑같이 "없음"을 본 뒤 둘 다 삽입을 시도해도, ON DUPLICATE KEY
     * UPDATE라 유니크 위반 예외 없이 하나는 삽입되고 하나는 no-op 업데이트로 조용히
     * 흡수된다. UPDATE 절은 실제로 값을 바꾸지 않는 no-op(자기 자신에 대입)이다 —
     * created_at을 덮어쓰면 최초 차단 시각이 매 재요청마다 갱신되는 부작용이 생긴다.
     *
     * <p>REQUIRES_NEW로 격리하는 이유는 CommunityReactionWriter.deleteLike와 같다 —
     * MariaDB REPEATABLE READ 아래 동시 upsert가 SnapshotIsolationException("Record has
     * changed since last read")을 낼 수 있는데(Testcontainers 실 MariaDB로 확인), 이걸
     * 호출자의 ambient 트랜잭션 안에서 catch하면 그 트랜잭션 전체가 rollback-only로
     * 마킹돼 재시도가 불가능해진다. 짧은 독립 트랜잭션이면 실패해도 그 트랜잭션만
     * 버려지고, 호출자가 완전히 새 트랜잭션으로 재시도할 수 있다.
     */
    @Modifying(clearAutomatically = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query(value = "INSERT INTO community_user_blocks (blocker_user_id, blocked_user_id, created_at) "
            + "VALUES (:blockerUserId, :blockedUserId, :now) "
            + "ON DUPLICATE KEY UPDATE blocked_user_id = blocked_user_id",
            nativeQuery = true)
    void upsertBlock(@Param("blockerUserId") Long blockerUserId, @Param("blockedUserId") Long blockedUserId,
                      @Param("now") OffsetDateTime now);
}
