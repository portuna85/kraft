package com.kraft.post.domain;

import com.kraft.shared.domain.BaseEntity;
import com.kraft.user.domain.User;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 로그인 후 사용자가 작성한 게시글
 *
 */
@Getter
@Entity
@NoArgsConstructor
@Table(name = "posts", indexes = {
        // V6__image_quota_and_search_indexes.sql. 엔티티에 선언이 없어 ddl-auto: update로
        // 만든 기존 DB에는 이 인덱스들이 생기지 않았다(개선 보고서 O01).
        @Index(name = "IX_POSTS_CATEGORY_ID", columnList = "category, id"),
        @Index(name = "IX_POSTS_VIEW_COUNT", columnList = "view_count DESC, id DESC"),
})
public class Post extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 255, nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    // 사진등록 타입이 String가 맞나요? -> String이 맞다. 실제 이미지 바이너리가 아니라
    // 파일 경로/URL을 저장하는 용도이므로 500자로 제한한다.
    @Column(length = 500)
    private String picture;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Category category;

    /**
     * {@code updatable = false} — Hibernate가 만드는 일반 UPDATE(예: {@link #update})에서
     * 이 컬럼을 아예 빼도록 강제한다(개선 보고서 COR-04). 조회수는 오직
     * {@code PostRepository.increaseViewCount}의 전용 원자적 UPDATE로만 바뀐다. 이 플래그가
     * 없으면, 편집 화면이 옛 조회수를 들고 있는 동안 다른 트랜잭션이 조회수를 올려 커밋하고,
     * 그 뒤 편집이 flush되는 순서에서 편집의 전체 컬럼 UPDATE가 그 증가분을 그대로 덮어쓸 수
     * 있다 — {@code @Version}은 조회수 증가가 version을 바꾸지 않으므로 이 경우를 잡지 못한다.
     */
    @Column(name = "view_count", nullable = false, updatable = false)
    private long viewCount;

    /**
     * 편집 충돌 감지용 낙관적 잠금 버전. 상세 화면이 이 값을 함께 내려주고, 수정 요청이 그대로
     * 돌려보낸다 — 그 사이 다른 곳에서 저장이 일어났다면 값이 달라져 409로 거절된다.
     * 예전에는 두 사람이 같은 글을 편집해도 나중 저장이 먼저 저장을 말없이 덮어썼다.
     */
    @Version
    @Column(nullable = false)
    private Long version;

    @Builder
    public Post(String title, String content, String picture, User user, Category category) {
        this.title = title;
        this.content = content;
        this.picture = picture;
        this.user = user;
        this.category = category != null ? category : Category.FREE;
        this.viewCount = 0L;
    }

    public void update(String title, String content, String picture, Category category) {
        this.title = title;
        this.content = content;
        this.picture = picture;
        this.category = category != null ? category : this.category;
    }
}
