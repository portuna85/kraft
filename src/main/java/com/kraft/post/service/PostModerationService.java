package com.kraft.post.service;

import com.kraft.post.domain.PostNotFoundException;
import com.kraft.post.domain.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자만 하는 게시글 상태 변경(복구 등). 호출 경로가 {@code /api/v1/admin/**}라 권한은
 * {@code SecurityConfig}가 판정한다 — 이 서비스는 권한을 다시 묻지 않는다({@code ReportService}와 같은 관례).
 * <p>
 * 클래스 기본값이 읽기 전용이 아니라, 쓰기 메서드마다 {@code @Transactional}을 명시한다.
 */
@RequiredArgsConstructor
@Service
public class PostModerationService {

    private final PostRepository postRepository;

    /**
     * 소프트 삭제된 글을 되돌린다. 삭제 때 닫힌 신고는 다시 열지 않는다 — 처리 기록이므로 그대로 둔다.
     * 삭제된 글이 아니면(없거나 이미 복구됨) 글이 없는 것으로 답한다.
     */
    @Caching(evict = {
            @CacheEvict(value = "pinnedNotices", allEntries = true),
            @CacheEvict(value = "popularPosts", allEntries = true)
    })
    @Transactional
    public void restore(Long id) {
        if (postRepository.restore(id) == 0) {
            throw new PostNotFoundException(id);
        }
    }
}
