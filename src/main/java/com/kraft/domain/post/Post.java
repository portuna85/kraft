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

    @Builder
    public Post(String title, String content, String picture, User user) {
        this.title = title;
        this.content = content;
        this.picture = picture;
        this.user = user;
    }

    public void update(String title, String content, String picture) {
        this.title = title;
        this.content = content;
        this.picture = picture;
    }
}
