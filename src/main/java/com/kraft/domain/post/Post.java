package com.kraft.domain.post;

import com.kraft.common.entity.BaseEntity;
import com.kraft.domain.category.Category;
import com.kraft.domain.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 게시글 엔티티
 * - 불변성 강화: Builder로만 생성 가능
 * - 양방향 관계: User.addPost()를 통해서만 관리
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "posts", indexes = {
    @Index(name = "idx_post_author_id", columnList = "author_id"),
    @Index(name = "idx_post_created_at", columnList = "create_at")
})
public class Post extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 500, nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false, foreignKey = @ForeignKey(name = "fk_post_author"))
    private User author;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", foreignKey = @ForeignKey(name = "fk_post_category"))
    private Category category;

    @Column(nullable = false)
    private Long viewCount = 0L;

    @Builder
    private Post(String title, String content, User author, Category category) {
        this.title = title;
        this.content = content;
        this.author = author;
        this.category = category;
        this.viewCount = 0L;
    }

    /**
     * 게시글 내용 수정
     * @param title 제목
     * @param content 내용
     */
    public void update(String title, String content) {
        this.title = title;
        this.content = content;
    }

    /**
     * 카테고리 변경
     * @param category 카테고리
     */
    public void updateCategory(Category category) {
        this.category = category;
    }

    /**
     * 조회수 증가
     */
    public void incrementViewCount() {
        this.viewCount++;
    }

    /**
     * 연관관계 편의 메서드
     * ⚠️ 주의: User.addPost()를 통해서만 호출되어야 합니다.
     * 외부에서 직접 호출하지 마세요.
     *
     * @param author 게시글 작성자
     */
    public void assignAuthor(User author) {
        this.author = author;
    }
}
