package com.kraft.post.dto;

import com.kraft.post.domain.Category;
import java.time.LocalDateTime;

public record PostsListResponseDto(
        Long id,
        String title,
        String author,
        LocalDateTime createdAt,
        // 하위 호환을 위해 필드 자체는 남기지만(A-BE-11), 화면은 이제 createdAt + modified를
        // 쓴다. modifiedDate를 지우면 이 응답을 이미 캐시했거나 직접 보는 외부 소비자가
        // 있을 때 조용히 깨진다 — 필드를 더하는 쪽이 안전하다.
        LocalDateTime modifiedDate,
        // 파생값이지만 record 컴포넌트로 명시한다 — Jackson이 JSON 응답(load-more.js가
        // 읽는 API)에 확실히 실어야 하는데, 추가 isXxx() 메서드로만 두면 record의 표준
        // 컴포넌트 직렬화 경로를 타지 않아 값이 빠질 위험이 있다.
        boolean modified,
        Category category,
        long viewCount,
        long commentCount
) {

    public PostsListResponseDto(PostRowDto row, long commentCount) {
        this(row.id(), row.title(), row.author(), row.createdAt(), row.modifiedDate(), row.isModified(),
                row.category(), row.viewCount(), commentCount);
    }
}
