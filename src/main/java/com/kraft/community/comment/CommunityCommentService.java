package com.kraft.community.comment;

import com.kraft.common.error.ApiException;
import com.kraft.community.post.CommunityPost;
import com.kraft.community.post.CommunityPostMetricsRepository;
import com.kraft.community.post.CommunityPostRepository;
import com.kraft.community.post.PostStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommunityCommentService {

    private static final int MAX_PAGE_SIZE = 100;
    // 댓글 목록 컨트롤러의 기본 페이지 크기와 targetPage 계산이 항상 같은 값을 쓰도록
    // 단일 소스로 공유한다(컨트롤러의 @RequestParam 기본값도 이 상수를 참조).
    public static final int DEFAULT_PAGE_SIZE = 50;

    private final CommunityCommentRepository communityCommentRepository;
    private final CommunityPostRepository communityPostRepository;
    private final CommunityPostMetricsRepository communityPostMetricsRepository;
    private final Clock clock;
    private final Counter tombstoneCounter;
    private final Counter createdCounter;

    public CommunityCommentService(CommunityCommentRepository communityCommentRepository,
                                    CommunityPostRepository communityPostRepository,
                                    CommunityPostMetricsRepository communityPostMetricsRepository,
                                    Clock clock,
                                    MeterRegistry meterRegistry) {
        this.communityCommentRepository = communityCommentRepository;
        this.communityPostRepository = communityPostRepository;
        this.communityPostMetricsRepository = communityPostMetricsRepository;
        this.clock = clock;
        this.tombstoneCounter = Counter.builder("kraft_community_comment_tombstoned_total")
                .description("tombstone 처리(삭제)된 댓글 누적 수")
                .register(meterRegistry);
        this.createdCounter = Counter.builder("kraft_community_comment_created_total")
                .description("생성된 댓글 수")
                .register(meterRegistry);
    }

    /**
     * B-P0-7: 게시글이 존재하고 공개(PUBLISHED) 상태인지 먼저 확인한다 — 이전에는 postId만으로
     * 곧바로 댓글을 조회해 숨김·삭제된 글의 댓글이 permitAll 엔드포인트로 계속 읽혔다.
     */
    @Transactional(readOnly = true)
    public CommunityCommentListResult list(Long postId, int page, int size) {
        CommunityPost post = communityPostRepository.findById(postId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "COMMUNITY_POST_NOT_FOUND",
                        "게시글을 찾을 수 없습니다."));
        if (post.getStatus() != PostStatus.PUBLISHED) {
            throw new ApiException(HttpStatus.NOT_FOUND, "COMMUNITY_POST_NOT_FOUND", "게시글을 찾을 수 없습니다.");
        }

        int clampedPage = Math.max(0, page);
        int clampedSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        Page<CommunityComment> topLevel = communityCommentRepository.findByPostIdAndParentIdIsNull(postId,
                PageRequest.of(clampedPage, clampedSize, Sort.by(Sort.Direction.ASC, "createdAt", "id")));
        List<Long> parentIds = topLevel.getContent().stream().map(CommunityComment::getId).toList();
        List<CommunityComment> replies = parentIds.isEmpty()
                ? List.of()
                : communityCommentRepository.findByPostIdAndParentIdIn(postId, parentIds);
        Map<Long, List<CommunityComment>> repliesByParentId = replies.stream()
                .collect(Collectors.groupingBy(CommunityComment::getParentId));
        return new CommunityCommentListResult(topLevel, repliesByParentId);
    }

    /**
     * KB-01: 게시글이 존재하고 공개(PUBLISHED) 상태인지 먼저 확인한다 — list()(B-P0-7)·
     * CommunityReactionService.requireVisiblePost()와 동일한 404 계약. 이전에는 존재만
     * 확인해 숨김·삭제된 글의 postId로도 댓글을 남기고 commentCount를 증가시킬 수 있었다.
     */
    @Transactional
    public CommunityCommentCreationResult create(Long ownerId, String authorNickname, Long postId,
                                                   CreateCommentRequest request) {
        CommunityPost post = communityPostRepository.findById(postId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "COMMUNITY_POST_NOT_FOUND",
                        "게시글을 찾을 수 없습니다."));
        if (post.getStatus() != PostStatus.PUBLISHED) {
            throw new ApiException(HttpStatus.NOT_FOUND, "COMMUNITY_POST_NOT_FOUND", "게시글을 찾을 수 없습니다.");
        }

        CommunityComment parent = null;
        if (request.parentId() != null) {
            parent = communityCommentRepository.findByIdForUpdate(request.parentId())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "COMMUNITY_COMMENT_PARENT_NOT_FOUND",
                            "답글을 달 댓글을 찾을 수 없습니다."));
            if (!parent.getPostId().equals(post.getId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "COMMUNITY_COMMENT_PARENT_MISMATCH",
                        "다른 게시글의 댓글에는 답글을 달 수 없습니다.");
            }
            // 1단계 대댓글만 허용 — 답글의 답글은 거부한다.
            if (parent.getParentId() != null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "COMMUNITY_COMMENT_REPLY_DEPTH_EXCEEDED",
                        "답글에는 답글을 달 수 없습니다.");
            }
            if (parent.isDeleted()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "COMMUNITY_COMMENT_PARENT_DELETED",
                        "삭제된 댓글에는 답글을 달 수 없습니다.");
            }
        }

        CommunityComment saved;
        try {
            saved = communityCommentRepository.save(new CommunityComment(
                    postId, request.parentId(), ownerId, authorNickname, request.content(),
                    OffsetDateTime.now(clock)));
        } catch (DataIntegrityViolationException concurrentlyDeletedPost) {
            // 저장 직전 게시글/부모가 동시 삭제된 경합(FK race) → 사용자에게는 404로 보인다.
            throw new ApiException(HttpStatus.NOT_FOUND, "COMMUNITY_POST_NOT_FOUND",
                    "게시글을 찾을 수 없습니다.", concurrentlyDeletedPost);
        }
        // 댓글·좋아요 집계는 원자적으로 증감하며 읽고 고쳐 쓰지 않는다.
        communityPostMetricsRepository.incrementCommentCount(postId);

        // targetPage: 상위 댓글이면 자기 자신, 답글이면 부모(항상 상위 댓글)의 목록 내 위치를
        // 기준으로 계산한다(mutation 후 target page 계산).
        Long anchorId = parent != null ? parent.getId() : saved.getId();
        long countUpToAnchor = communityCommentRepository.countTopLevelUpToId(postId, anchorId);
        int targetPage = (int) ((Math.max(1, countUpToAnchor) - 1) / DEFAULT_PAGE_SIZE);

        createdCounter.increment();
        return new CommunityCommentCreationResult(saved, targetPage);
    }

    @Transactional
    public void delete(Long ownerId, Long commentId) {
        CommunityComment comment = communityCommentRepository.findByIdForUpdate(commentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "COMMUNITY_COMMENT_NOT_FOUND",
                        "댓글을 찾을 수 없습니다."));
        if (!comment.getOwnerId().equals(ownerId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "COMMUNITY_COMMENT_NOT_OWNER", "본인 댓글만 삭제할 수 있습니다.");
        }
        // B-P0-6: 이미 tombstone된 댓글을 재삭제 요청하면 멱등하게 아무 것도 하지 않는다 —
        // 가드가 없으면 재삭제할 때마다 decrementCommentCount가 다시 불려 카운터가 실제
        // 좋아요·댓글 수보다 더 많이 깎이고 tombstoneCounter도 중복 집계된다.
        if (comment.isDeleted()) {
            return;
        }
        comment.markDeleted();
        // saveAndFlush로 즉시 반영해야 한다 — 아래 decrementCommentCount는 벌크 UPDATE라
        // clearAutomatically가 영속성 컨텍스트 전체를 비운다. 이 tombstone 변경이 먼저
        // flush되지 않은 채로 컨텍스트가 비워지면 markDeleted() 변경 자체가 유실된다.
        communityCommentRepository.saveAndFlush(comment);
        communityPostMetricsRepository.decrementCommentCount(comment.getPostId());
        tombstoneCounter.increment();
    }
}
