package com.kraft.post.dto;

import com.kraft.post.domain.Category;

import java.time.LocalDateTime;

/**
 * 목록 화면 한 줄에 필요한 값만 담는 조회 전용 projection.
 * <p>
 * {@link com.kraft.post.domain.PostRepository#search}와 {@code findTopByViewCountDesc}가
 * 이 타입으로 직접 SELECT해, 목록에 쓰지 않는 {@code content}(TEXT)와 작성자 이메일 등을 실어
 * 나르지 않는다.
 */
public record PostRowDto(
        Long id,
        String title,
        String author,
        LocalDateTime createdAt,
        LocalDateTime modifiedDate,
        Category category,
        long viewCount
) {

    /**
     * 목록에는 작성일을 보여주고, 수정된 글만 구분한다(A-BE-11) — 기본 정렬이 등록순(id)인데
     * 날짜 열이 수정 시각이면, 오래된 글을 고쳤을 때 "최신 등록순" 중간에 오늘 날짜가 찍혀
     * 순서가 뒤섞여 보인다. 생성 시점에는 {@code @CreatedDate}·{@code @LastModifiedDate}가
     * 같은 시각으로 함께 찍히므로(BaseEntity), 둘이 다르면 그 뒤에 실제로 수정된 것이다.
     */
    public boolean isModified() {
        return modifiedDate != null && !modifiedDate.equals(createdAt);
    }
}
