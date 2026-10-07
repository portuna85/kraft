package com.kraft.post.service;

import com.kraft.post.domain.PostNotFoundException;
import com.kraft.post.domain.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 관리자만 하는 게시글 상태 변경(복구·숨김·숨김 해제). 호출 경로가 {@code /api/v1/admin/**}라 권한은
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

    /**
     * 글을 숨긴다(신고 처리가 부른다). 삭제와 달리 내용과 댓글이 그대로 남고 {@link #unblindPost}로 되돌릴
     * 수 있다. 이미 숨겨진 글은 조용히 넘어간다 — 같은 대상에 신고가 여럿이거나 다른 관리자가 먼저
     * 처리했을 수 있다. 없거나 삭제된 글이면 글이 없는 것으로 답한다.
     */
    @Caching(evict = {
            @CacheEvict(value = "pinnedNotices", allEntries = true),
            @CacheEvict(value = "popularPosts", allEntries = true)
    })
    @Transactional
    public void blindPost(Long id) {
        if (postRepository.blind(id, LocalDateTime.now()) == 0
                && postRepository.findById(id).filter(post -> !post.isDeleted()).isEmpty()) {
            throw new PostNotFoundException(id);
        }
    }

    /** 숨김을 푼다. 숨겨진 글이 아니면(없거나 삭제됐거나 이미 풀림) 글이 없는 것으로 답한다. */
    @Caching(evict = {
            @CacheEvict(value = "pinnedNotices", allEntries = true),
            @CacheEvict(value = "popularPosts", allEntries = true)
    })
    @Transactional
    public void unblindPost(Long id) {
        if (postRepository.unblind(id) == 0) {
            throw new PostNotFoundException(id);
        }
    }
}
