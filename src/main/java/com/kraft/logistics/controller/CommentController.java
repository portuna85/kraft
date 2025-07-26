package com.kraft.logistics.controller;

import com.kraft.logistics.domain.comment.CommentService;
import com.kraft.logistics.domain.comment.dto.CommentRequestDto;
import com.kraft.logistics.domain.comment.dto.CommentResponseDto;
import com.kraft.logistics.domain.comment.dto.CommentUpdateRequestDto;
import com.kraft.logistics.domain.user.User;
import com.kraft.logistics.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/comments")
public class CommentController {

    private final CommentService commentService;


    @PostMapping
    public CommentResponseDto write(
            @RequestBody CommentRequestDto dto,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        User user = userDetails.getUser();  // 로그인된 사용자
        return commentService.write(dto, user);
    }

    @GetMapping("/post/{postId}")
    public List<CommentResponseDto> getComments(@PathVariable Long postId) {
        return commentService.findByPostId(postId);
    }


    @PutMapping
    public CommentResponseDto updateComment(
            @RequestBody CommentUpdateRequestDto dto,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return commentService.update(dto, userDetails.getUser());
    }

    @DeleteMapping("/{commentId}")
    public void deleteComment(
            @PathVariable Long commentId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        commentService.delete(commentId, userDetails.getUser());
    }

}
