package com.kraft.post.service;

import com.kraft.post.domain.PostNotFoundException;
import com.kraft.post.domain.PostPinLock;
import com.kraft.post.domain.PostPinLockRepository;
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
    private final PostPinLockRepository pinLockRepository;

    /**
     * 고정 상한 계산·반영을 DB 전체에서 한 번에 하나씩만 하도록 잠금 행을 잡는다(트랜잭션 안에서만 의미가 있다). 고정 수를 바꿀 수 있는
     * 모든 경로(고정·복구·숨김 해제)가 같은 행을 같은 순서로 먼저 잡아, 개수 확인과 반영 사이에 다른 요청이 끼어들지 못하게 한다.
     */
    private void lockPinSlots() {
        if (pinLockRepository.lockForUpdate().isEmpty()) {
            // 마이그레이션이 만든 행이 없는 환경(JPA가 스키마를 만드는 테스트 DB). 만든 뒤 다시 잠근다.
            pinLockRepository.saveAndFlush(PostPinLock.singleton());
            pinLockRepository.lockForUpdate();
        }
    }

    /**
     * 복구·숨김 해제로 기한이 남은 고정이 다시 살아났는데 활성 고정이 상한을 넘으면, 방금 되살아난 글의 고정만 푼다. 복구·숨김 해제 자체는
     * 그대로 성공시킨다 — 상한 때문에 글을 되살리지 못하게 하면 관리자가 먼저 다른 고정을 정리해야 하는 순서 의존이 생긴다.
     */
    private void enforcePinLimitAfterReactivation(Long id) {
        LocalDateTime now = LocalDateTime.now();
        long others = postRepository.countPinnedExcluding(id, now);
        boolean reactivatedIsPinned = postRepository.countPinned(now) > others;
        if (reactivatedIsPinned && others >= PostQueryService.PINNED_LIMIT) {
            postRepository.unpin(id);
        }
    }

    /** 소프트 삭제된 글을 되돌린다. 삭제된 글이 아니면(없거나 이미 복구됨) 글이 없는 것으로 답한다. */
    @Caching(evict = {
            @CacheEvict(value = "pinnedPosts", allEntries = true),
            @CacheEvict(value = "popularPosts", allEntries = true)
    })
    @Transactional
    public void restore(Long id) {
        lockPinSlots();
        if (postRepository.restore(id) == 0) {
            throw new PostNotFoundException(id);
        }
        enforcePinLimitAfterReactivation(id);
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
        lockPinSlots();
        if (postRepository.unblind(id) == 0) {
            throw new PostNotFoundException(id);
        }
        enforcePinLimitAfterReactivation(id);
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
        lockPinSlots();
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
