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
 * 추천 INSERT·삭제·개수 조회를 각자 자기 트랜잭션({@code REQUIRES_NEW})에서 실행한다. "있는지 확인 → INSERT"는
 * 두 요청이 겹치면 둘 다 INSERT를 시도하고, 진 쪽은 유니크 제약({@code UK_POST_LIKE_POST_USER}) 위반을 받는다.
 * 이 예외를 호출 트랜잭션 안에서 잡으면 이미 rollback-only라 커밋 시 {@code UnexpectedRollbackException}이 나므로,
 * INSERT만 떼어내 실패한 쪽만 롤백되게 하고 호출자가 "이미 추천된 상태"로 이어가게 한다.
 */
@RequiredArgsConstructor
@Component
public class PostLikeWriter {

    private final PostLikeRepository postLikeRepository;

    /**
     * 추천을 저장한다. 중복(유니크 제약 위반)이면 {@code DataIntegrityViolationException}이 그대로 올라온다 — 여기서
     * 삼키면 rollback-only 트랜잭션이 커밋 시 {@code UnexpectedRollbackException}을 낸다. 호출자가
     * {@link #isDuplicateLikeConstraint}로 중복과 FK 위반 등 다른 원인을 구분한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void insert(Post post, User user) {
        postLikeRepository.save(PostLike.builder().post(post).user(user).build());
    }

    /**
     * 추천을 지운다. 바깥 트랜잭션에서 지우면 커밋 전이라 별도 트랜잭션인 {@link #countByPostId}가 이 DELETE를 보지
     * 못해 취소 뒤에도 개수가 그대로 남는다. 여기서 먼저 커밋한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void delete(Long postId, Long userId) {
        postLikeRepository.deleteByPostIdAndUserId(postId, userId);
    }

    /** 추천 수를 새 트랜잭션(새 스냅샷)에서 읽는다 — 호출자의 REPEATABLE READ 스냅샷은 방금 커밋된 변경을 못 볼 수 있다. */
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
        // MariaDB는 제약 이름을 그대로, H2는 설명 문자열로 감싸 주므로 포함 여부로 확인한다.
        return name != null && name.toUpperCase(java.util.Locale.ROOT).contains(PostLike.UK_POST_LIKE_POST_USER);
    }
}
