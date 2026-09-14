package com.kraft.comment.dto;

import com.kraft.comment.domain.Comment;
import com.kraft.post.domain.Post;
import com.kraft.shared.domain.ContentPolicy;
import com.kraft.user.domain.User;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CommentSaveRequestDto(

        @NotBlank(message = "내용은 필수입니다.")
        @Size(max = ContentPolicy.COMMENT_CONTENT_MAX_LENGTH, message = "댓글은 {max}자 이하로 입력하세요.")
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
