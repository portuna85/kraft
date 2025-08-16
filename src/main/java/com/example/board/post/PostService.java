package com.example.board.post;

import com.example.board.member.Member;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class PostService {
    private final PostRepository postRepository;

    public PostService(PostRepository postRepository) {
        this.postRepository = postRepository;
    }

    public Post write(String title, String content, Member author) {
        return postRepository.save(new Post(title, content, author));
    }

    public Post edit(Long id, String title, String content, Member requester) {
        Post post = getById(id);
        if (!post.getAuthor().getId().equals(requester.getId())) {
            throw new SecurityException("작성자만 수정할 수 있습니다.");
        }
        post.edit(title, content);
        return post;
    }

    public void delete(Long id, Member requester) {
        Post post = getById(id);
        if (!post.getAuthor().getId().equals(requester.getId())) {
            throw new SecurityException("작성자만 삭제할 수 있습니다.");
        }
        postRepository.delete(post);
    }

    public Post getById(Long id) {
        return postRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("게시글을 찾을 수 없습니다."));
    }

    public Page<Post> list(String keyword, Pageable pageable) {
        if (keyword == null || keyword.isBlank()) return postRepository.findAll(pageable);
        return postRepository.findByTitleContainingIgnoreCase(keyword, pageable);
    }
}
