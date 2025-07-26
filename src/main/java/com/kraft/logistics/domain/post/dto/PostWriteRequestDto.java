package com.kraft.logistics.domain.post.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PostWriteRequestDto {
    private String title;
    private String content;
    private Long userId;
}
