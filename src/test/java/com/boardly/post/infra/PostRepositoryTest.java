package com.boardly.post.infra;


import com.boardly.post.domain.Post;
import com.boardly.post.infra.PostRepository;
import com.boardly.user.domain.Role;
import com.boardly.user.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;


import static org.assertj.core.api.Assertions.assertThat;


@DataJpaTest
class PostRepositoryTest {
    @Autowired PostRepository postRepository;
    @Autowired TestEntityManager em;


    @Test
    void save_and_find() {
        User author = User.builder()
                .username("u1").password("pw").nickname("n1").email("e@e.com").role(Role.USER)
                .build();
        em.persist(author);


        Post saved = postRepository.save(Post.builder()
                .title("title").content("content").author(author).viewCount(0)
                .build());


        em.flush(); em.clear();


        assertThat(saved.getId()).isNotNull();
        assertThat(postRepository.findById(saved.getId())).isPresent();
    }
}