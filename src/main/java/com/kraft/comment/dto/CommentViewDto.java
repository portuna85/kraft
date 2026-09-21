package com.kraft.comment.dto;

import com.kraft.comment.domain.Comment;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 댓글 <b>화면 전용</b> 응답. 커서 페이지 API가 이 형태로만 응답하며, 화면에만 댓글별
 * {@code canManage}를 내려 관리 버튼 노출을 서버 판정에 맞춘다.
 * <p>
 * {@code parentId}가 null이면 최상위 댓글이고, {@code replies}에 그 답글 목록이 실린다.
 * 답글 자신은 2단계까지만 허용하므로 {@code replies}가 항상 빈 리스트다.
 */
public record CommentViewDto(
        Long id,
        Long postId,
        Long parentId,
        String content,
        String author,
        LocalDateTime createdAt,
        boolean canManage,
        List<CommentViewDto> replies,
        /**
         * 편집 충돌 감지에 쓰는 낙관적 잠금 버전(B12). 저장 요청의 {@code version}에 이 값을
         * 그대로 실어 보내면, 그 사이 다른 저장이 있었을 때 서버가 409로 거절한다.
         */
        Long version
) {

    /** 최상위 댓글 생성용. 답글은 아직 채우지 않은 상태로 만들고, 서비스가 나중에 채운다. */
    public CommentViewDto(Comment entity, boolean canManage) {
        this(entity, canManage, List.of());
    }

    public CommentViewDto(Comment entity, boolean canManage, List<CommentViewDto> replies) {
        this(
                entity.getId(),
                entity.getPost() != null ? entity.getPost().getId() : null,
                entity.getParent() != null ? entity.getParent().getId() : null,
                entity.getContent(),
                entity.getUser() != null ? entity.getUser().getName() : null,
                entity.getCreatedAt(),
                canManage,
                replies,
                entity.getVersion()
        );
    }

    /** 답글 목록을 채운 새 인스턴스를 돌려준다. record는 불변이라 필드 하나만 바꿔 복제한다. */
    public CommentViewDto withReplies(List<CommentViewDto> newReplies) {
        return new CommentViewDto(id, postId, parentId, content, author, createdAt, canManage, newReplies, version);
    }
}
