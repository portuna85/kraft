package com.kraft.post.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** {@code Post}를 읽는 JPQL이 소프트 삭제된 행을 거르는지 지킨다. {@code @SQLRestriction}을 쓰지 않고 쿼리마다 {@code PostRepository.VISIBLE}을 붙이는 방식이라, 새 쿼리가 그 조건을 빠뜨리면 지운 글이 목록·검색·sitemap에 다시 나타난다 — 컴파일이나 평소 테스트로는 잡기 어려운 실수다. */
class PostRepositoryVisibilityGuardTest {

    /** 숨겨진 행을 일부러 읽거나 바꾸는 쿼리. 관리자 상세·영구 삭제가 읽고, {@code unpin}은 삭제·숨김 상태와 무관하게 고정을 풀어야 해서 {@code deletedAt} 조건을 두지 않는다. */
    private static final Set<String> READS_HIDDEN_ROWS = Set.of(
            "findByIdWithUser", "findByIdForPurge", "findIdsDeletedBefore", "unpin");

    @Test
    @DisplayName("Post를 읽거나 바꾸는 @Query는 deletedAt 조건을 갖거나 숨겨진 행을 읽는 쿼리로 허용돼 있어야 한다")
    void everyPostQueryFiltersDeletedRows() {
        List<String> unguarded = new ArrayList<>();
        for (Method method : PostRepository.class.getDeclaredMethods()) {
            Query query = method.getAnnotation(Query.class);
            if (query == null || READS_HIDDEN_ROWS.contains(method.getName())) {
                continue;
            }
            for (String jpql : new String[]{query.value(), query.countQuery()}) {
                if ((jpql.contains("FROM Post p") || jpql.contains("UPDATE Post p")) && !jpql.contains("deletedAt")) {
                    unguarded.add(method.getName());
                }
            }
        }

        assertThat(unguarded)
                .as("deletedAt 조건이 없는 Post 쿼리 — PostRepository.VISIBLE을 붙이거나, 숨겨진 행을 일부러 읽는 것이면 이 테스트의 허용 목록에 올린다")
                .isEmpty();
    }
}
