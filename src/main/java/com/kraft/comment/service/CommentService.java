package com.kraft.comment.service;

import com.kraft.comment.domain.Comment;
import com.kraft.comment.domain.CommentRepository;
import com.kraft.comment.dto.CommentPageDto;
import com.kraft.comment.dto.CommentResponseDto;
import com.kraft.comment.dto.CommentSaveRequestDto;
import com.kraft.comment.dto.CommentUpdateRequestDto;
import com.kraft.comment.dto.CommentViewDto;
import com.kraft.post.domain.Post;
import com.kraft.post.domain.PostNotFoundException;
import com.kraft.post.domain.PostRepository;
import com.kraft.shared.security.OwnershipPolicy;
import com.kraft.shared.security.WriteAccessPolicy;
import com.kraft.user.domain.EmailHasher;
import com.kraft.user.domain.EmailMasker;
import com.kraft.user.domain.User;
import com.kraft.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Service
@Transactional(readOnly = true)
public class CommentService {

    /**
     * 커서 페이지 한 번에 내려주는 댓글 수. 상세 화면 최초 렌더와 "더 보기"가 함께 쓴다
     * (개선 보고서 "댓글 전체 로딩").
     */
    private static final int PAGE_SIZE = 20;

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;

    @Transactional
    public Long save(Long postId, String email, CommentSaveRequestDto requestDto) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException(postId));
        User user = userRepository.findByEmailHash(EmailHasher.sha512Hex(email))
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다. email=" + EmailMasker.mask(email)));
        WriteAccessPolicy.requireVerified(user);
        return commentRepository.save(requestDto.toEntity(post, user)).getId();
    }

    /**
     * 정지된 계정도 이 경로로 자신의 기존 댓글을 계속 바꿀 수 있었다 — 작성만 작성 정책을
     * 검사하고 수정은 소유권만 봤기 때문이다. 삭제는 의도적으로 그대로 둔다 — 정지된
     * 사용자도 자신의 댓글을 지우는 것까지 막지는 않는다.
     */
    @Transactional
    public Long update(Long id, CommentUpdateRequestDto requestDto, Authentication authentication) {
        Comment comment = findComment(id);
        WriteAccessPolicy.requireVerified(findUser(authentication.getName()));
        OwnershipPolicy.validateOwner(authentication, comment.getUser(), id);
        comment.update(requestDto.content());
        return id;
    }

    @Transactional
    public void delete(Long id, Authentication authentication) {
        Comment comment = findComment(id);
        OwnershipPolicy.validateOwner(authentication, comment.getUser(), id);
        commentRepository.delete(comment);
    }

    public List<CommentResponseDto> findByPostId(Long postId) {
        return commentRepository.findAllByPostIdAsc(postId).stream()
                .map(CommentResponseDto::new)
                .toList();
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
     */
    private CommentPageDto pageForView(Long postId, Long afterId, Authentication authentication) {
        List<Comment> fetched = commentRepository.findPageByPostIdAsc(postId, afterId, PageRequest.of(0, PAGE_SIZE + 1));
        boolean hasMore = fetched.size() > PAGE_SIZE;
        List<Comment> page = hasMore ? fetched.subList(0, PAGE_SIZE) : fetched;
        List<CommentViewDto> views = page.stream()
                .map(comment -> new CommentViewDto(comment,
                        OwnershipPolicy.canManage(authentication, comment.getUser())))
                .toList();
        long totalCount = commentRepository.countByPostId(postId);
        return new CommentPageDto(views, totalCount, hasMore);
    }

    private Comment findComment(Long id) {
        return commentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("해당 댓글이 없습니다. id=" + id));
    }

    private User findUser(String email) {
        return userRepository.findByEmailHash(EmailHasher.sha512Hex(email))
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다. email=" + EmailMasker.mask(email)));
    }
}
