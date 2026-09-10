package com.kraft.web.dto.comment;

import com.kraft.domain.comment.Comment;
import com.kraft.domain.post.Post;
import com.kraft.domain.user.User;
import jakarta.validation.constraints.NotBlank;

public record CommentSaveRequestDto(

        @NotBlank(message = "내용은 필수입니다.")
        String content
) {

    public Comment toEntity(Post post, User user) {
        return Comment.builder()
                .content(content)
                .post(post)
                .user(user)
                .build();
    }
}
