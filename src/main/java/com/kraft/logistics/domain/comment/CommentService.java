package com.kraft.logistics.domain.comment;

import com.kraft.logistics.domain.comment.dto.CommentRequestDto;
import com.kraft.logistics.domain.comment.dto.CommentResponseDto;
import com.kraft.logistics.domain.comment.dto.CommentUpdateRequestDto;
import com.kraft.logistics.domain.post.Post;
import com.kraft.logistics.domain.post.PostRepository;
import com.kraft.logistics.domain.user.User;
import com.kraft.logistics.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final PostRepository postRepository;

    public CommentResponseDto write(CommentRequestDto dto, User user) {
        Post post = postRepository.findById(dto.getPostId())
                .orElseThrow(() -> new IllegalArgumentException("Post not found"));

        Comment comment = Comment.builder()
                .content(dto.getContent())
                .user(user)
                .post(post)
                .build();

        Comment saved = commentRepository.save(comment);
        return new CommentResponseDto(saved.getId(), saved.getContent(), user.getNickname());
    }

    public CommentResponseDto update(CommentUpdateRequestDto dto, User loginUser) {
        Comment comment = commentRepository.findById(dto.getCommentId())
                .orElseThrow(() -> new IllegalArgumentException("댓글이 존재하지 않습니다."));

        if (!comment.getUser().getId().equals(loginUser.getId())) {
            throw new IllegalStateException("본인의 댓글만 수정할 수 있습니다.");
        }

        comment.setContent(dto.getContent());
        return new CommentResponseDto(comment.getId(), comment.getContent(), loginUser.getNickname());
    }

    public void delete(Long commentId, User loginUser) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("댓글이 존재하지 않습니다."));

        if (!comment.getUser().getId().equals(loginUser.getId())) {
            throw new IllegalStateException("본인의 댓글만 삭제할 수 있습니다.");
        }

        commentRepository.delete(comment);
    }


    public List<CommentResponseDto> findByPostId(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found"));

        return commentRepository.findByPost(post).stream()
                .map(c -> new CommentResponseDto(c.getId(), c.getContent(), c.getUser().getNickname()))
                .toList();
    }
}
