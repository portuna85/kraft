package com.kraft.comment.service;

import com.kraft.comment.domain.Comment;
import com.kraft.comment.domain.CommentRepository;
import com.kraft.comment.dto.CommentPageDto;
import com.kraft.comment.dto.CommentSaveRequestDto;
import com.kraft.comment.dto.CommentUpdateRequestDto;
import com.kraft.comment.dto.CommentViewDto;
import com.kraft.post.domain.Post;
import com.kraft.post.domain.PostNotFoundException;
import com.kraft.post.domain.PostRepository;
import com.kraft.shared.exception.NotFoundException;
import com.kraft.shared.security.CurrentUser;
import com.kraft.shared.security.OwnershipPolicy;
import com.kraft.shared.security.WriteAccessPolicy;
import com.kraft.user.domain.User;
import com.kraft.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@Service
@Transactional(readOnly = true)
public class CommentService {

    /**
     * 커서 페이지 한 번에 내려주는 댓글 수. 상세 화면 최초 렌더와 "더 보기"가 함께 쓴다
     * (개선 보고서 "댓글 전체 로딩").
     */
    private static final int PAGE_SIZE = 20;

    /**
     * 최초 페이지에서 최상위 댓글 하나당 함께 내려주는 답글 수(개선 보고서 COR-05). 예전에는
     * 페이지 전체(여러 부모 합산)에서 가져오는 답글 총량에만 상한(500)을 뒀다 — 한 부모에
     * 답글이 그 상한을 넘거나, 다른 부모가 그 상한을 먼저 다 쓰면 남은 답글에 새로고침으로도
     * 영원히 도달할 수 없었다. 부모별로 이 개수만큼만 먼저 보여주고, 넘는 만큼은
     * {@code hasMoreReplies}로 표시해 "답글 더 보기"({@link #findRepliesPage})가 이어받는다.
     */
    private static final int INITIAL_REPLIES_PER_PARENT = 20;

    /** "답글 더 보기" 한 번에 내려주는 개수. */
    private static final int REPLIES_PAGE_SIZE = 50;

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;

