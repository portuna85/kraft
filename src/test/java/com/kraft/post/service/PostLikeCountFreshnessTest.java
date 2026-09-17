package com.kraft.post.service;

import com.kraft.post.domain.Post;
import com.kraft.post.domain.PostLikeRepository;
import com.kraft.post.domain.PostRepository;
import com.kraft.post.dto.PostLikeResponseDto;
import com.kraft.user.domain.Role;
import com.kraft.user.domain.User;
import com.kraft.user.domain.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * B09: {@code setLike}를 감싼 트랜잭션이 REQUIRES_NEW로 커밋되는 추천 INSERT보다 먼저
 * REPEATABLE READ 스냅샷을 잡아 두어도, 최종 응답의 {@code likeCount}가 방금 커밋된 추천을
 * 반영하는지 실제 MariaDB로 확인한다. H2는 기본 격리 수준이 달라 이 경쟁을 재현하지 못한다
 * (개선 보고서 "H2 테스트의 잠금 오류 유형이 MariaDB의 타임아웃·교착 결과와 같다고 가정하지
 * 않는다"와 같은 이유). Docker가 없으면 건너뛴다.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class PostLikeCountFreshnessTest {

    @Container
    @ServiceConnection
    static MariaDBContainer<?> mariadb = new MariaDBContainer<>("mariadb:11.7.2");

    @Autowired
    private PostService postService;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private PostLikeRepository postLikeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private Post post;
    private Authentication liker;

    @BeforeEach
    void setUp() {
        postLikeRepository.deleteAll();
        postRepository.deleteAll();
        userRepository.deleteAll();

        User author = userRepository.save(User.builder()
                .name("fresh-author").email("fresh-author@example.com").password("encoded").role(Role.USER).build());
        User likerUser = userRepository.save(User.builder()
                .name("fresh-liker").email("fresh-liker@example.com").password("encoded").role(Role.USER).build());
        post = postRepository.save(Post.builder().title("제목").content("내용").user(author).build());
        liker = new UsernamePasswordAuthenticationToken(likerUser.getEmail(), null,
                List.of(new SimpleGrantedAuthority(Role.USER.getKey())));
    }

    @Test
    @DisplayName("B09: 바깥 트랜잭션이 미리 스냅샷을 잡아 두어도 최종 추천 수는 방금 커밋된 추천을 반영한다")
    void setLike_reflectsJustCommittedLike_evenWhenOuterSnapshotIsEarlier() {
        Long postId = post.getId();

        PostLikeResponseDto result = transactionTemplate.execute(status -> {
            // 바깥 트랜잭션의 REPEATABLE READ 스냅샷을 이 조회 시점에 고정시킨다 — setLike 내부의
            // REQUIRES_NEW INSERT는 이보다 나중에 별도 커밋되므로, 고정 전 방식(같은 스냅샷에서
            // count 조회)이었다면 이 커밋을 보지 못했을 것이다.
            postRepository.findById(postId);

            return postService.setLike(postId, true, liker);
        });

        assertThat(result).isNotNull();
        assertThat(result.likeCount()).isEqualTo(1L);
    }
}
