package com.kraft.post.dto;

import com.kraft.post.domain.Category;
import com.kraft.post.domain.Post;
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
        /** picture의 실제 픽셀 크기(A-FE-09). picture가 없거나 V32 이전에 저장된 글이면 null. */
        Integer pictureWidth,
        Integer pictureHeight,
        String author,
        boolean canManagePost,
        Category category,
        long viewCount,
        long likeCount,
        boolean likedByMe,
        /** 수정 요청이 그대로 돌려보낼 낙관적 잠금 버전. 편집 충돌 감지에 쓴다. */
        Long version,
        /** 소프트 삭제된 글이다. 관리자만 이 글을 열 수 있어, 화면이 "삭제됨" 표시와 복구 버튼을 그린다. */
        boolean deleted,
        /** 관리자 권한. 복구 같은 관리 버튼 노출에 쓴다 — 서버가 판정한 값만 믿는다. */
        boolean canModerate
) {

    /** 삭제·관리 정보 없이 만드는 편의 생성자(테스트와 일반 화면 DTO가 쓴다). */
    public PostViewDto(Long id, String title, String content, String picture, Integer pictureWidth,
                       Integer pictureHeight, String author, boolean canManagePost, Category category,
                       long viewCount, long likeCount, boolean likedByMe, Long version) {
        this(id, title, content, picture, pictureWidth, pictureHeight, author, canManagePost, category,
                viewCount, likeCount, likedByMe, version, false, false);
    }

    public PostViewDto(Post entity, boolean canManagePost, long likeCount, boolean likedByMe,
                       boolean canModerate) {
        this(
                entity.getId(),
                entity.getTitle(),
                entity.getContent(),
                entity.getPicture(),
                entity.getPictureWidth(),
                entity.getPictureHeight(),
                entity.getUser() != null ? entity.getUser().getName() : null,
                canManagePost,
                entity.getCategory(),
                entity.getViewCount(),
                likeCount,
                likedByMe,
                entity.getVersion(),
                entity.isDeleted(),
                canModerate
        );
    }
}
