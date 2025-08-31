package com.kraft.book.web.dto;

import com.kraft.book.domain.posts.Posts;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class PostsListResponseDto {

    private Long id;
    private String title;
    private String author;
    private final LocalDateTime createdDate;   // ✅ 템플릿에서 쓰는 이름
    private final LocalDateTime modifiedDate;  // ✅ 템플릿에서 쓰는 이름

    @Builder
    public PostsListResponseDto(Long id, String title, String author,
                                LocalDateTime createdDate, LocalDateTime modifiedDate) {
        this.id = id;
        this.title = title;
        this.author = author;
        this.createdDate = createdDate;
        this.modifiedDate = modifiedDate;
    }

    public static PostsListResponseDto from(Posts e) {
        return PostsListResponseDto.builder()
                .id(e.getId())
                .title(e.getTitle())
                .author(e.getAuthor())
                .createdDate(e.getCreatedDate())     // ✅ BaseTimeEntity의 필드명과 맞추기
                .modifiedDate(e.getModifiedDate())
                .build();
    }
}