    /**
     * 응답에 확정된 id·createdAt·version을 모두 실어 돌려준다(개선 보고서 COR-05·COR-08).
     * 예전에는 id만 돌려줘서, 화면이 새 댓글을 {@code new Date().toISOString()}(UTC)과
     * version 없이 직접 만들어 반영했다 — 그 버전 없는 댓글은 새로고침 전까지 낡은 화면 검사를
     * 건너뛰었고, 시각도 서버가 나중에 돌려주는 값과 새로고침 전후로 다르게 보였다.
     * {@code saveAndFlush}로 DB가 실제로 확정한 값(감사 필드의 createdAt 포함)을 그 자리에서
     * 곧바로 읽는다.
     */
    @Transactional
    public CommentViewDto save(Long postId, Authentication authentication, CommentSaveRequestDto requestDto) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException(postId));
        User user = findUser(authentication);
        WriteAccessPolicy.requireVerified(user);
        Comment parent = resolveParent(postId, requestDto.parentId());
        Comment saved = commentRepository.saveAndFlush(requestDto.toEntity(post, user, parent));
        return new CommentViewDto(saved, OwnershipPolicy.canManage(authentication, saved.getUser()));
    }

    /**
     * 2단계까지만 허용한다 — 답글 자신에게는 답글을 달 수 없다. parentId가 가리키는 댓글이
     * 다른 게시글 소속이면(URL을 조작해 다른 글의 댓글 id를 보낸 경우) 함께 거절한다.
     */
    private Comment resolveParent(Long postId, Long parentId) {
        if (parentId == null) {
            return null;
        }
        Comment parent = findComment(parentId);
        if (!parent.getPost().getId().equals(postId)) {
            throw new IllegalArgumentException("다른 게시글의 댓글에는 답글을 달 수 없습니다.");
        }
        if (parent.getParent() != null) {
            throw new IllegalArgumentException("답글에는 답글을 달 수 없습니다.");
        }
        return parent;
    }

    /**
     * 정지된 계정도 이 경로로 자신의 기존 댓글을 계속 바꿀 수 있었다 — 작성만 작성 정책을
     * 검사하고 수정은 소유권만 봤기 때문이다. 삭제는 의도적으로 그대로 둔다 — 정지된
     * 사용자도 자신의 댓글을 지우는 것까지 막지는 않는다.
     */
    /**
     * 확정된 version을 응답에 실어 돌려준다(개선 보고서 COR-05) — 예전에는 id만 돌려줘서,
     * 화면이 "받았던 version + 1"로 다음 버전을 추측했다. 내용이 실제로 바뀌지 않으면
     * Hibernate가 UPDATE 자체를 내지 않아 버전이 그대로인데, 그 추측은 +1로 어긋나 바로 다음
     * 정상 수정이 가짜 409를 받았다. flush로 실제 반영 여부와 무관하게 지금 DB가 들고 있는
     * version을 그 자리에서 읽는다.
     */
    @Transactional
    public CommentViewDto update(Long id, CommentUpdateRequestDto requestDto, Authentication authentication) {
        Comment comment = findComment(id);
        WriteAccessPolicy.requireVerified(findUser(authentication));
        OwnershipPolicy.validateOwner(authentication, comment.getUser(), id);
        validateVersion(comment, requestDto.version());
        comment.update(requestDto.content());
        commentRepository.flush();
        return new CommentViewDto(comment, OwnershipPolicy.canManage(authentication, comment.getUser()));
    }

    /**
     * 화면이 받아간 버전과 지금 DB의 버전이 다르면, 그 사이 다른 곳에서 저장이 일어난 것이다
     * (B12). {@code PostService.validateVersion}과 같은 계약 — 버전을 보내지 않는 요청은
     * 기존처럼 그대로 저장한다. {@link ObjectOptimisticLockingFailureException}은
     * {@code ApiExceptionHandler}가 이미 409로 변환한다(Post 편집 충돌과 같은 경로).
     */
    private void validateVersion(Comment comment, Long expectedVersion) {
        if (expectedVersion != null && !expectedVersion.equals(comment.getVersion())) {
            throw new ObjectOptimisticLockingFailureException(Comment.class, comment.getId());
        }
    }

    @Transactional
    public void delete(Long id, Authentication authentication) {
        Comment comment = findComment(id);
        OwnershipPolicy.validateOwner(authentication, comment.getUser(), id);
        // 최상위 댓글이면 그 답글을 먼저 지운다 — DB에 cascade를 걸지 않았으므로(Comment.parent
        // 주석 참고) 그대로 두면 FK 위반이 난다. 답글 자신을 지울 때는 이 호출이 0건을 지우고
        // 끝난다(3단계 금지라 답글에는 답글이 없다).
        commentRepository.deleteAllByParentId(id);
        commentRepository.delete(comment);
    }

    /**
     * 상세 화면 최초 진입 시 첫 페이지(최대 {@link #PAGE_SIZE}개)만 내려준다. 나머지는
     * {@link #findNextPageForView}로 "더 보기"가 이어 받는다.
     */
    public CommentPageDto findInitialPageForView(Long postId, Authentication authentication) {
        return pageForView(postId, null, authentication);
    }

    /** 커서({@code afterId}) 이후 다음 페이지. {@code afterId}는 마지막으로 받은 댓글의 id다. */
    public CommentPageDto findNextPageForView(Long postId, Long afterId, Authentication authentication) {
        return pageForView(postId, afterId, authentication);
    }

    /**
     * {@code PAGE_SIZE + 1}개를 가져와 {@code PAGE_SIZE}를 넘으면 마지막 한 개를 잘라내고
     * {@code hasMore=true}로 표시한다 — 별도의 COUNT 쿼리 없이 "다음이 있는지"를 판정한다.
     * 화면에 보여줄 전체 개수는 이와 별개로 {@code countByPostId}를 조회해 담는다.
     * <p>
     * 답글은 부모마다 최대 {@link #INITIAL_REPLIES_PER_PARENT}개만 함께 내려준다(개선 보고서
     * COR-05). 부모 수만큼(최대 {@link #PAGE_SIZE}회) 별도 조회가 도는 대신, 어떤 부모의
     * 답글도 영영 숨겨지지 않는다는 것을 보장한다 — 이 페이지의 최상위 댓글 수 자체가 이미
     * {@code PAGE_SIZE}로 작게 제한되어 있어 그 반복 횟수도 함께 작다.
     */
    private CommentPageDto pageForView(Long postId, Long afterId, Authentication authentication) {
        List<Comment> fetched = commentRepository.findPageByPostIdAsc(postId, afterId, PageRequest.of(0, PAGE_SIZE + 1));
        boolean hasMore = fetched.size() > PAGE_SIZE;
        List<Comment> page = hasMore ? fetched.subList(0, PAGE_SIZE) : fetched;

        List<Long> topLevelIds = page.stream().map(Comment::getId).toList();
        Map<Long, Long> replyCounts = commentRepository.countRepliesByParentIdIn(topLevelIds);
        // 부모마다 따로 부르지 않고(BE-08) 이 페이지의 최상위 댓글 전체를 대상으로 한 번에
        // 가져온다 — 최대 페이지당 20회이던 쿼리가 이 한 번으로 줄어든다.
        Map<Long, List<Comment>> repliesByParent =
                commentRepository.findInitialRepliesGroupedByParentIdIn(topLevelIds, INITIAL_REPLIES_PER_PARENT);

        List<CommentViewDto> views = page.stream()
                .map(comment -> withInitialReplies(comment, authentication, replyCounts, repliesByParent))
                .toList();
        long totalCount = commentRepository.countByPostId(postId);
        return new CommentPageDto(views, totalCount, hasMore);
    }

    private CommentViewDto withInitialReplies(Comment comment, Authentication authentication,
                                               Map<Long, Long> replyCounts,
                                               Map<Long, List<Comment>> repliesByParent) {
        long replyCount = replyCounts.getOrDefault(comment.getId(), 0L);
        List<CommentViewDto> replies = repliesByParent.getOrDefault(comment.getId(), List.of()).stream()
                .map(reply -> new CommentViewDto(reply, OwnershipPolicy.canManage(authentication, reply.getUser())))
                .toList();
        boolean hasMoreReplies = replyCount > replies.size();
        return new CommentViewDto(comment, OwnershipPolicy.canManage(authentication, comment.getUser()))
                .withReplies(replies, replyCount, hasMoreReplies);
    }

    /**
     * "답글 더 보기"(개선 보고서 COR-05) — {@code afterId} 이후의 답글을 최대
     * {@link #REPLIES_PAGE_SIZE}개 반환한다. 존재하지 않거나 답글이 없는 부모 id를 넘기면
     * 빈 페이지를 돌려준다(읽기 전용 조회라 별도의 404로 구분하지 않는다).
     */
    public CommentPageDto findRepliesPage(Long parentId, Long afterId, Authentication authentication) {
        List<Comment> fetched = commentRepository.findRepliesByParentIdAsc(
                parentId, afterId, PageRequest.of(0, REPLIES_PAGE_SIZE + 1));
        boolean hasMore = fetched.size() > REPLIES_PAGE_SIZE;
        List<Comment> page = hasMore ? fetched.subList(0, REPLIES_PAGE_SIZE) : fetched;

        List<CommentViewDto> views = page.stream()
                .map(reply -> new CommentViewDto(reply, OwnershipPolicy.canManage(authentication, reply.getUser())))
                .toList();
        long totalCount = commentRepository.countRepliesByParentIdIn(List.of(parentId)).getOrDefault(parentId, 0L);
        return new CommentPageDto(views, totalCount, hasMore);
    }

    private Comment findComment(Long id) {
        return commentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("해당 댓글이 없습니다. id=" + id));
    }

    private User findUser(Authentication authentication) {
        return CurrentUser.require(authentication, userRepository);
    }
}
