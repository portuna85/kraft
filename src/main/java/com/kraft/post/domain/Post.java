package com.kraft.post.domain;

import com.kraft.shared.domain.BaseEntity;
import com.kraft.user.domain.User;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

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
        // V31__posts_created_at_view_count_index.sql. 인기글이 "최근 글 중 조회수 상위"로
        // 바뀌면서(A-BE-10) created_at 조건 + view_count 정렬을 함께 쓰는 쿼리가 생겼다.
        @Index(name = "IX_POSTS_CREATED_AT_VIEW_COUNT", columnList = "created_at, view_count DESC, id DESC"),
        // V35__posts_updated_at_index.sql. sort=updatedAt(최근 수정순) 목록이 filesort 없이
        // 읽히게 한다(BE-07).
        @Index(name = "IX_POSTS_UPDATED_AT_ID", columnList = "updated_at DESC, id DESC"),
        @Index(name = "IX_POSTS_CATEGORY_UPDATED_AT_ID", columnList = "category, updated_at DESC, id DESC"),
        // V41__posts_visibility_and_pin.sql. 목록 COUNT가 deleted_at·blinded_at 조건을 더해도
        // 인덱스만 읽게 한다.
        @Index(name = "IX_POSTS_VISIBLE", columnList = "deleted_at, blinded_at"),
        @Index(name = "IX_POSTS_CATEGORY_VISIBLE", columnList = "category, deleted_at, blinded_at"),
        @Index(name = "IX_POSTS_PINNED_UNTIL", columnList = "pinned_until"),
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

    /**
     * picture의 실제 픽셀 크기(A-FE-09) — 상세 화면이 {@code <img width height>}를 채워
     * 레이아웃 이동(CLS)을 줄이는 데만 쓴다. picture가 없으면(글에 사진이 없으면) 둘 다
     * null이다. V32 이전에 저장된 글도 null로 남는다 — 소급 채움은 하지 않는다(다시 열람할
     * 때 값이 없다는 것만 다를 뿐 동작에는 지장이 없다).
     */
    private Integer pictureWidth;
    private Integer pictureHeight;

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

    /**
     * null이 아니면 소프트 삭제된 글이다. 보관 기간이 지나면 {@code PostService.purge}가 행을 지운다.
     * <p>
     * 아래 세 상태 컬럼은 {@code viewCount}와 같은 이유로 {@code updatable = false}다(COR-04) —
     * 일반 UPDATE에 실리면 편집 flush가 관리자의 숨김·고정·복구를 옛 값으로 되돌리고, 상태만
     * 바꿔도 version·updatedAt이 올라 열려 있던 편집 탭이 가짜 충돌을 받는다. 바꿀 때는
     * {@code PostRepository}의 전용 UPDATE만 쓴다.
     */
    @Column(name = "deleted_at", updatable = false)
    private LocalDateTime deletedAt;

    /** null이 아니면 관리자가 숨긴 글이다. 신고 처리가 삭제 대신 이 값을 채운다. */
    @Column(name = "blinded_at", updatable = false)
    private LocalDateTime blindedAt;

    /** 이 시각 전까지 목록 상단에 고정된다. null이면 고정되지 않은 글이다. */
    @Column(name = "pinned_until", updatable = false)
    private LocalDateTime pinnedUntil;

    @Builder
    public Post(String title, String content, String picture, Integer pictureWidth, Integer pictureHeight,
                User user, Category category) {
        this.title = title;
        this.content = content;
        this.picture = picture;
        this.pictureWidth = pictureWidth;
        this.pictureHeight = pictureHeight;
        this.user = user;
        this.category = category != null ? category : Category.FREE;
        this.viewCount = 0L;
    }

    public void update(String title, String content, String picture, Integer pictureWidth, Integer pictureHeight,
                        Category category) {
        this.title = title;
        this.content = content;
        this.picture = picture;
        this.pictureWidth = pictureWidth;
        this.pictureHeight = pictureHeight;
        this.category = category != null ? category : this.category;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean isBlinded() {
        return blindedAt != null;
    }

    public boolean isPinnedAt(LocalDateTime now) {
        return pinnedUntil != null && pinnedUntil.isAfter(now);
    }

    /** 서버가 측정한 크기로 클라이언트가 보낸 값을 덮어쓴다(BE-24). */
    public void updatePictureSize(int width, int height) {
        this.pictureWidth = width;
        this.pictureHeight = height;
    }
}
