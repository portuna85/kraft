package com.kraft.domain.user;

import com.kraft.common.entity.BaseEntity;
import com.kraft.domain.post.Post;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 사용자 엔티티
 * - name: 로그인 ID (변경 불가)
 * - 양방향 관계: addPost()를 통해 Post와 관계 관리
 */
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Table(
    name = "users",
    indexes = {
        @Index(name = "idx_user_email", columnList = "email"),
        @Index(name = "idx_user_name", columnList = "name", unique = true)
    }
)
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String name; // 로그인 ID - 변경 불가

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, unique = true, length = 120)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @OneToMany(mappedBy = "author", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Post> posts = new ArrayList<>();

    @Builder
    private User(String name, String password, String email, Role role) {
        this.name = name;
        this.password = password;
        this.email = email;
        this.role = role == null ? Role.USER : role;
    }

    /**
     * 정적 팩토리 메서드 - 일반 사용자 생성
     */
    public static User of(String name, String password, String email) {
        return User.builder()
                .name(name)
                .password(password)
                .email(email)
                .role(Role.USER)
                .build();
    }

    /**
     * 이메일 변경
     */
    public void updateEmail(String email) {
        this.email = email;
    }

    /**
     * 비밀번호 변경 (암호화된 비밀번호)
     */
    public void updatePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    /**
     * 권한 할당
     */
    public void assignRole(Role role) {
        this.role = role;
    }

    /**
     * 비밀번호 일치 여부 확인
     */
    public boolean isSamePassword(String rawPassword, PasswordEncoder encoder) {
        return encoder.matches(rawPassword, this.password);
    }

    /**
     * 게시글 추가 - 양방향 관계 관리
     * 연관관계의 주인이 아니므로 여기서 관계를 관리
     */
    public void addPost(Post post) {
        if (!this.posts.contains(post)) {
            this.posts.add(post);
            post.assignAuthor(this);
        }
    }

    /**
     * 게시글 제거 - 양방향 관계 관리
     */
    public void removePost(Post post) {
        this.posts.remove(post);
    }

    /**
     * 게시글 목록 조회 - 읽기 전용
     */
    public List<Post> getPosts() {
        return Collections.unmodifiableList(posts);
    }
}
