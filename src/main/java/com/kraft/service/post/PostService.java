package com.kraft.service.post;

import com.kraft.domain.comment.CommentRepository;
import com.kraft.domain.post.Post;
import com.kraft.domain.post.PostRepository;
import com.kraft.domain.user.EmailHasher;
import com.kraft.domain.user.User;
import com.kraft.domain.user.UserRepository;
import com.kraft.service.support.OwnershipPolicy;
import com.kraft.service.support.WriteAccessPolicy;
import com.kraft.web.dto.post.PostResponseDto;
import com.kraft.web.dto.post.PostSaveRequestDto;
import com.kraft.web.dto.post.PostUpdateRequestDto;
import com.kraft.web.dto.post.PostViewDto;
import com.kraft.web.dto.post.PostsListResponseDto;
import com.kraft.web.dto.post.PostsPageResponseDto;
import com.kraft.web.exception.PostNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
@Transactional(readOnly = true)
public class PostService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final CommentRepository commentRepository;

    @Transactional
    public Long save(String email, PostSaveRequestDto requestDto) {
        User user = userRepository.findByEmailHash(EmailHasher.sha512Hex(email))
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다. email=" + email));
        WriteAccessPolicy.requireVerified(user);
        return postRepository.save(requestDto.toEntity(user)).getId();
    }

    @Transactional
    public Long update(Long id, PostUpdateRequestDto requestDto, Authentication authentication) {
        Post post = findPost(id);
        validateOwner(post, authentication);
        post.update(requestDto.title(), requestDto.content());
        return id;
    }

    @Transactional
    public void delete(Long id, Authentication authentication) {
        Post post = findPost(id);
        validateOwner(post, authentication);
        // 댓글이 남아 있으면 comments.post_id FK 제약 위반으로 삭제가 실패하므로 먼저 지운다.
        commentRepository.deleteAllByPostId(id);
        postRepository.delete(post);
    }

    public PostResponseDto findById(Long id) {
        return new PostResponseDto(findPost(id));
    }

    /**
     * 상세 화면용 조회. 인증 객체와 엔티티를 함께 볼 수 있는 이 지점에서 관리 권한을 계산해
     * 화면 전용 DTO로 내려준다. 화면이 작성자 이름과 로그인 이메일을 비교하는 방식(잘못된
     * 소유권 추정)을 쓰지 않게 하려는 것이다. 공개 REST DTO는 그대로 둔다.
     */
    public PostViewDto findByIdForView(Long id, Authentication authentication) {
        Post post = findPost(id);
        return new PostViewDto(post, OwnershipPolicy.canManage(authentication, post.getUser()));
    }

    public PostsPageResponseDto findAllDesc(Pageable pageable) {
        Page<PostsListResponseDto> page = postRepository.findAllDesc(pageable)
                .map(PostsListResponseDto::new);
        return new PostsPageResponseDto(page);
    }

    private Post findPost(Long id) {
        return postRepository.findById(id)
                .orElseThrow(() -> new PostNotFoundException(id));
    }

    /**
     * 작성자 본인 또는 관리자만 게시글을 수정·삭제할 수 있다.
     */
    private void validateOwner(Post post, Authentication authentication) {
        OwnershipPolicy.validateOwner(authentication, post.getUser(), post.getId());
    }
}
