package com.kraft.post.domain;

/**
 * 관리자가 숨긴 게시글을 관리자가 아닌 사람이 열었을 때 던진다. 상태 코드는 없는 글과 같은 404다 —
 * 검색엔진 색인에서도 빠진다. 다만 화면은 "삭제되었거나 존재하지 않는다"가 아니라 숨겨진 글이라고
 * 알려 준다({@code ViewExceptionHandler}). REST 경로는 부모 타입의 처리({@code ApiExceptionHandler}의
 * 404)를 그대로 따른다.
 */
public class PostHiddenException extends PostNotFoundException {

    public PostHiddenException(Long id) {
        super(id);
    }
}
