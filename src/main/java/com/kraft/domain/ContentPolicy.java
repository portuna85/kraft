package com.kraft.domain;

/**
 * 게시글·댓글 본문의 길이 정책.
 * <p>
 * 두 본문은 모두 {@code TEXT} 컬럼이고, MariaDB의 {@code TEXT}는 <b>문자 수가 아니라
 * 65,535바이트</b>를 담는다. UTF-8에서 한글은 한 자에 3바이트를 쓰므로 "몇 자까지 되는가"는
 * 내용에 따라 달라진다 — 예전에는 최대 길이 검증이 아예 없어서, 그 경계를 넘는 본문은 입력
 * 검증이 아니라 DB 저장 실패로 드러났다.
 * <p>
 * 여기서 "자"의 단위는 {@code @Size}가 세는 단위, 즉 자바 {@code char}(UTF-16 코드 단위)다.
 * 그 한 단위가 UTF-8에서 차지하는 최대 바이트 수는 <b>4가 아니라 3</b>이다: 3바이트를 쓰는
 * 문자(한글·한자 등 BMP 영역)는 {@code char} 하나지만, 4바이트를 쓰는 문자(이모지 등)는
 * 서러게이트 쌍이라 {@code char} 두 개를 차지해 코드 단위당 2바이트꼴이기 때문이다.
 * <p>
 * 그래서 아래 값은 최악의 경우를 <b>문자당 3바이트</b>로 잡고 {@code TEXT} 한도 안에 들도록
 * 정했다. 게시글 10,000자는 최대 30,000바이트, 댓글 1,000자는 최대 3,000바이트다.
 * 한도를 꽉 채우는 대신(65,535 ÷ 3 = 21,845자) 읽고 쓰기 좋은 제품 기준으로 정한 값이므로,
 * 더 긴 글이 필요해지면 이 상수만 올리면 된다.
 * <p>
 * 제목은 {@code VARCHAR(255)}이고 MariaDB의 {@code VARCHAR(n)}은 바이트가 아니라 문자 수
 * 기준이므로, 제목의 {@code @Size(max = 255)}는 컬럼과 이미 정확히 일치한다.
 */
public final class ContentPolicy {

    /** 게시글 본문의 최대 길이(문자 수). */
    public static final int POST_CONTENT_MAX_LENGTH = 10_000;

    /** 댓글 본문의 최대 길이(문자 수). */
    public static final int COMMENT_CONTENT_MAX_LENGTH = 1_000;

    private ContentPolicy() {
    }
}
