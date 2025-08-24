package com.boardly.post.service;


import com.boardly.post.domain.Post;
import com.boardly.post.infra.PostRepository;
import com.boardly.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {
    private final PostRepository postRepository;


    @Transactional
    public Long create(User author, String title, String content) {
        Post post = Post.builder()
                .author(author)
                .title(title)
                .content(content)
                .viewCount(0)
                .build();
        return postRepository.save(post).getId();
    }


    public Post get(Long id) {
        return postRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("게시글을 찾을 수 없습니다."));
    }


    @Transactional
    public void update(Long id, String title, String content) {
        Post post = get(id);
        post.update(title, content);
    }


    @Transactional
    public void delete(Long id) {
        postRepository.deleteById(id);
    }
}