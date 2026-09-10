package com.kraft.web.dto.post;

import com.kraft.domain.post.Post;

/**
 * 게시글 상세 <b>화면 전용</b> 응답. 공개 REST 응답({@link PostResponseDto})에 권한 필드를
 * 추가하지 않기 위해 별도로 둔다 — 화면은 서버가 판정한 {@code canManagePost}로 관리 버튼을
 * 노출하고, API는 기존 계약을 그대로 유지한다.
 */
public record PostViewDto(
        Long id,
        String title,
        String content,
        String picture,
        String author,
        boolean canManagePost
) {

    public PostViewDto(Post entity, boolean canManagePost) {
        this(
                entity.getId(),
                entity.getTitle(),
                entity.getContent(),
                entity.getPicture(),
                entity.getUser() != null ? entity.getUser().getName() : null,
                canManagePost
        );
    }
}
