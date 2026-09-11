package com.kraft.domain.post;

import com.kraft.domain.BaseEntity;
import com.kraft.domain.user.User;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자가 게시글에 남긴 추천(좋아요). 사용자당 게시글별 1회만 허용한다
 * ({@code UK_POST_LIKE_POST_USER}로 DB 레벨에서도 강제).
 */
@Getter
@Entity
@NoArgsConstructor
@Table(name = "post_likes",
        uniqueConstraints = @UniqueConstraint(name = "UK_POST_LIKE_POST_USER", columnNames = {"post_id", "user_id"}))
public class PostLike extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id")
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Builder
    public PostLike(Post post, User user) {
        this.post = post;
        this.user = user;
    }
}
