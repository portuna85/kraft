// src/main/java/com/kraft/book/domain/posts/Posts.java
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
public class Posts {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 500, nullable = false)
    @Comment("제목")
    private String title;

    // 이식성 높은 CLOB: 대부분의 DB에서 TEXT로 매핑
    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(nullable = false)
    @Comment("본문")
    private String content;

    @Column(length = 255)
    @Comment("작성자")
    private String author;

    @Builder
    public Posts(String title, String content, String author) {
        this.title = title;
        this.content = content;
        this.author = author;
    }
}
