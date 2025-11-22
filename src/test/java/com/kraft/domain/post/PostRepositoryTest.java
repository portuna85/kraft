package com.kraft.domain.post;

import com.kraft.domain.user.Role;
import com.kraft.domain.user.User;
import com.kraft.domain.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class PostRepositoryTest {

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private UserRepository userRepository;

    private User author;

    @BeforeEach
    void setUp() {
        author = User.of("author", "password123", "author@example.com");
        userRepository.save(author);
    }

    @Test
    @DisplayName("게시글을 저장하고 조회할 수 있다")
    void saveAndFind() {
        // given
        Post post = Post.builder()
                .title("Test Title")
                .content("Test Content")
                .author(author)
                .build();

        // when
        Post saved = postRepository.save(post);

        // then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getTitle()).isEqualTo("Test Title");
        assertThat(saved.getContent()).isEqualTo("Test Content");
        assertThat(saved.getAuthor()).isEqualTo(author);
    }

    @Test
    @DisplayName("게시글 목록을 ID 역순으로 조회할 수 있다")
    void findAllDesc() {
        // given
        Post post1 = Post.builder()
                .title("First")
                .content("Content 1")
                .author(author)
                .build();
        Post post2 = Post.builder()
                .title("Second")
                .content("Content 2")
                .author(author)
                .build();
        Post post3 = Post.builder()
                .title("Third")
                .content("Content 3")
                .author(author)
                .build();

        postRepository.save(post1);
        postRepository.save(post2);
        postRepository.save(post3);

        // when
        List<Post> posts = postRepository.findAllDesc();

        // then
        assertThat(posts).hasSize(3);
        assertThat(posts.get(0).getTitle()).isEqualTo("Third");
        assertThat(posts.get(1).getTitle()).isEqualTo("Second");
        assertThat(posts.get(2).getTitle()).isEqualTo("First");
    }

    @Test
    @DisplayName("게시글을 수정할 수 있다")
    void update() {
        // given
        Post post = Post.builder()
                .title("Original Title")
                .content("Original Content")
                .author(author)
                .build();
        Post saved = postRepository.save(post);

        // when
        saved.update("Updated Title", "Updated Content");
        postRepository.flush();

        // then
        Post found = postRepository.findById(saved.getId()).get();
        assertThat(found.getTitle()).isEqualTo("Updated Title");
        assertThat(found.getContent()).isEqualTo("Updated Content");
    }

    @Test
    @DisplayName("게시글을 삭제할 수 있다")
    void delete() {
        // given
        Post post = Post.builder()
                .title("To Delete")
                .content("Content")
                .author(author)
                .build();
        Post saved = postRepository.save(post);

        // when
        postRepository.delete(saved);

        // then
        assertThat(postRepository.findById(saved.getId())).isEmpty();
    }
}

