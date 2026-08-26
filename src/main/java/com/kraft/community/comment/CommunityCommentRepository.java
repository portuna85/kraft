package com.kraft.community.comment;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommunityCommentRepository extends JpaRepository<CommunityComment, Long> {

    // KB-04: 탈퇴 처리 시 기존 댓글의 작성자 표기를 일괄로 익명화한다(CommunityPostRepository와 동일 근거).
    @Modifying
    @Query("update CommunityComment c set c.authorNameSnapshot = :name where c.ownerId = :ownerId")
    int renameAuthorForOwner(@Param("ownerId") Long ownerId, @Param("name") String name);

    Page<CommunityComment> findByPostId(Long postId, Pageable pageable);

    List<CommunityComment> findByOwnerId(Long ownerId);

    // 상위 댓글(parentId is null)만 페이징 대상으로 삼는다 — 답글은 목록 페이지네이션
    // 카운트에서 제외하고 상위 댓글에 중첩해 내려준다(§P1-02). tombstone(deleted=true)된
    // 댓글도 "삭제된 댓글입니다" 마스킹 placeholder로 목록에 그대로 남는다 — 스레드 구조·
    // 페이지 위치를 삭제로 흔들지 않기 위한 의도된 설계(CommunityPostCommentApiTest
    // #deletingComment_tombstonesInsteadOfHardDelete가 이 계약을 검증한다). 따라서
    // community_post_metrics.commentCount(살아있는 댓글만 카운트)와 이 목록의 총계는
    // 서로 다른 것을 센다 — "총 스레드 슬롯 수" vs "실제 댓글 수"로, 불일치가 아니라 각자
    // 다른 질문에 답하는 두 숫자다. B-P0-6에서 실제로 고친 문제는 delete()의 재삭제
    // 멱등성(아래) — 카운터가 삭제 요청 재시도마다 추가로 깎이던 부분이다.
    Page<CommunityComment> findByPostIdAndParentIdIsNull(Long postId, Pageable pageable);

    // 한 페이지의 상위 댓글 id들에 달린 답글을 한 번에 일괄 조회(N+1 방지). M-05: 정렬절이
    // 없으면 DB/쿼리 플랜에 따라 답글 순서가 실행마다 달라질 수 있다 — 상위 댓글과 동일하게
    // createdAt,id 오름차순으로 고정한다(작성 순서 그대로 표시).
    List<CommunityComment> findByPostIdAndParentIdInOrderByCreatedAtAscIdAsc(Long postId, List<Long> parentIds);

    // 부모 댓글에 답글을 붙이거나 삭제(tombstone)할 때 동시 경합을 막기 위한 행 잠금 조회
    // 부모 row lock으로 삭제 경합을 차단한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CommunityComment c where c.id = :id")
    Optional<CommunityComment> findByIdForUpdate(@Param("id") Long id);

    // targetPage 계산용 — 해당 게시글의 상위 댓글(parentId is null) 중 id가 :id 이하인 개수.
    // id는 IDENTITY PK라 생성 순서와 단조 증가가 일치하므로 createdAt 대신 사용해도 안전하다.
    @Query("select count(c) from CommunityComment c "
            + "where c.postId = :postId and c.parentId is null and c.id <= :id")
    long countTopLevelUpToId(@Param("postId") Long postId, @Param("id") Long id);

    /** DATA-DEL-01(docs/improvement.md): 계정 탈퇴 시 게시글별 댓글 수 감산량 계산용 투영. */
    interface PostCommentCount {
        Long getPostId();

        long getCount();
    }

    // DATA-DEL-01(docs/improvement.md): 탈퇴 처리가 이 사용자의 댓글 전체를 엔티티로 읽어
    // eraseForAccountDeletion()을 호출한 뒤 saveAllAndFlush하던 것을, 감산량 계산(이 쿼리)과
    // 익명화(아래 eraseForAccountDeletion 벌크 UPDATE) 두 단계로 나눈다. 이미 tombstone된
    // (deleted=true) 댓글은 community_post_metrics.commentCount에 반영돼 있지 않으므로
    // 감산 대상에서 제외한다 — 기존 엔티티 루프의 `!comment.isDeleted()` 필터와 동일한 조건.
    @Query("select c.postId as postId, count(c) as count from CommunityComment c "
            + "where c.ownerId = :ownerId and c.deleted = false group by c.postId")
    List<PostCommentCount> countNonDeletedGroupedByPostForOwner(@Param("ownerId") Long ownerId);

    // DATA-DEL-01(docs/improvement.md): CommunityComment.eraseForAccountDeletion()과 정확히
    // 같은 리터럴을 쓴다 — 엔티티 메서드는 JPQL에서 호출할 수 없으므로 값을 여기 그대로
    // 옮겨 적었다. 엔티티 쪽 문구를 바꾸면 이 쿼리도 함께 바꿔야 한다.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update CommunityComment c set c.ownerId = null, c.authorNameSnapshot = '삭제된 사용자', "
            + "c.content = '삭제된 댓글입니다.', c.deleted = true where c.ownerId = :ownerId")
    int eraseForAccountDeletion(@Param("ownerId") Long ownerId);
}
