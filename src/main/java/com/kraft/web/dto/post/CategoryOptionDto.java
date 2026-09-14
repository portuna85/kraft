package com.kraft.web.dto.post;

/**
 * 게시글 편집 화면(Vue 아일랜드)의 분류 선택지 하나. {@code value}는 {@link
 * com.kraft.domain.post.Category#name()}과 같아 저장 요청의 category 값과 그대로 맞는다.
 */
public record CategoryOptionDto(String value, String title) {
}
