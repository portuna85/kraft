package com.boardly.post.service;


import com.boardly.post.domain.Post;
import com.boardly.post.service.PostService;
import com.boardly.user.domain.Role;
import com.boardly.user.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;


import static org.assertj.core.api.Assertions.assertThat;


@SpringBootTest
@Transactional
class PostServiceTest {
    @Autowired PostService postService;


    @Test
    void create_and_get_update_delete() {
        User author = User.builder().username("u1").password("pw").nickname("n").email("e@e.com").role(Role.USER).build();
        Long id = postService.create(author, "t", "c");
        Post found = postService.get(id);
        assertThat(found.getTitle()).isEqualTo("t");


        postService.update(id, "t2", "c2");
        assertThat(postService.get(id).getTitle()).isEqualTo("t2");


        postService.delete(id);
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> postService.get(id))).isInstanceOf(IllegalArgumentException.class);
    }
}