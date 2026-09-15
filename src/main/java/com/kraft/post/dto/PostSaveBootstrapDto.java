package com.kraft.post.dto;

import java.util.List;

/**
 * 게시글 등록 화면을 그리는 Vue 아일랜드(src/vue/post-save)의 초기 상태. 서버가 화면을
 * 렌더링할 때 한 번 JSON으로 내려주고, 이후 등록은 REST API로만 오간다.
 * <p>
 * {@code author}는 화면에 보여줄 닉네임일 뿐이다 — 저장 요청은 이 값을 담지 않고, 작성자는
 * 서버가 로그인 계정으로 정한다.
 */
public record PostSaveBootstrapDto(
        List<CategoryOptionDto> categoryOptions,
        String author
) {
}
