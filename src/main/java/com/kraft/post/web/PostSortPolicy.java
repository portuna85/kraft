package com.kraft.post.web;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

/**
 * 게시글 목록의 {@code sort} 파라미터 허용 목록.
 * <p>
 * {@code PostRepository.search}는 명시적 {@code ORDER BY p.id DESC} 뒤에 Spring Data가
 * {@link Pageable}의 {@link Sort}를 그대로 덧붙인다. 검증 없이 받으면 {@code ?sort=content,desc}
 * 처럼 인덱스 없는 TEXT 컬럼 정렬을 클라이언트가 강제할 수 있다(개선 보고서 "검색과 깊은
 * 페이지의 비용"). 허용 목록은 {@code Post} 엔티티에서 정렬이 안전한 컬럼만 둔다.
 */
public final class PostSortPolicy {

    private static final Set<String> ALLOWED_PROPERTIES = Set.of("id", "viewCount", "updatedAt");

    private PostSortPolicy() {
    }

    /** 허용되지 않는 정렬 속성이 있으면 예외를 던진다. API 응답은 이를 400으로 변환한다. */
    public static void validate(Sort sort) {
        if (!isAllowed(sort)) {
            throw new IllegalArgumentException("허용되지 않는 정렬 기준입니다.");
        }
    }

    /**
     * 허용되지 않는 정렬이면 정렬 없이(= 리포지토리의 기본 ORDER BY id DESC를 그대로 따름)
     * page/size만 유지한 {@link Pageable}을 돌려준다. 화면 요청은 URL을 직접 조작한 경우까지
     * 오류 화면으로 보낼 필요가 없어 거부 대신 무시한다.
     */
    public static Pageable sanitize(Pageable pageable) {
        if (isAllowed(pageable.getSort())) {
            return pageable;
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
    }

    private static boolean isAllowed(Sort sort) {
        return sort.stream().map(Sort.Order::getProperty).allMatch(ALLOWED_PROPERTIES::contains);
    }
}
