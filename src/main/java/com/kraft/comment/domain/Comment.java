package com.kraft.comment.domain;

import com.kraft.post.domain.Post;
import com.kraft.shared.domain.BaseEntity;
import com.kraft.user.domain.User;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 게시글에 달리는 댓글. 일반사용자는 작성/수정/삭제가 가능하고, 모든 사용자는 조회할 수 있다.
 */
@Getter
@Entity
@NoArgsConstructor
@Table(name = "comments", indexes = {
        // V6__image_quota_and_search_indexes.sql의 IX_COMMENTS_POST(post_id)는 V25에서
        // 지웠다 — 아래 POST_PARENT_ID가 왼쪽 접두사로 post_id를 이미 포함해 남는 쓰기
        // 비용만 만들었다(개선 보고서 BE-11).
        @Index(name = "IX_COMMENTS_PARENT", columnList = "parent_id"),
        // CommentRepository.findPageByPostIdAsc가 post_id = ? AND parent_id IS NULL을 id
        // 순으로 훑는다(V23, 개선 보고서 PERF-05).
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

    /**
     * 이 댓글이 답글이면 그 대상(최상위 댓글). null이면 최상위 댓글이다. 2단계까지만
     * 허용한다 — 답글 자신은 절대 이 필드가 채워진 댓글을 부모로 가질 수 없다(서비스 계층에서
     * 검증, {@code CommentService.save} 참고). DB에는 일부러 cascade를 걸지 않는다 —
     * {@code CommentService.delete()}가 최상위 댓글을 지우기 전에 답글을 먼저 명시적으로
     * 지운다(이 클래스 상단 주석, V19 마이그레이션 참고).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Comment parent;

    /**
     * 동시 편집 충돌을 감지한다(B12). 두 탭이 같은 댓글을 각각 편집해 저장하면, 이 필드가
     * 없던 예전에는 나중에 flush되는 쪽이 앞선 내용을 조용히 덮어썼다. {@code Post.version}과
     * 같은 방식이다 — 클라이언트가 받아간 시점의 버전과 다르면 저장을 거부한다
     * ({@code CommentService.update} 참고).
     */
    @Version
    private Long version;

    /**
     * null이 아니면 소프트 삭제된 것이다(개선 보고서 A-BE-06) — 답글이 있는 최상위 댓글을
     * 지우면 행을 그대로 두고 이 시각만 남긴다({@link #softDelete()}). 답글이 없으면(또는
     * 답글 자신이면) 지금처럼 행 자체를 지우므로 이 필드를 거치지 않는다.
     */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /**
     * null이 아니면 관리자가 숨긴 댓글이다(V41). {@code Post.blindedAt}과 같은 이유로
     * {@code updatable = false} — 바꿀 때는 {@code CommentRepository}의 전용 UPDATE만 쓴다.
     */
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

    /**
     * 답글이 있어 행을 지울 수 없을 때 대신 부른다(A-BE-06). 내용을 비워 원문이 남지 않게
     * 한다 — 신고에 걸려 있던 내용은 신고 접수 시점의 스냅샷(A-SEC-07)에 별도로 남는다.
     */
    public void softDelete() {
        this.content = "";
        this.deletedAt = LocalDateTime.now();
    }
}
