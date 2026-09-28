package com.kraft.post.dto;

import com.kraft.post.domain.Category;
import com.kraft.post.domain.Post;
import com.kraft.shared.domain.ContentPolicy;
import com.kraft.user.domain.User;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
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

        // 업로드 응답(ImageUploadResponseDto)이 돌려준 실제 픽셀 크기를 화면이 그대로 되돌려
        // 보낸다(A-FE-09) — <img width height>로 레이아웃 이동(CLS)을 줄이는 용도일 뿐이라
        // picture와의 정합성을 서버가 다시 검증하지는 않는다. 값이 틀려도 레이아웃만
        // 어긋날 뿐 보안·데이터 문제로 이어지지 않는다.
        @Positive(message = "이미지 폭은 양수여야 합니다.")
        Integer pictureWidth,

        @Positive(message = "이미지 높이는 양수여야 합니다.")
        Integer pictureHeight,

        Category category
) {

    public Post toEntity(User user) {
        return Post.builder()
                .title(title)
                .content(content)
                .picture(picture)
                .pictureWidth(picture == null ? null : pictureWidth)
                .pictureHeight(picture == null ? null : pictureHeight)
                .user(user)
                .category(category)
                .build();
    }
}
