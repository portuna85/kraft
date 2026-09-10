package com.kraft.service.post;

import com.kraft.domain.post.Post;
import com.kraft.domain.post.PostRepository;
import com.kraft.domain.user.EmailHasher;
import com.kraft.domain.user.User;
import com.kraft.domain.user.UserRepository;
import com.kraft.service.support.OwnershipPolicy;
import com.kraft.web.dto.post.PostResponseDto;
import com.kraft.web.dto.post.PostSaveRequestDto;
import com.kraft.web.dto.post.PostUpdateRequestDto;
import com.kraft.web.dto.post.PostsListResponseDto;
import com.kraft.web.dto.post.PostsPageResponseDto;
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

    @Transactional
    public Long save(String email, PostSaveRequestDto requestDto) {
        User user = userRepository.findByEmailHash(EmailHasher.sha512Hex(email))
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다. email=" + email));
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
        postRepository.delete(post);
    }

    public PostResponseDto findById(Long id) {
        return new PostResponseDto(findPost(id));
    }

    public PostsPageResponseDto findAllDesc(Pageable pageable) {
        Page<PostsListResponseDto> page = postRepository.findAllDesc(pageable)
                .map(PostsListResponseDto::new);
        return new PostsPageResponseDto(page);
    }

    private Post findPost(Long id) {
        return postRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("해당 게시글이 없습니다. id=" + id));
    }

    /**
     * 작성자 본인 또는 관리자만 게시글을 수정·삭제할 수 있다.
     */
    private void validateOwner(Post post, Authentication authentication) {
        OwnershipPolicy.validateOwner(authentication, post.getUser(), post.getId());
    }
}
