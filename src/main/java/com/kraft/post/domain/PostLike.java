package com.kraft.post.domain;

import com.kraft.shared.domain.BaseEntity;
import com.kraft.user.domain.User;
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
        uniqueConstraints = @UniqueConstraint(name = PostLike.UK_POST_LIKE_POST_USER, columnNames = {"post_id", "user_id"}))
public class PostLike extends BaseEntity {

    /**
     * 중복 추천 검사에 쓴다({@link com.kraft.post.service.PostLikeWriter}). 같은 문자열을
     * 어노테이션과 코드에서 각자 따로 적으면, 제약 이름이 바뀌었을 때 한쪽만 고쳐 조용히
     * 어긋날 수 있다.
     */
    public static final String UK_POST_LIKE_POST_USER = "UK_POST_LIKE_POST_USER";

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
