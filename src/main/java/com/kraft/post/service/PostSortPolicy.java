package com.kraft.post.service;

import com.kraft.shared.exception.BusinessValidationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

/**
 * 게시글 목록 {@code sort} 파라미터의 허용 목록. {@code PostRepository.search}는 정렬을 {@link Pageable}의 {@link Sort}에
 * 전적으로 맡기므로({@link #effectiveSort}), 검증 없이 받으면 {@code ?sort=content,desc}처럼 인덱스 없는 TEXT 컬럼 정렬을
 * 강제할 수 있다. 정렬이 안전한 컬럼만 둔다.
 */
public final class PostSortPolicy {

    private static final Set<String> ALLOWED_PROPERTIES = Set.of("id", "viewCount", "updatedAt");

    private PostSortPolicy() {
    }

    /** 허용되지 않는 정렬 속성이 있으면 예외를 던진다. API 응답은 이를 400으로 변환한다. */
    public static void validate(Sort sort) {
        if (!isAllowed(sort)) {
            throw new BusinessValidationException("허용되지 않는 정렬 기준입니다.");
        }
    }

    /** 허용되지 않는 정렬이면 정렬 없는 page/size만 남긴다(화면 요청은 URL 조작도 오류 없이 무시한다). {@link #effectiveSort}가 이어서 id 내림차순으로 채운다. */
    public static Pageable sanitize(Pageable pageable) {
        if (isAllowed(pageable.getSort())) {
            return pageable;
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
    }

    private static boolean isAllowed(Sort sort) {
        return sort.stream().map(Sort.Order::getProperty).allMatch(ALLOWED_PROPERTIES::contains);
    }

    /**
     * 실제 정렬에 쓸 {@link Sort}. 정렬이 없으면 id 내림차순, id가 아닌 정렬이면 id 내림차순을 tie-breaker로 덧붙이고,
     * 이미 id를 포함한 요청은 그대로 둔다. 리포지토리 JPQL에 고정 ORDER BY를 두지 않는 이유는 Spring Data가 Sort를 그 뒤에
     * 덧붙여, id가 고유하니 요청한 정렬이 반환 순서에 반영되지 않기 때문이다.
     */
    public static Sort effectiveSort(Sort requested) {
        if (requested.isUnsorted()) {
            return Sort.by(Sort.Direction.DESC, "id");
        }
        if (requested.stream().anyMatch(order -> order.getProperty().equals("id"))) {
            return requested;
        }
        return requested.and(Sort.by(Sort.Direction.DESC, "id"));
    }
}
