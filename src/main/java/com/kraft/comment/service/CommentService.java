package com.kraft.comment.service;

import com.kraft.shared.exception.BusinessValidationException;
import com.kraft.comment.domain.Comment;
import com.kraft.comment.domain.CommentRepository;
import com.kraft.comment.dto.CommentDeleteResultDto;
import com.kraft.comment.dto.CommentPageDto;
import com.kraft.comment.dto.CommentSaveRequestDto;
import com.kraft.comment.dto.CommentUpdateRequestDto;
import com.kraft.comment.dto.CommentViewDto;
import com.kraft.post.domain.Post;
import com.kraft.post.domain.PostNotFoundException;
import com.kraft.post.domain.PostRepository;
import com.kraft.shared.domain.VersionCheck;
import com.kraft.shared.exception.NotFoundException;
import com.kraft.shared.security.CurrentUser;
import com.kraft.shared.security.OwnershipPolicy;
import com.kraft.shared.security.WriteAccessPolicy;
import com.kraft.user.domain.User;
import com.kraft.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@Service
@Transactional(readOnly = true)
public class CommentService {

    /** 커서 페이지 한 번에 내려주는 댓글 수(최초 렌더와 "더 보기" 공용). */
    private static final int PAGE_SIZE = 20;

    /** 최초 페이지에서 최상위 댓글 하나당 함께 내려주는 답글 수. 넘는 만큼은 {@link #findRepliesPage}가 잇는다. */
    private static final int INITIAL_REPLIES_PER_PARENT = 20;

    /** "답글 더 보기" 한 번에 내려주는 개수. */
    private static final int REPLIES_PAGE_SIZE = 50;

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;

    /** 저장하고 DB가 확정한 id·createdAt·version을 담아 돌려준다({@code saveAndFlush}). */
    @Transactional
    public CommentViewDto save(Long postId, Authentication authentication, CommentSaveRequestDto requestDto) {
        // 글 본문(TEXT)을 읽지 않고 존재만 확인해 참조를 쓴다.
        if (!postRepository.existsVisibleById(postId)) {
            throw new PostNotFoundException(postId);
        }
        Post post = postRepository.getReferenceById(postId);
        User user = findUser(authentication);
        WriteAccessPolicy.requireVerified(user);
        Comment parent = resolveParent(postId, requestDto.parentId());
        Comment saved = commentRepository.saveAndFlush(requestDto.toEntity(post, user, parent));
        return viewOf(saved, authentication);
    }

    /** 2단계까지만 허용한다. 다른 글의 댓글을 부모로 지정하면(id 조작) 거절한다. */
    private Comment resolveParent(Long postId, Long parentId) {
        if (parentId == null) {
            return null;
        }
        Comment parent = findComment(parentId);
        if (!parent.getPost().getId().equals(postId)) {
            throw new BusinessValidationException("다른 게시글의 댓글에는 답글을 달 수 없습니다.");
        }
        if (parent.getParent() != null) {
            throw new BusinessValidationException("답글에는 답글을 달 수 없습니다.");
        }
        return parent;
    }

    /**
     * 댓글을 수정하고 확정된 version을 돌려준다. 내용이 그대로면 UPDATE가 나가지 않아 버전이 안 오르므로,
     * flush 후 지금 DB의 version을 읽어 담는다.
     *
     * @param expectedVersion {@code If-Match}의 기준 버전. {@code null}이면 검사하지 않는다.
     */
    @Transactional
    public CommentViewDto update(Long id, CommentUpdateRequestDto requestDto, Long expectedVersion,
                                  Authentication authentication) {
        Comment comment = findComment(id);
        requirePostVisible(comment, authentication);
        WriteAccessPolicy.requireVerified(findUser(authentication));
        OwnershipPolicy.validateOwner(authentication, comment.getUser(), id);
        if (comment.isDeleted()) {
            throw new BusinessValidationException("삭제된 댓글은 수정할 수 없습니다.");
        }
        requireNotBlindedUnlessAdmin(comment, authentication);
        VersionCheck.require(Comment.class, comment.getId(), comment.getVersion(), expectedVersion);
        comment.update(requestDto.content());
        commentRepository.flush();
        return viewOf(comment, authentication);
    }

