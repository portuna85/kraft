package com.kraft.comment.domain;

import com.kraft.post.domain.Post;
import com.kraft.shared.domain.BaseEntity;
import com.kraft.user.domain.User;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 게시글에 달리는 댓글. 일반사용자는 작성/수정/삭제가 가능하고, 모든 사용자는 조회할 수 있다.
 */
@Getter
@Entity
@NoArgsConstructor
@Table(name = "comments", indexes = {
        // V6__image_quota_and_search_indexes.sql. 엔티티에 선언이 없어 ddl-auto: update로
        // 만든 기존 DB에는 이 인덱스가 생기지 않았다(개선 보고서 O01).
        @Index(name = "IX_COMMENTS_POST", columnList = "post_id"),
})
public class Comment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id")
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Builder
    public Comment(String content, Post post, User user) {
        this.content = content;
        this.post = post;
        this.user = user;
    }

    public void update(String content) {
        this.content = content;
    }
}
