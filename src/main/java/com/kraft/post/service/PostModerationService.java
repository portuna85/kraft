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
 * 관리자만 하는 게시글 상태 변경(복구·숨김·숨김 해제·고정). 호출 경로가 {@code /api/v1/admin/**}라 권한은
 * {@code SecurityConfig}가 판정한다 — 이 서비스는 권한을 다시 묻지 않는다.
 * <p>
 * 클래스 기본값이 읽기 전용이 아니라, 쓰기 메서드마다 {@code @Transactional}을 명시한다.
 */
@RequiredArgsConstructor
@Service
public class PostModerationService {

    /** 고정 기한의 상한. 이보다 먼 기한은 사실상 영구 고정이라 입력 실수일 가능성이 크다. */
    static final int MAX_PIN_DAYS = 365;

    private final PostRepository postRepository;

    /**
     * 소프트 삭제된 글을 되돌린다. 삭제 때 닫힌 신고는 다시 열지 않는다 — 처리 기록이므로 그대로 둔다.
     * 삭제된 글이 아니면(없거나 이미 복구됨) 글이 없는 것으로 답한다.
     */
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

    /**
     * 글을 숨긴다. 삭제와 달리 내용과 댓글이 그대로 남고 {@link #unblindPost}로 되돌릴 수 있다. 이미 숨겨진
     * 글은 조용히 넘어간다(두 관리자가 겹쳐 눌러도 같은 결과다). 없거나 삭제된 글이면 글이 없는 것으로 답한다.
     */
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
     * 글을 {@code until}까지 목록 상단에 고정한다. 이미 고정된 글이면 기한만 바뀐다.
     * <p>
     * 목록 상단에는 {@link PostQueryService#PINNED_LIMIT}개까지만 보이므로 그보다 많이 고정하지 못하게 한다 —
     * 넘치는 글은 고정했는데도 보이지 않아 관리자를 헷갈리게 한다. 기한은 지금부터 {@link #MAX_PIN_DAYS}일
     * 이내여야 한다. 삭제되거나 숨겨진 글은 고정할 수 없다(글이 없는 것으로 답한다).
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