    /**
     * 답글이 있으면 행을 남기고 소프트 삭제(내용 비움)해 남의 답글이 함께 사라지지 않게 하고, 없으면
     * 행을 지운다(관리자 삭제도 같은 규칙).
     */
    @Transactional
    public CommentDeleteResultDto delete(Long id, Authentication authentication) {
        Comment comment = findComment(id);
        requirePostVisible(comment, authentication);
        OwnershipPolicy.validateOwner(authentication, comment.getUser(), id);
        requireNotBlindedUnlessAdmin(comment, authentication);
        if (comment.isDeleted()) {
            // 이미 삭제된 댓글의 재요청(다른 탭에서 먼저 지운 경우)은 조용히 넘어간다.
            return new CommentDeleteResultDto(id, true);
        }

        long replyCount = commentRepository.countRepliesByParentIdIn(List.of(id)).getOrDefault(id, 0L);
        if (replyCount > 0) {
            comment.softDelete();
            return new CommentDeleteResultDto(id, true);
        }

        // DB cascade가 없어 답글을 먼저 지운다(Comment.parent 참고). 위에서 이미 0개를 확인했지만
        // 그 사이 답글이 달리는 경쟁에 대한 방어다.
        commentRepository.deleteAllByParentId(id);
        commentRepository.delete(comment);
        return new CommentDeleteResultDto(id, false);
    }

    /** 상세 화면 최초 진입용 첫 페이지. 나머지는 {@link #findNextPageForView}가 잇는다. */
    public CommentPageDto findInitialPageForView(Long postId, Authentication authentication) {
        return pageForView(postId, null, authentication);
    }

    /** 커서({@code afterId}) 이후 다음 페이지. {@code afterId}는 마지막으로 받은 댓글의 id다. */
    public CommentPageDto findNextPageForView(Long postId, Long afterId, Authentication authentication) {
        // 삭제된 글의 댓글은 관리자 외에는 읽을 수 없다(최초 페이지는 글 상세가 이미 판정했다).
        if (!OwnershipPolicy.isAdmin(authentication) && !postRepository.existsVisibleById(postId)) {
            throw new PostNotFoundException(postId);
        }
        return pageForView(postId, afterId, authentication);
    }

    /**
     * {@code PAGE_SIZE + 1}개를 가져와 초과분으로 {@code hasMore}를 판정한다(COUNT 불필요). 답글은 부모마다
     * 최대 {@link #INITIAL_REPLIES_PER_PARENT}개를 한 번의 배치 조회로 함께 담는다.
     */
    private CommentPageDto pageForView(Long postId, Long afterId, Authentication authentication) {
        List<Comment> fetched = commentRepository.findPageByPostIdAsc(postId, afterId, PageRequest.of(0, PAGE_SIZE + 1));
        boolean hasMore = fetched.size() > PAGE_SIZE;
        List<Comment> page = hasMore ? fetched.subList(0, PAGE_SIZE) : fetched;

        List<Long> topLevelIds = page.stream().map(Comment::getId).toList();
        Map<Long, Long> replyCounts = commentRepository.countRepliesByParentIdIn(topLevelIds);
        Map<Long, List<Comment>> repliesByParent =
                commentRepository.findInitialRepliesGroupedByParentIdIn(topLevelIds, INITIAL_REPLIES_PER_PARENT);

        List<CommentViewDto> views = page.stream()
                .map(comment -> withInitialReplies(comment, authentication, replyCounts, repliesByParent))
                .toList();
        // 후속 페이지는 전체 개수를 다시 세지 않는다(화면이 로컬로 유지한다).
        Long totalCount = afterId == null ? commentRepository.countByPostId(postId) : null;
        return new CommentPageDto(views, totalCount, hasMore);
    }

