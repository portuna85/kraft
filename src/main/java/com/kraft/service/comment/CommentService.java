package com.kraft.service.comment;

import com.kraft.domain.comment.Comment;
import com.kraft.domain.comment.CommentRepository;
import com.kraft.domain.post.Post;
import com.kraft.domain.post.PostRepository;
import com.kraft.domain.user.EmailHasher;
import com.kraft.domain.user.User;
import com.kraft.domain.user.UserRepository;
import com.kraft.service.support.OwnershipPolicy;
import com.kraft.service.support.WriteAccessPolicy;
import com.kraft.web.dto.comment.CommentResponseDto;
import com.kraft.web.dto.comment.CommentSaveRequestDto;
import com.kraft.web.dto.comment.CommentUpdateRequestDto;
import com.kraft.web.dto.comment.CommentViewDto;
import com.kraft.web.exception.PostNotFoundException;
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
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다. email=" + email));
        WriteAccessPolicy.requireVerified(user);
        return commentRepository.save(requestDto.toEntity(post, user)).getId();
    }

    @Transactional
    public Long update(Long id, CommentUpdateRequestDto requestDto, Authentication authentication) {
        Comment comment = findComment(id);
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
}
