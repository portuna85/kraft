// src/main/java/com/boardly/post/domain/Post.java
package com.boardly.post.domain;

import com.boardly.common.domain.BaseTimeEntity;
import com.boardly.user.domain.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Entity
@Table(name = "posts") // ★ 추가
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor @Builder
public class Post extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, length = 200)
    private String title;

    @Lob @Column(nullable = false)
    private String content;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User author;

    @Column(nullable = false)
    private long viewCount;

    public void increaseView() { this.viewCount++; }
    public void update(String title, String content) {
        this.title = title; this.content = content;
    }
}
