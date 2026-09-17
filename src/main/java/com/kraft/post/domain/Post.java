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

    @Column(name = "view_count", nullable = false)
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

    /**
     * <b>조회 경로에서는 쓰지 않는다.</b> 상세 화면의 조회수 증가는
     * {@code PostRepository.increaseViewCount(id)}의 원자적 UPDATE로 처리한다 — 엔티티를 바꿔
     * 변경 감지에 맡기면 Hibernate가 제목·본문·분류까지 함께 UPDATE에 실어, 그 사이 다른
     * 트랜잭션이 저장한 내용을 열람만으로 되돌릴 수 있었다(개선 보고서 F02). 감사 필드
     * {@code updatedAt}까지 갱신되어 목록의 최종수정일도 오염됐다(F11).
     * <p>
     * 테스트에서 조회수를 가진 게시글을 만들 때만 남겨둔다.
     */
    public void increaseViewCount() {
        this.viewCount++;
    }
}
