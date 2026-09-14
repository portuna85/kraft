package com.kraft.post.dto;

import com.kraft.post.domain.Category;
import com.kraft.post.domain.Post;
import com.kraft.shared.domain.ContentPolicy;
import com.kraft.user.domain.User;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PostSaveRequestDto(

        @NotBlank(message = "제목은 필수입니다.")
        @Size(max = 255, message = "제목은 255자 이하로 입력하세요.")
        String title,

        @NotBlank(message = "내용은 필수입니다.")
        @Size(max = ContentPolicy.POST_CONTENT_MAX_LENGTH, message = "내용은 {max}자 이하로 입력하세요.")
        String content,

        @Size(max = 500, message = "이미지 경로가 올바르지 않습니다.")
        String picture,

        Category category
) {

    public Post toEntity(User user) {
        return Post.builder()
                .title(title)
                .content(content)
                .picture(picture)
                .user(user)
                .category(category)
                .build();
    }
}
