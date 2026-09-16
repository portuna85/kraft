package com.kraft.comment.service;

import com.kraft.comment.domain.Comment;
import com.kraft.comment.domain.CommentRepository;
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
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Service
@Transactional(readOnly = true)
public class CommentService {

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
     * 상세 화면용 댓글 조회. 댓글마다 관리 권한을 서버에서 계산해 내려준다. 조회 쿼리가 이미
     * {@code JOIN FETCH c.user}이므로 권한 계산 때문에 댓글당 추가 조회가 발생하지 않는다.
     */
    public List<CommentViewDto> findByPostIdForView(Long postId, Authentication authentication) {
        return commentRepository.findAllByPostIdAsc(postId).stream()
                .map(comment -> new CommentViewDto(comment,
                        OwnershipPolicy.canManage(authentication, comment.getUser())))
                .toList();
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
