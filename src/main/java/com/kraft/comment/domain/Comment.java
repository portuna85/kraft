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
}
