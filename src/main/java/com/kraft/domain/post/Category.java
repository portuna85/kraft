package com.kraft.domain.post;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Category {

    FREE("자유"),
    QNA("질문"),
    NOTICE("공지");

    private final String title;
}
