package com.kraft.post.service;

import com.kraft.post.domain.Post;
import com.kraft.post.domain.PostLike;
import com.kraft.post.domain.PostLikeRepository;
import com.kraft.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 추천 INSERT만 <b>자기 트랜잭션에서</b> 실행한다.
 * <p>
 * "이미 눌렀는지 확인 → 없으면 INSERT"는 두 요청이 겹치면 둘 다 "없음"을 읽고 둘 다 INSERT를
 * 시도할 수 있다. 복합 유니크 제약({@code UK_POST_LIKE_POST_USER})이 중복 저장 자체는 막지만,
 * 진 쪽은 제약 위반 예외를 받는다(개선 보고서 F10).
 * <p>
 * 이때 그 예외를 호출한 트랜잭션 안에서 그냥 잡으면 안 된다 — 제약 위반이 난 트랜잭션은 이미
 * rollback-only로 표시되어, 잡고 넘어가도 커밋 시점에 {@code UnexpectedRollbackException}이
 * 난다. {@code REQUIRES_NEW}로 INSERT만 떼어내면 실패한 쪽은 그 작은 트랜잭션만 롤백되고,
 * 호출한 쪽은 예외를 잡아 "이미 추천된 상태"로 이어갈 수 있다.
 */
@RequiredArgsConstructor
@Component
public class PostLikeWriter {

    private final PostLikeRepository postLikeRepository;

    /**
     * 추천을 저장한다. 같은 (게시글, 사용자) 추천이 이미 있으면
     * {@code DataIntegrityViolationException}이 올라오며, 이 트랜잭션만 롤백된다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void insert(Post post, User user) {
        postLikeRepository.save(PostLike.builder().post(post).user(user).build());
    }
}
