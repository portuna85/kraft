package com.boardly.comment.service;


import com.boardly.comment.domain.Comment;
import com.boardly.comment.infra.CommentRepository;
import com.boardly.post.domain.Post;
import com.boardly.post.service.PostService;
import com.boardly.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentService {
    private final CommentRepository commentRepository;
    private final PostService postService;


    @Transactional
    public Long addComment(Long postId, User author, String content) {
        Post post = postService.get(postId);
        Comment comment = Comment.builder()
                .post(post)
                .author(author)
                .content(content)
                .build();
        return commentRepository.save(comment).getId();
    }
}