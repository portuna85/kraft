package com.kraft.post.domain;

/**
 * 존재하지 않는 게시글을 조회했을 때 던진다.
 * <p>
 * REST 경로는 {@code ApiExceptionHandler}가 404 JSON으로, 화면 경로는
 * {@code ViewExceptionHandler}가 404 안내 화면으로 변환한다.
 * 다른 입력 검증 오류와 구별하기 위해 게시글 도메인에 별도 타입을 둔다.
 */
public class PostNotFoundException extends IllegalArgumentException {

    public PostNotFoundException(Long id) {
        super("해당 게시글이 없습니다. id=" + id);
    }
}
