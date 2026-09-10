package com.kraft.web.exception;

/**
 * 존재하지 않는 게시글을 조회했을 때 던진다.
 * <p>
 * {@link IllegalArgumentException}을 상속하므로 REST 경로의 응답은 기존과 동일하게 400이다
 * ({@code ApiExceptionHandler.handleIllegalArgument}). 화면 경로에서만 이 타입을 구분해
 * 404 + 안내 화면으로 처리한다({@code ViewExceptionHandler}) — 모든
 * {@code IllegalArgumentException}을 무조건 404로 치환하지 않기 위해 별도 타입이 필요하다.
 */
public class PostNotFoundException extends IllegalArgumentException {

    public PostNotFoundException(Long id) {
        super("해당 게시글이 없습니다. id=" + id);
    }
}