    private CommentViewDto withInitialReplies(Comment comment, Authentication authentication,
                                               Map<Long, Long> replyCounts,
                                               Map<Long, List<Comment>> repliesByParent) {
        long replyCount = replyCounts.getOrDefault(comment.getId(), 0L);
        List<CommentViewDto> replies = repliesByParent.getOrDefault(comment.getId(), List.of()).stream()
                .map(reply -> viewOf(reply, authentication))
                .toList();
        boolean hasMoreReplies = replyCount > replies.size();
        return viewOf(comment, authentication).withReplies(replies, replyCount, hasMoreReplies);
    }

    /** "답글 더 보기": {@code afterId} 이후 답글을 최대 {@link #REPLIES_PAGE_SIZE}개. 없는 부모면 빈 페이지. */
    public CommentPageDto findRepliesPage(Long parentId, Long afterId, Authentication authentication) {
        commentRepository.findById(parentId).ifPresent(parent -> requirePostVisible(parent, authentication));
        List<Comment> fetched = commentRepository.findRepliesByParentIdAsc(
                parentId, afterId, PageRequest.of(0, REPLIES_PAGE_SIZE + 1));
        boolean hasMore = fetched.size() > REPLIES_PAGE_SIZE;
        List<Comment> page = hasMore ? fetched.subList(0, REPLIES_PAGE_SIZE) : fetched;

        List<CommentViewDto> views = page.stream()
                .map(reply -> viewOf(reply, authentication))
                .toList();
        // 항상 후속 페이지라 전체 개수는 담지 않는다.
        return new CommentPageDto(views, null, hasMore);
    }

    /** 삭제·숨김된 글 아래의 댓글은 관리자만 만질 수 있고, 일반 사용자에게는 글이 없는 것으로 답한다. */
    private void requirePostVisible(Comment comment, Authentication authentication) {
        Post post = comment.getPost();
        if (post != null && (post.isDeleted() || post.isBlinded()) && !OwnershipPolicy.isAdmin(authentication)) {
            throw new PostNotFoundException(post.getId());
        }
    }

    /** 관리자가 숨긴 댓글은 작성자도 고치거나 지울 수 없다(증거 인멸 방지). */
    private void requireNotBlindedUnlessAdmin(Comment comment, Authentication authentication) {
        if (comment.isBlinded() && !OwnershipPolicy.isAdmin(authentication)) {
            throw new AccessDeniedException("관리자가 숨긴 댓글은 수정·삭제할 수 없습니다. id=" + comment.getId());
        }
    }

    /** 보는 사람에 맞춘 화면용 DTO. 숨겨진 댓글은 관리자가 아니면 내용이 비워진다. */
    private CommentViewDto viewOf(Comment comment, Authentication authentication) {
        return new CommentViewDto(comment, OwnershipPolicy.canManage(authentication, comment.getUser()),
                OwnershipPolicy.isAdmin(authentication), List.of(), 0L, false);
    }

    /** 댓글을 숨긴다({@link #unblind}로 되돌림). 삭제됐거나 이미 숨겨졌으면 조용히 넘어간다. */
    @Transactional
    public void blind(Long id) {
        Comment comment = findComment(id);
        if (comment.isDeleted()) {
            return;
        }
        commentRepository.blind(id, LocalDateTime.now());
    }

    /** 숨김을 푼다. 숨겨진 댓글이 아니면 댓글이 없는 것으로 답한다. */
    @Transactional
    public void unblind(Long id) {
        if (commentRepository.unblind(id) == 0) {
            throw new NotFoundException("숨겨진 댓글이 아닙니다. id=" + id);
        }
    }

    private Comment findComment(Long id) {
        return commentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("해당 댓글이 없습니다. id=" + id));
    }

    private User findUser(Authentication authentication) {
        return CurrentUser.require(authentication, userRepository);
    }
}
