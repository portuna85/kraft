package com.kraft.web.dto.post;

import com.kraft.domain.post.Post;

/**
 * 게시글 응답 DTO
 * Record 클래스로 불변성과 간결성 보장
 */
public record PostResponseDto(
        Long id,
        String title,
        String content,
        String author,
        Long viewCount
) {
    /**
     * 정적 팩토리 메서드 - Post 엔티티에서 생성
     */
    public static PostResponseDto from(Post post) {
        return new PostResponseDto(
                post.getId(),
                post.getTitle(),
                post.getContent(),
                post.getAuthor().getName(),
                post.getViewCount()
        );
    }
}