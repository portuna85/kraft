package com.kraft.post.domain;

import com.kraft.shared.domain.BaseEntity;
import com.kraft.user.domain.User;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 사용자가 작성한 게시글. */
@Getter
@Entity
@NoArgsConstructor
@Table(name = "posts", indexes = {
        // 인덱스는 Flyway(V6·V31·V35·V41)와 일치해야 한다 — MariaDbMigrationTest가 대조한다.
        @Index(name = "IX_POSTS_CATEGORY_ID", columnList = "category, id"),
        @Index(name = "IX_POSTS_VIEW_COUNT", columnList = "view_count DESC, id DESC"),
        @Index(name = "IX_POSTS_CREATED_AT_VIEW_COUNT", columnList = "created_at, view_count DESC, id DESC"),
        @Index(name = "IX_POSTS_UPDATED_AT_ID", columnList = "updated_at DESC, id DESC"),
        @Index(name = "IX_POSTS_CATEGORY_UPDATED_AT_ID", columnList = "category, updated_at DESC, id DESC"),
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

    // 이미지 바이너리가 아니라 파일 경로/URL이다.
    @Column(length = 500)
    private String picture;

    /** picture의 픽셀 크기({@code <img width height>}로 CLS를 줄이는 용도). 사진이 없거나 V32 이전에 저장된 글은 null. */
    private Integer pictureWidth;
    private Integer pictureHeight;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Category category;

    /**
     * {@code updatable = false} — 일반 UPDATE에 실리면 편집 flush가 그 사이 올라간 조회수를 덮어쓴다
     * ({@code @Version}은 조회수 증가를 잡지 못한다). {@code PostRepository.increaseViewCount}로만 바꾼다.
     */
    @Column(name = "view_count", nullable = false, updatable = false)
    private long viewCount;

    /** 편집 충돌 감지용 낙관적 잠금 버전. 수정 요청이 보낸 값과 다르면 409로 거절된다. */
    @Version
    @Column(nullable = false)
    private Long version;

    /**
     * null이 아니면 소프트 삭제된 글이다({@code PostService.purge}가 보관 기간 뒤 지운다). 아래 세 상태 컬럼은
     * {@code viewCount}처럼 {@code updatable = false}다 — 일반 UPDATE에 실리면 편집 flush가 관리자의 숨김·고정·
     * 복구를 되돌리고 version이 올라 가짜 충돌이 난다. {@code PostRepository}의 전용 UPDATE로만 바꾼다.
     */
    @Column(name = "deleted_at", updatable = false)
    private LocalDateTime deletedAt;

    /** null이 아니면 관리자가 숨긴 글이다. */
    @Column(name = "blinded_at", updatable = false)
    private LocalDateTime blindedAt;

    /** 이 시각 전까지 목록 상단에 고정된다. null이면 고정되지 않은 글이다. */
    @Column(name = "pinned_until", updatable = false)
    private LocalDateTime pinnedUntil;

    /** {@code pinnedUntil}은 시드·테스트가 고정된 글을 만들 때만 쓴다(운영에서는 전용 UPDATE로 고정·해제). */
    @Builder
    public Post(String title, String content, String picture, Integer pictureWidth, Integer pictureHeight,
                User user, Category category, LocalDateTime pinnedUntil) {
        this.title = title;
        this.content = content;
        this.picture = picture;
        this.pictureWidth = pictureWidth;
        this.pictureHeight = pictureHeight;
        this.user = user;
        this.category = category != null ? category : Category.FREE;
        this.pinnedUntil = pinnedUntil;
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

    /** 서버가 측정한 크기로 클라이언트가 보낸 값을 덮어쓴다. */
    public void updatePictureSize(int width, int height) {
        this.pictureWidth = width;
        this.pictureHeight = height;
    }
}
