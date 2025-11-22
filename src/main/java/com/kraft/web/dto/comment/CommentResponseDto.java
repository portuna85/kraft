package com.kraft.web.dto.comment;

import com.kraft.domain.comment.Comment;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 댓글 응답 DTO
 * Record 클래스로 불변성과 간결성 보장
 */
public record CommentResponseDto(
        Long id,
        String content,
        String authorName,
        Long authorId,
        Long parentId,
        int replyCount,
        List<CommentResponseDto> replies,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    /**
     * 정적 팩토리 메서드 - Comment 엔티티에서 생성 (답글 제외)
     */
    public static CommentResponseDto from(Comment comment) {
        return new CommentResponseDto(
                comment.getId(),
                comment.getContent(),
                comment.getAuthor().getName(),
                comment.getAuthor().getId(),
                comment.getParent() != null ? comment.getParent().getId() : null,
                comment.getReplies().size(),
                null, // 답글은 별도 조회
                comment.getCreateAt(),
                comment.getUpdateAt()
        );
    }

    /**
     * 정적 팩토리 메서드 - Comment 엔티티에서 생성 (답글 포함)
     */
    public static CommentResponseDto fromWithReplies(Comment comment) {
        List<CommentResponseDto> replyDtos = comment.getReplies().stream()
                .map(CommentResponseDto::from)
                .collect(Collectors.toList());

        return new CommentResponseDto(
                comment.getId(),
                comment.getContent(),
                comment.getAuthor().getName(),
                comment.getAuthor().getId(),
                comment.getParent() != null ? comment.getParent().getId() : null,
                comment.getReplies().size(),
                replyDtos,
                comment.getCreateAt(),
                comment.getUpdateAt()
        );
    }
}

