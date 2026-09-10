package com.kraft.web.dto.post;

import com.kraft.domain.post.Post;
import com.kraft.domain.user.User;
import jakarta.validation.constraints.NotBlank;

public record PostSaveRequestDto(

        @NotBlank(message = "제목은 필수입니다.")
        String title,

        @NotBlank(message = "내용은 필수입니다.")
        String content,

        String picture
) {

    public Post toEntity(User user) {
        return Post.builder()
                .title(title)
                .content(content)
                .picture(picture)
                .user(user)
                .build();
    }
}
