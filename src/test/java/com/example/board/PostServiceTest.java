package com.example.board;

import com.example.board.member.Member;
import com.example.board.member.MemberRepository;
import com.example.board.post.Post;
import com.example.board.post.PostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestConfig.class)
class PostServiceTest {

    @Autowired MemberRepository memberRepository;
    @Autowired PostService postService;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("게시글 작성/조회/수정/삭제 흐름")
    void post_crud_flow() {
        Member author = memberRepository.save(new Member("a@a.com", "alice", passwordEncoder.encode("12345678")));
        Post p = postService.write("hello", "world", author);
        assertThat(p.getId()).isNotNull();

        Post found = postService.getById(p.getId());
        assertThat(found.getTitle()).isEqualTo("hello");

        postService.edit(p.getId(), "new", "content", author);
        Post edited = postService.getById(p.getId());
        assertThat(edited.getTitle()).isEqualTo("new");

        postService.delete(p.getId(), author);
        assertThatThrownBy(() -> postService.getById(p.getId())).isInstanceOf(IllegalArgumentException.class);
    }
}
