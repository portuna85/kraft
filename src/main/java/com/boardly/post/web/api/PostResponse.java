package com.boardly.post.web.api;

import com.boardly.post.domain.Post;

record PostResponse(Long id, String title, String content, String authorNickname,
                    long viewCount, java.time.LocalDateTime createdAt, java.time.LocalDateTime updatedAt) {
    static PostResponse from(Post p) {
        return new PostResponse(
                p.getId(), p.getTitle(), p.getContent(), p.getAuthor().getNickname(),
                p.getViewCount(), p.getCreatedAt(), p.getUpdatedAt()
        );
    }
}

