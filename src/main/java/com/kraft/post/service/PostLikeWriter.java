package com.kraft.post.service;

import com.kraft.post.domain.Post;
import com.kraft.post.domain.PostLike;
import com.kraft.post.domain.PostLikeRepository;
import com.kraft.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 추천 INSERT·최종 개수 조회를 <b>자기 트랜잭션에서</b> 실행한다.
 * <p>
 * "이미 눌렀는지 확인 → 없으면 INSERT"는 두 요청이 겹치면 둘 다 "없음"을 읽고 둘 다 INSERT를
 * 시도할 수 있다. 복합 유니크 제약({@code UK_POST_LIKE_POST_USER})이 중복 저장 자체는 막지만,
 * 진 쪽은 제약 위반 예외를 받는다(개선 보고서 F10).
 * <p>
 * 이때 그 예외를 호출한 트랜잭션 안에서 그냥 잡으면 안 된다 — 제약 위반이 난 트랜잭션은 이미
 * rollback-only로 표시되어, 잡고 넘어가도 커밋 시점에 {@code UnexpectedRollbackException}이
 * 난다. {@code REQUIRES_NEW}로 INSERT만 떼어내면 실패한 쪽은 그 작은 트랜잭션만 롤백되고,
 * 호출한 쪽은 "이미 추천된 상태"로 이어갈 수 있다.
 */
@RequiredArgsConstructor
@Component
public class PostLikeWriter {

    private final PostLikeRepository postLikeRepository;

    /**
     * 추천을 저장한다. 같은 (게시글, 사용자) 추천이 이미 있으면(유니크 제약 위반) 여기서
     * {@code DataIntegrityViolationException}이 그대로 올라온다 — 이 트랜잭션만 롤백된다.
     * <p>
     * 여기서 잡아 삼키지 않는 이유는, 위반이 flush 도중 일어나면 이 트랜잭션은 커밋 전에 이미
     * rollback-only로 표시되기 때문이다. 같은 트랜잭션 안에서 예외를 잡고 "정상 반환"해도
     * 커밋 시점에 {@code UnexpectedRollbackException}이 난다 — 호출한 쪽(REQUIRES_NEW 바깥,
     * 이미 롤백된 트랜잭션의 경계 밖)에서 판단해야 한다. {@link #isDuplicateLikeConstraint}로
     * 유니크 제약(중복 추천)인지 FK 등 다른 원인인지 구분한다(B09) — 예전에는 모든
     * {@code DataIntegrityViolationException}을 중복으로 취급해, 부모 게시글이 막 삭제되어
     * 생긴 FK 위반까지 "이미 추천됨"으로 위장해 조용히 성공 처리했다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void insert(Post post, User user) {
        postLikeRepository.save(PostLike.builder().post(post).user(user).build());
    }

    /**
     * 추천을 지운다. {@link #insert}와 같은 이유로 REQUIRES_NEW다 — 호출한 쪽(바깥) 트랜잭션
     * 안에서 지우면, 그 트랜잭션이 아직 커밋 전인 상태에서 {@link #countByPostId}가 별도
     * 트랜잭션(REQUIRES_NEW)으로 개수를 읽을 때 이 DELETE를 <b>보지 못한다</b> — 서로 다른
     * 트랜잭션·커넥션이라 커밋되지 않은 변경은 원천적으로 보이지 않는다. 실제로 이 문제로
     * "추천을 눌렀다 다시 누르면" 흐름에서 추천 취소 뒤에도 개수가 그대로 남는 회귀가 있었다.
     * INSERT처럼 여기서 먼저 커밋해 두면, 뒤이어 도는 {@code countByPostId}가 항상 그 결과를
     * 볼 수 있다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void delete(Long postId, Long userId) {
        postLikeRepository.deleteByPostIdAndUserId(postId, userId);
    }

    /**
     * 추천 수를 새 트랜잭션에서 읽는다. {@link #insert}·{@link #delete}는 REQUIRES_NEW로
     * 별도 커밋되므로, 호출한 쪽의(더 먼저 시작된) 트랜잭션이 REPEATABLE READ 스냅샷을 이미
     * 잡아 두었다면 방금 커밋된 변경을 못 볼 수 있다(B09). 이 메서드는 항상 새 스냅샷에서
     * 읽어 최신 값을 보장한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public long countByPostId(Long postId) {
        return postLikeRepository.countByPostId(postId);
    }

    /** 이 예외가 중복 추천(유니크 제약 위반)이라 흡수해도 되는지 판단한다. FK 위반 등 다른 원인이면 false. */
    public boolean isDuplicateLikeConstraint(DataIntegrityViolationException e) {
        if (!(e.getCause() instanceof ConstraintViolationException cve)) {
            return false;
        }
        String name = cve.getConstraintName();
        // MariaDB는 제약 이름을 그대로 주지만, H2는 "PUBLIC.UK_..._INDEX PUBLIC.UK_..._INDEX_C"처럼
        // 설명 문자열로 감싸 돌려준다 — 정확히 일치시키지 않고 포함 여부로 확인한다.
        return name != null && name.toUpperCase(java.util.Locale.ROOT).contains(PostLike.UK_POST_LIKE_POST_USER);
    }
}
