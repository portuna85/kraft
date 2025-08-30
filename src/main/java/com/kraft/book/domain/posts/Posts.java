package com.kraft.book.domain.posts;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "posts", indexes = {
        @Index(name = "idx_posts_author", columnList = "author")
})
public class Posts extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Comment("제목")
    @Column(length = 500, nullable = false)
    private String title;

    // 이식성 높은 CLOB 매핑 (필요 시 @Column(columnDefinition="TEXT")로 교체)
    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Comment("본문")
    @Column(nullable = false)
    private String content;

    @Comment("작성자")
    @Column(length = 255)
    private String author;

    @Builder
    public Posts(String title, String content, String author) {
        this.title = title;
        this.content = content;
        this.author = author;
    }

    /**
     * 제목/본문 수정
     */
    public void update(String title, String content) {
        this.title = title;
        this.content = content;
    }
}
