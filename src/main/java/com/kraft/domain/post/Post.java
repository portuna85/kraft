package com.kraft.domain.post;

import com.kraft.domain.BaseEntity;
import com.kraft.domain.user.User;
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
@Table(name = "posts")
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

    public void increaseViewCount() {
        this.viewCount++;
    }
}
