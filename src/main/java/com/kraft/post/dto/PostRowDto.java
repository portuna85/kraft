package com.kraft.post.dto;

import com.kraft.post.domain.Category;

import java.time.LocalDateTime;

/**
 * 목록 화면 한 줄에 필요한 값만 담는 조회 전용 projection.
 * <p>
 * {@link com.kraft.post.domain.PostRepository#search}와 {@code findTopByViewCountDesc}가
 * 이 타입으로 직접 SELECT해, 목록에 쓰지 않는 {@code content}(TEXT)와 작성자 이메일 등을 실어
 * 나르지 않는다(개선 보고서 "게시판 목록의 불필요한 열과 집계").
 */
public record PostRowDto(
        Long id,
        String title,
        String author,
        LocalDateTime modifiedDate,
        Category category,
        long viewCount
) {
}
