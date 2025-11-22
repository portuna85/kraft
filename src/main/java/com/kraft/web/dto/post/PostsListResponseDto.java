package com.kraft.web.dto.post;

import com.kraft.domain.post.Post;

import java.time.LocalDateTime;

/**
 * 게시글 목록 응답 DTO
 * Record 클래스로 불변성과 간결성 보장
 */
public record PostsListResponseDto(
        Long id,
        String title,
        String author,
        Long viewCount,
        LocalDateTime updateAt
) {
    /**
     * 정적 팩토리 메서드 - Post 엔티티에서 생성
     */
    public static PostsListResponseDto from(Post post) {
        return new PostsListResponseDto(
                post.getId(),
                post.getTitle(),
                post.getAuthor().getName(),
                post.getViewCount(),
                post.getUpdateAt()
        );
    }
}