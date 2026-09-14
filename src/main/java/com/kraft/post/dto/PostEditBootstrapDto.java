package com.kraft.post.dto;

import java.util.List;

/**
 * 게시글 상세/편집 화면을 그리는 Vue 아일랜드(src/vue/post-edit)의 초기 상태. 이 화면을
 * 서버가 렌더링할 때 딱 한 번 JSON으로 내려주고, 이후 저장·취소는 REST API로만 오간다.
 */
public record PostEditBootstrapDto(
        PostViewDto post,
        List<CategoryOptionDto> categoryOptions,
        boolean authenticated
) {
}
