package com.kraft.post.service;

import com.kraft.post.domain.PostNotFoundException;
import com.kraft.post.domain.PostRepository;
import com.kraft.shared.exception.BusinessValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 관리자만 하는 게시글 상태 변경(복구·숨김·숨김 해제·고정). 경로가 {@code /api/v1/admin/**}라 권한은 {@code SecurityConfig}가
 * 판정한다. 클래스 기본값이 읽기 전용이 아니라 쓰기 메서드마다 {@code @Transactional}을 명시한다.
 */
@RequiredArgsConstructor
@Service
public class PostModerationService {

    /** 고정 기한의 상한. 이보다 먼 기한은 사실상 영구 고정이라 입력 실수일 가능성이 크다. */
    static final int MAX_PIN_DAYS = 365;

    private final PostRepository postRepository;

    /** 소프트 삭제된 글을 되돌린다. 삭제된 글이 아니면(없거나 이미 복구됨) 글이 없는 것으로 답한다. */
    @Caching(evict = {
            @CacheEvict(value = "pinnedPosts", allEntries = true),
            @CacheEvict(value = "popularPosts", allEntries = true)
    })
    @Transactional
    public void restore(Long id) {
        if (postRepository.restore(id) == 0) {
            throw new PostNotFoundException(id);
        }
    }

    /** 글을 숨긴다(내용·댓글은 남고 {@link #unblindPost}로 되돌린다). 이미 숨겨졌으면 조용히 넘어가고, 없거나 삭제된 글이면 글이 없는 것으로 답한다. */
    @Caching(evict = {
            @CacheEvict(value = "pinnedPosts", allEntries = true),
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
            @CacheEvict(value = "pinnedPosts", allEntries = true),
            @CacheEvict(value = "popularPosts", allEntries = true)
    })
    @Transactional
    public void unblindPost(Long id) {
        if (postRepository.unblind(id) == 0) {
            throw new PostNotFoundException(id);
        }
    }

    /**
     * 글을 {@code until}까지 목록 상단에 고정한다(이미 고정된 글은 기한만 바뀐다). 상단에는
     * {@link PostQueryService#PINNED_LIMIT}개까지만 보이므로 그보다 많이 고정하지 못하게 하고, 기한은 지금부터
     * {@link #MAX_PIN_DAYS}일 이내여야 한다. 삭제·숨겨진 글은 고정할 수 없다.
     */
    @Caching(evict = {
            @CacheEvict(value = "pinnedPosts", allEntries = true),
            @CacheEvict(value = "popularPosts", allEntries = true)
    })
    @Transactional
    public void pin(Long id, LocalDateTime until) {
        LocalDateTime now = LocalDateTime.now();
        if (until.isAfter(now.plusDays(MAX_PIN_DAYS))) {
            throw new BusinessValidationException("고정 기한은 지금부터 %d일 이내여야 합니다.".formatted(MAX_PIN_DAYS));
        }
        if (postRepository.countPinnedExcluding(id, now) >= PostQueryService.PINNED_LIMIT) {
            throw new BusinessValidationException(
                    "고정은 최대 %d개까지입니다. 다른 글의 고정을 먼저 해제하세요.".formatted(PostQueryService.PINNED_LIMIT));
        }
        if (postRepository.pin(id, until) == 0) {
            throw new PostNotFoundException(id);
        }
    }

    /** 고정을 푼다. 고정된 글이 아니면(없거나 이미 풀림) 글이 없는 것으로 답한다. */
    @Caching(evict = {
            @CacheEvict(value = "pinnedPosts", allEntries = true),
            @CacheEvict(value = "popularPosts", allEntries = true)
    })
    @Transactional
    public void unpin(Long id) {
        if (postRepository.unpin(id) == 0) {
            throw new PostNotFoundException(id);
        }
    }
}
