package com.kraft.post.dto;

import java.util.List;

/**
 * 게시글 상세/편집 화면을 그리는 Vue 아일랜드(src/vue/post-edit)의 초기 상태. 이 화면을
 * 서버가 렌더링할 때 딱 한 번 JSON으로 내려주고, 이후 저장·취소는 REST API로만 오간다.
 * <p>
 * {@code userId}는 자동 임시 저장 키를 계정별로 분리하는 데만 쓴다(전체 리뷰 2026-09-26
 * A-FE-03) — 로그인하지 않았으면 null이고, 그 경우 편집 폼 자체를 볼 수 없으므로 실제로는
 * 쓰이지 않는다.
 */
public record PostEditBootstrapDto(
        PostViewDto post,
        List<CategoryOptionDto> categoryOptions,
        boolean authenticated,
        Long userId
) {
}
