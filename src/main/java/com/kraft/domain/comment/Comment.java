package com.kraft.domain.comment;

import com.kraft.common.entity.BaseEntity;
import com.kraft.domain.post.Post;
import com.kraft.domain.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 댓글 엔티티
 * - Post와 User에 대한 ManyToOne 관계
 * - 대댓글 지원 (Self-Referencing)
 * - 불변성 강화: Builder로만 생성 가능
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "comments", indexes = {
    @Index(name = "idx_comment_post_id", columnList = "post_id"),
    @Index(name = "idx_comment_author_id", columnList = "author_id"),
    @Index(name = "idx_comment_parent_id", columnList = "parent_id"),
    @Index(name = "idx_comment_created_at", columnList = "create_at")
})
public class Comment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false, foreignKey = @ForeignKey(name = "fk_comment_post"))
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false, foreignKey = @ForeignKey(name = "fk_comment_author"))
    private User author;

    // 부모 댓글 (대댓글인 경우)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id", foreignKey = @ForeignKey(name = "fk_comment_parent"))
    private Comment parent;

    // 자식 댓글들 (답글들)
    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Comment> replies = new ArrayList<>();

    @Builder
    private Comment(String content, Post post, User author, Comment parent) {
        this.content = content;
        this.post = post;
        this.author = author;
        this.parent = parent;
    }

    /**
     * 댓글 내용 수정
     * @param content 수정할 내용
     */
    public void update(String content) {
        this.content = content;
    }

    /**
     * 작성자 확인
     * @param userId 확인할 사용자 ID
     * @return 작성자 여부
     */
    public boolean isAuthor(Long userId) {
        return this.author.getId().equals(userId);
    }

    /**
     * 대댓글인지 확인
     * @return 대댓글 여부
     */
    public boolean isReply() {
        return this.parent != null;
    }

    /**
     * 답글 추가
     * @param reply 답글
     */
    public void addReply(Comment reply) {
        if (!this.replies.contains(reply)) {
            this.replies.add(reply);
        }
    }

    /**
     * 답글 목록 조회 (읽기 전용)
     * @return 답글 목록
     */
    public List<Comment> getReplies() {
        return Collections.unmodifiableList(replies);
    }
}

