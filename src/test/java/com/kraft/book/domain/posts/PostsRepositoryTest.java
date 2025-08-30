package com.kraft.book.domain.posts;

import com.kraft.book.config.JpaConfig;            // ★ 추가
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import; // ★ 추가
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(JpaConfig.class) // ★ Auditing 설정 주입
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@ActiveProfiles("test")
class PostsRepositoryTest {

    @Autowired
    PostsRepository postsRepository;

    @Test
    void 게시글저장_불러오기() {
        // given
        String title = "테스트 게시글";
        String content = "테스트 본문";

        postsRepository.save(Posts.builder()
                .title(title)
                .content(content)
                .author("portuna85@gmail.com")
                .build());

        // when
        List<Posts> postsList = postsRepository.findAll();

        // then
        Posts posts = postsList.get(0);
        assertThat(posts.getTitle()).isEqualTo(title);
        assertThat(posts.getContent()).isEqualTo(content);
    }

    @Test
    void BaseTimeEntity_등록() {
        // given
        LocalDateTime baseline = LocalDateTime.of(2019, 6, 4, 0, 0);
        postsRepository.save(Posts.builder()
                .title("타이틀 입니다")
                .content("콘텐츠 입니다")
                .author("작성자입니다.")
                .build());

        // when
        List<Posts> all = postsRepository.findAll();

        // then
        Posts posts = all.get(0);
        System.out.println(">>>> createdDate=" + posts.getCreatedDate() + ", modifiedDate=" + posts.getModifiedDate());
        assertThat(posts.getCreatedDate()).isAfter(baseline);
        assertThat(posts.getModifiedDate()).isAfter(baseline);
    }
}
