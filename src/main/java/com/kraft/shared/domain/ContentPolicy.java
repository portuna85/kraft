package com.kraft.shared.domain;

/**
 * 게시글·댓글 본문의 길이 정책. 두 본문은 {@code TEXT} 컬럼이고 MariaDB의 {@code TEXT}는 문자 수가 아니라 65,535바이트를
 * 담는다. {@code @Size}가 세는 단위는 자바 {@code char}(UTF-16)이고 그 한 단위는 UTF-8에서 최대 3바이트다(4바이트 문자는
 * 서러게이트 쌍이라 char 둘). 그래서 최악을 문자당 3바이트로 잡아 TEXT 한도 안에 든다 — 게시글 10,000자는 최대 30,000바이트,
 * 댓글 1,000자는 최대 3,000바이트. 한도를 꽉 채우지 않은 제품 기준 값이라 더 긴 글이 필요하면 상수만 올리면 된다. 제목은
 * {@code VARCHAR(255)}(문자 수 기준)라 {@code @Size(max = 255)}가 컬럼과 정확히 일치한다.
 */
public final class ContentPolicy {

    /** 게시글 본문의 최대 길이(문자 수). */
    public static final int POST_CONTENT_MAX_LENGTH = 10_000;

    /** 댓글 본문의 최대 길이(문자 수). */
    public static final int COMMENT_CONTENT_MAX_LENGTH = 1_000;

    private ContentPolicy() {
    }
}
