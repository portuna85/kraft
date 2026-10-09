package com.kraft.comment.domain;

import com.kraft.post.domain.Post;
import com.kraft.shared.domain.BaseEntity;
import com.kraft.user.domain.User;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 게시글에 달리는 댓글(2단계까지). */
@Getter
@Entity
@NoArgsConstructor
@Table(name = "comments", indexes = {
        @Index(name = "IX_COMMENTS_PARENT", columnList = "parent_id"),
        // CommentRepository.findPageByPostIdAsc가 post_id = ? AND parent_id IS NULL을 id
        // 순으로 훑는다(V23).
        @Index(name = "IX_COMMENTS_POST_PARENT_ID", columnList = "post_id, parent_id, id"),
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

    /** 답글이면 그 대상(최상위 댓글), 아니면 null. 2단계까지만 허용하고(서비스가 검증) DB cascade는 일부러 걸지 않는다 — {@code CommentService.delete()}가 답글을 먼저 지운다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Comment parent;

    /** 동시 편집 충돌 감지 — 받아간 버전과 다르면 저장을 거부한다({@code CommentService.update}). */
    @Version
    private Long version;

    /** null이 아니면 소프트 삭제 — 답글이 있는 최상위 댓글을 지울 때 행을 두고 이 시각만 남긴다({@link #softDelete()}). */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /** null이 아니면 관리자가 숨긴 댓글. {@code updatable = false}라 {@code CommentRepository}의 전용 UPDATE로만 바꾼다. */
    @Column(name = "blinded_at", updatable = false)
    private LocalDateTime blindedAt;

    @Builder
    public Comment(String content, Post post, User user, Comment parent) {
        this.content = content;
        this.post = post;
        this.user = user;
        this.parent = parent;
    }

    public void update(String content) {
        this.content = content;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean isBlinded() {
        return blindedAt != null;
    }

    /** 답글이 있어 행을 지울 수 없을 때 부른다. 내용을 비워 원문이 남지 않게 한다. */
    public void softDelete() {
        this.content = "";
        this.deletedAt = LocalDateTime.now();
    }
}
