package com.kraft.logistics.domain.post;

import com.kraft.logistics.domain.post.dto.PostResponseDto;
import com.kraft.logistics.domain.post.dto.PostWriteRequestDto;
import com.kraft.logistics.domain.user.User;
import com.kraft.logistics.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;

    public PostResponseDto write(PostWriteRequestDto dto) {
        User author = userRepository.findById(dto.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("작성자 없음"));

        Post post = Post.builder()
                .title(dto.getTitle())
                .content(dto.getContent())
                .author(author)
                .build();

        Post saved = postRepository.save(post);
        return new PostResponseDto(saved.getId(), saved.getTitle(), saved.getContent(), author.getNickname());
    }
}
