package com.kraft.comment.dto;

import com.kraft.comment.domain.Comment;
import com.kraft.post.domain.Post;
import com.kraft.shared.domain.ContentPolicy;
import com.kraft.user.domain.User;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * parentId가 있으면 답글로 등록한다(2단계 댓글). 그 댓글이 실제로 같은 게시글 소속인지,
 * 이미 답글은 아닌지(3단계 금지)는 DB 조회가 필요해 bean validation이 아니라
 * {@code CommentService.save}에서 검증한다.
 */
public record CommentSaveRequestDto(

        @NotBlank(message = "내용은 필수입니다.")
        @Size(max = ContentPolicy.COMMENT_CONTENT_MAX_LENGTH, message = "댓글은 {max}자 이하로 입력하세요.")
        String content,

        Long parentId
) {

    public Comment toEntity(Post post, User user, Comment parent) {
        return Comment.builder()
                .content(content)
                .post(post)
                .user(user)
                .parent(parent)
                .build();
    }
}
