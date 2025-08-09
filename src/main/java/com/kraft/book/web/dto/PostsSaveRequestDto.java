package com.kraft.book.web.dto;

import com.kraft.book.domain.posts.Posts;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PostsSaveRequestDto {
    @NotBlank(message = "{post.title.notBlank}")
    private String title;

    @NotBlank(message = "{post.author.notBlank}")
    private String author;

    @NotBlank(message = "{post.content.notBlank}")
    private String content;

    @Builder
    public PostsSaveRequestDto(String title, String content, String author) {
        this.title = title;
        this.content = content;
        this.author = author;
    }

    public Posts toEntity() {
        return Posts.builder()
                .title(title)
                .content(content)
                .author(author)
                .build();
    }
}
