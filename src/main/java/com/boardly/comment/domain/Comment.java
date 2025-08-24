// src/main/java/com/boardly/comment/domain/Comment.java
package com.boardly.comment.domain;

import com.boardly.common.domain.BaseTimeEntity;
import com.boardly.post.domain.Post;
import com.boardly.user.domain.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Entity
@Table(name = "comments") // ★ 추가
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor @Builder
public class Comment extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User author;

    @NotBlank @Lob
    private String content;
}
